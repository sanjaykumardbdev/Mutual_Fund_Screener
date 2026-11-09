package local.mutualfund.screener.security;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("permissionService")
public class PermissionService {

    private final JdbcTemplate jdbcTemplate;

    public PermissionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasPermission(Authentication authentication, String permissionCode) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return false;
        }
        if (!isAccountActive(authentication)) {
            return false;
        }

        List<UserOverride> overrides = jdbcTemplate.query(
                """
                SELECT up.permission_value, p.user_overridable
                FROM user_permissions up
                JOIN app_permissions p ON p.permission_id = up.permission_id
                WHERE up.user_id = ? AND p.permission_code = ?
                """,
                (resultSet, rowNumber) -> new UserOverride(
                        resultSet.getString("permission_value"),
                        resultSet.getInt("user_overridable") == 1
                ),
                principal.getUserId(),
                permissionCode
        );
        if (!overrides.isEmpty()) {
            UserOverride override = overrides.get(0);
            return "ALLOW".equals(override.value()) && override.userOverridable();
        }

        Integer roleGrantCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_roles ur
                JOIN role_permissions rp ON rp.role_id = ur.role_id
                JOIN app_permissions p ON p.permission_id = rp.permission_id
                WHERE ur.user_id = ? AND p.permission_code = ?
                """,
                Integer.class,
                principal.getUserId(),
                permissionCode
        );
        return roleGrantCount != null && roleGrantCount > 0;
    }

    public boolean isAccountActive(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return false;
        }
        List<AccountStatus> accountStatuses = jdbcTemplate.query(
                """
                SELECT is_enabled, is_account_locked, password_hash
                FROM app_users WHERE user_id = ?
                """,
                (resultSet, rowNumber) -> new AccountStatus(
                        resultSet.getInt("is_enabled") == 1,
                        resultSet.getInt("is_account_locked") == 0,
                        resultSet.getString("password_hash")
                ),
                principal.getUserId()
        );
        return !accountStatuses.isEmpty()
                && accountStatuses.get(0).enabled()
                && accountStatuses.get(0).unlocked()
                && accountStatuses.get(0).passwordHash().equals(principal.getPassword());
    }

    private record UserOverride(String value, boolean userOverridable) {
    }

    private record AccountStatus(boolean enabled, boolean unlocked, String passwordHash) {
    }
}
