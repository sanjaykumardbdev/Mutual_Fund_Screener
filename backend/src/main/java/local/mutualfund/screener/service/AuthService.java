package local.mutualfund.screener.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import local.mutualfund.screener.dto.AuthDtos.ChangePasswordRequest;
import local.mutualfund.screener.dto.AuthDtos.LoginRequest;
import local.mutualfund.screener.dto.AuthDtos.RegisterRequest;
import local.mutualfund.screener.dto.AuthDtos.UserResponse;
import local.mutualfund.screener.security.AppUserPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            CookieCsrfTokenRepository csrfTokenRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    public UserResponse login(
            LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            loginRequest.username().trim(),
                            loginRequest.password()
                    )
            );
        } catch (org.springframework.security.core.AuthenticationException exception) {
            recordLoginAttempt(loginRequest.username().trim(), request, "LOGIN_FAILURE");
            throw new BadCredentialsException("Invalid username or password");
        }

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        jdbcTemplate.update(
                "UPDATE app_users SET last_login_at = SYSTIMESTAMP, updated_at = SYSTIMESTAMP WHERE user_id = ?",
                principal.getUserId()
        );
        recordLoginAttempt(loginRequest.username().trim(), request, "LOGIN_SUCCESS");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        csrfTokenRepository.saveToken(null, request, response);
        return getUser(principal.getUserId());
    }

    @Transactional
    public UserResponse register(RegisterRequest registerRequest) {
        validateBcryptPassword(registerRequest.password());
        Long userId;
        try {
            userId = insertUser(registerRequest.username().trim(),
                    registerRequest.email().trim().toLowerCase(Locale.ROOT),
                    registerRequest.firstName(),
                    registerRequest.lastName(),
                    passwordEncoder.encode(registerRequest.password()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Username or email is already registered"
            );
        }
        int roleAssigned = jdbcTemplate.update(
                """
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, role_id FROM app_roles WHERE role_name = 'USER'
                """,
                userId
        );
        if (roleAssigned != 1) {
            throw new IllegalStateException("The USER role is missing from APP_ROLES");
        }
        return getUser(userId);
    }

    public UserResponse getUser(long userId) {
        var users = jdbcTemplate.query(
                """
                SELECT user_id, username, email, first_name, last_name, is_enabled
                FROM app_users WHERE user_id = ?
                """,
                (resultSet, rowNumber) -> new UserResponse(
                        resultSet.getLong("user_id"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("first_name"),
                        resultSet.getString("last_name"),
                        resultSet.getInt("is_enabled") == 1,
                        java.util.List.of()
                ),
                userId
        );
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        UserResponse user = users.get(0);
        var roles = jdbcTemplate.queryForList(
                """
                SELECT r.role_name
                FROM app_roles r
                JOIN user_roles ur ON ur.role_id = r.role_id
                WHERE ur.user_id = ?
                ORDER BY r.role_name
                """,
                String.class,
                userId
        );
        return new UserResponse(
                user.id(), user.username(), user.email(), user.firstName(),
                user.lastName(), user.enabled(), roles
        );
    }

    public void logout(AppUserPrincipal principal, HttpServletRequest request) {
        jdbcTemplate.update(
                """
                INSERT INTO user_login_audit
                    (user_id, username, event_type, ip_address, user_agent, session_id)
                VALUES (?, ?, 'LOGOUT', ?, ?, ?)
                """,
                principal.getUserId(),
                principal.getUsername(),
                request.getRemoteAddr(),
                truncate(request.getHeader("User-Agent"), 1000),
                request.getSession(false) == null ? null : request.getSession(false).getId()
        );
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        validateBcryptPassword(request.newPassword());
        String currentHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM app_users WHERE user_id = ? AND is_enabled = 1",
                String.class,
                userId
        );
        if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), currentHash)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "New password must differ from the current password"
            );
        }
        jdbcTemplate.update(
                "UPDATE app_users SET password_hash = ?, updated_at = SYSTIMESTAMP WHERE user_id = ?",
                passwordEncoder.encode(request.newPassword()),
                userId
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_activity_audit (user_id, activity_type, activity_details)
                VALUES (?, 'PASSWORD_CHANGED', 'User changed own password')
                """,
                userId
        );
    }

    private Long insertUser(
            String username, String email, String firstName, String lastName, String passwordHash
    ) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO app_users
                        (username, email, password_hash, first_name, last_name, is_enabled, is_account_locked)
                    VALUES (?, ?, ?, ?, ?, 1, 0)
                    """,
                    new String[]{"USER_ID"}
            );
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, firstName);
            statement.setString(5, lastName);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Oracle did not return the new user ID");
        }
        return key.longValue();
    }

    private void recordLoginAttempt(String username, HttpServletRequest request, String eventType) {
        Long userId = jdbcTemplate.query(
                "SELECT user_id FROM app_users WHERE UPPER(username) = UPPER(?)",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                username
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_login_audit
                    (user_id, username, event_type, ip_address, user_agent, session_id, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                username,
                eventType,
                request.getRemoteAddr(),
                truncate(request.getHeader("User-Agent"), 1000),
                request.getSession(false) == null ? null : request.getSession(false).getId(),
                "LOGIN_FAILURE".equals(eventType) ? "Authentication failed" : null
        );
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void validateBcryptPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Password must be no more than 72 UTF-8 bytes"
            );
        }
    }
}
