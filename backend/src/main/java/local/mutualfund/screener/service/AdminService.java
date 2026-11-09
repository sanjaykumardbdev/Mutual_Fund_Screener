package local.mutualfund.screener.service;

import java.util.List;
import local.mutualfund.screener.dto.AdminDtos.PermissionSummary;
import local.mutualfund.screener.dto.AdminDtos.UserPermissionSummary;
import local.mutualfund.screener.dto.AdminDtos.UserSummary;
import local.mutualfund.screener.dto.AuthDtos.LoginAuditResponse;
import local.mutualfund.screener.dto.AuthDtos.RegisterRequest;
import local.mutualfund.screener.dto.AuthDtos.UserResponse;
import local.mutualfund.screener.security.AppUserPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    public AdminService(
            JdbcTemplate jdbcTemplate,
            AuthService authService,
            PasswordEncoder passwordEncoder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserSummary> getUsers() {
        return jdbcTemplate.query(
                """
                SELECT u.user_id, u.username, u.email, u.is_enabled, u.is_account_locked,
                       LISTAGG(r.role_name, ',') WITHIN GROUP (ORDER BY r.role_name) AS roles
                FROM app_users u
                LEFT JOIN user_roles ur ON ur.user_id = u.user_id
                LEFT JOIN app_roles r ON r.role_id = ur.role_id
                GROUP BY u.user_id, u.username, u.email, u.is_enabled, u.is_account_locked
                ORDER BY u.username
                """,
                (resultSet, rowNumber) -> new UserSummary(
                        resultSet.getLong("user_id"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getInt("is_enabled") == 1,
                        resultSet.getInt("is_account_locked") == 1,
                        resultSet.getString("roles") == null
                                ? List.of()
                                : List.of(resultSet.getString("roles").split(","))
                )
        );
    }

    public List<LoginAuditResponse> getLoginAudit() {
        return jdbcTemplate.query(
                """
                SELECT username, event_at, event_type, ip_address, user_agent, failure_reason
                FROM user_login_audit
                ORDER BY event_at DESC
                FETCH FIRST 200 ROWS ONLY
                """,
                (resultSet, rowNumber) -> new LoginAuditResponse(
                        resultSet.getString("username"),
                        resultSet.getTimestamp("event_at").toInstant(),
                        resultSet.getString("event_type"),
                        resultSet.getString("ip_address"),
                        resultSet.getString("user_agent"),
                        resultSet.getString("failure_reason")
                )
        );
    }

    @Transactional
    public UserResponse createUser(RegisterRequest request, AppUserPrincipal actor) {
        UserResponse user;
        try {
            user = authService.register(request);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already in use");
        }
        audit(actor.getUserId(), "USER_CREATED", "userId=" + user.id());
        return user;
    }

    @Transactional
    public void setEnabled(long targetUserId, boolean enabled, AppUserPrincipal actor) {
        if (targetUserId == actor.getUserId() && !enabled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot disable your own account");
        }
        UserSummary target = getUser(targetUserId);
        if (target.enabled() && !enabled && target.roles().contains("ADMIN")) {
            ensureOtherEnabledAdminExists(targetUserId);
        }
        int updated = jdbcTemplate.update(
                "UPDATE app_users SET is_enabled = ?, updated_at = SYSTIMESTAMP WHERE user_id = ?",
                enabled ? 1 : 0,
                targetUserId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        audit(actor.getUserId(), enabled ? "USER_ENABLED" : "USER_DISABLED",
                "targetUserId=" + targetUserId);
    }

    @Transactional
    public void replaceRoles(long targetUserId, List<String> roles, AppUserPrincipal actor) {
        UserSummary target = getUser(targetUserId);
        List<String> normalizedRoles = roles.stream().map(String::toUpperCase).distinct().toList();
        if (target.enabled() && target.roles().contains("ADMIN")
                && !normalizedRoles.contains("ADMIN")) {
            ensureOtherEnabledAdminExists(targetUserId);
        }
        for (String role : normalizedRoles) {
            if (jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM app_roles WHERE role_name = ?",
                    Integer.class,
                    role
            ) == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role");
            }
        }
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", targetUserId);
        for (String role : normalizedRoles) {
            jdbcTemplate.update(
                    """
                    INSERT INTO user_roles (user_id, role_id)
                    SELECT ?, role_id FROM app_roles WHERE role_name = ?
                    """,
                    targetUserId,
                    role
            );
        }
        audit(actor.getUserId(), "USER_ROLES_CHANGED",
                "targetUserId=" + targetUserId + ", roles=" + String.join(",", normalizedRoles));
    }

    public List<PermissionSummary> getPermissions() {
        return jdbcTemplate.query(
                """
                SELECT permission_code, permission_name, user_overridable
                FROM app_permissions ORDER BY permission_code
                """,
                (resultSet, rowNumber) -> new PermissionSummary(
                        resultSet.getString("permission_code"),
                        resultSet.getString("permission_name"),
                        resultSet.getInt("user_overridable") == 1
                )
        );
    }

    public List<RoleSummary> getRoles() {
        return jdbcTemplate.query(
                """
                SELECT r.role_name, p.permission_code
                FROM app_roles r
                LEFT JOIN role_permissions rp ON rp.role_id = r.role_id
                LEFT JOIN app_permissions p ON p.permission_id = rp.permission_id
                ORDER BY r.role_name, p.permission_code
                """,
                resultSet -> {
                    var roles = new java.util.LinkedHashMap<String, java.util.List<String>>();
                    while (resultSet.next()) {
                        String roleName = resultSet.getString("role_name");
                        roles.computeIfAbsent(roleName, ignored -> new java.util.ArrayList<>());
                        String code = resultSet.getString("permission_code");
                        if (code != null) {
                            roles.get(roleName).add(code);
                        }
                    }
                    return roles.entrySet().stream()
                            .map(entry -> new RoleSummary(entry.getKey(), List.copyOf(entry.getValue())))
                            .toList();
                }
        );
    }

    @Transactional
    public void replaceRolePermissions(String roleName, List<String> permissionCodes, AppUserPrincipal actor) {
        String normalizedRole = roleName.toUpperCase();
        Long roleId = jdbcTemplate.query(
                "SELECT role_id FROM app_roles WHERE role_name = ?",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                normalizedRole
        );
        if (roleId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }
        List<String> normalizedCodes = permissionCodes.stream().map(String::toUpperCase).distinct().toList();
        if ("ADMIN".equals(normalizedRole)
                && (!normalizedCodes.contains("USER_MANAGE")
                || !normalizedCodes.contains("PERMISSION_MANAGE"))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "ADMIN must retain USER_MANAGE and PERMISSION_MANAGE"
            );
        }
        for (String code : normalizedCodes) {
            if (permissionIdOrNull(code) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission code");
            }
        }
        jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
        for (String code : normalizedCodes) {
            jdbcTemplate.update(
                    """
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT ?, permission_id FROM app_permissions WHERE permission_code = ?
                    """,
                    roleId,
                    code
            );
        }
        audit(actor.getUserId(), "ROLE_PERMISSIONS_CHANGED",
                normalizedRole + "=" + String.join(",", normalizedCodes));
    }

    @Transactional
    public void setPermissionOverridable(
            String permissionCode, boolean overridable, AppUserPrincipal actor
    ) {
        int updated = jdbcTemplate.update(
                """
                UPDATE app_permissions SET user_overridable = ?
                WHERE permission_code = ?
                """,
                overridable ? 1 : 0,
                permissionCode.toUpperCase()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found");
        }
        if (!overridable) {
            jdbcTemplate.update(
                    """
                    DELETE FROM user_permissions
                    WHERE permission_id = (
                        SELECT permission_id FROM app_permissions WHERE permission_code = ?
                    ) AND permission_value = 'ALLOW'
                    """,
                    permissionCode.toUpperCase()
            );
        }
        audit(actor.getUserId(), "PERMISSION_OVERRIDE_POLICY_CHANGED",
                permissionCode.toUpperCase() + "=" + overridable);
    }

    @Transactional
    public void setUserPermissionOverride(
            long targetUserId,
            String permissionCode,
            String permissionValue,
            AppUserPrincipal actor
    ) {
        if (targetUserId == actor.getUserId()
                && "DENY".equals(permissionValue)
                && List.of("USER_MANAGE", "PERMISSION_MANAGE").contains(permissionCode.toUpperCase())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "You cannot deny your own administrator management permissions"
            );
        }
        if (!userExists(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        String normalizedCode = permissionCode.toUpperCase();
        List<Integer> policies = jdbcTemplate.query(
                "SELECT user_overridable FROM app_permissions WHERE permission_code = ?",
                (resultSet, rowNumber) -> resultSet.getInt(1),
                normalizedCode
        );
        if (policies.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found");
        }
        if ("ALLOW".equals(permissionValue) && policies.get(0) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This permission cannot be explicitly allowed for an individual user"
            );
        }
        jdbcTemplate.update(
                """
                MERGE INTO user_permissions target
                USING (SELECT ? AS user_id, ? AS permission_id FROM dual) source
                ON (target.user_id = source.user_id AND target.permission_id = source.permission_id)
                WHEN MATCHED THEN UPDATE SET
                    target.permission_value = ?, target.updated_at = SYSTIMESTAMP
                WHEN NOT MATCHED THEN INSERT (user_id, permission_id, permission_value)
                    VALUES (source.user_id, source.permission_id, ?)
                """,
                targetUserId,
                permissionId(normalizedCode),
                permissionValue,
                permissionValue
        );
        audit(actor.getUserId(), "USER_PERMISSION_OVERRIDE_CHANGED",
                "targetUserId=" + targetUserId + ", permission=" + normalizedCode
                        + ", value=" + permissionValue);
    }

    public List<UserPermissionSummary> getUserPermissionOverrides(long targetUserId) {
        if (!userExists(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return jdbcTemplate.query(
                """
                SELECT p.permission_code, up.permission_value
                FROM user_permissions up
                JOIN app_permissions p ON p.permission_id = up.permission_id
                WHERE up.user_id = ? ORDER BY p.permission_code
                """,
                (resultSet, rowNumber) -> new UserPermissionSummary(
                        resultSet.getString("permission_code"),
                        resultSet.getString("permission_value")
                ),
                targetUserId
        );
    }

    @Transactional
    public void clearUserPermissionOverride(
            long targetUserId, String permissionCode, AppUserPrincipal actor
    ) {
        int deleted = jdbcTemplate.update(
                """
                DELETE FROM user_permissions
                WHERE user_id = ? AND permission_id = (
                    SELECT permission_id FROM app_permissions WHERE permission_code = ?
                )
                """,
                targetUserId,
                permissionCode.toUpperCase()
        );
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission override not found");
        }
        audit(actor.getUserId(), "USER_PERMISSION_OVERRIDE_CLEARED",
                "targetUserId=" + targetUserId + ", permission=" + permissionCode.toUpperCase());
    }

    private UserSummary getUser(long userId) {
        List<UserSummary> users = getUsers().stream().filter(user -> user.id() == userId).toList();
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return users.get(0);
    }

    private boolean userExists(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE user_id = ?", Integer.class, userId
        ) > 0;
    }

    private long permissionId(String code) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT permission_id FROM app_permissions WHERE permission_code = ?",
                Long.class,
                code
        );
        return id;
    }

    private Long permissionIdOrNull(String code) {
        return jdbcTemplate.query(
                "SELECT permission_id FROM app_permissions WHERE permission_code = ?",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                code
        );
    }

    private void ensureOtherEnabledAdminExists(long targetUserId) {
        Integer admins = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM app_users u
                JOIN user_roles ur ON ur.user_id = u.user_id
                JOIN app_roles r ON r.role_id = ur.role_id
                WHERE u.user_id <> ? AND u.is_enabled = 1 AND r.role_name = 'ADMIN'
                """,
                Integer.class,
                targetUserId
        );
        if (admins == null || admins == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "At least one enabled administrator must remain"
            );
        }
    }

    private void audit(long actorUserId, String type, String details) {
        jdbcTemplate.update(
                """
                INSERT INTO user_activity_audit (user_id, activity_type, activity_details)
                VALUES (?, ?, ?)
                """,
                actorUserId,
                type,
                details
        );
    }

    public record RoleSummary(String roleName, List<String> permissions) {
    }
}
