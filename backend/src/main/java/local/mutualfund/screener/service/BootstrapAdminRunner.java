package local.mutualfund.screener.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public BootstrapAdminRunner(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbcTemplate.queryForList("SELECT user_overridable FROM app_permissions WHERE 1 = 0");
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_users", Integer.class
        );
        if (userCount != null && userCount > 0) {
            return;
        }
        boolean anyConfigured = !username.isBlank() || !email.isBlank() || !password.isBlank();
        if (!anyConfigured) {
            LOGGER.warn("No application users exist. Set BOOTSTRAP_ADMIN_USERNAME, "
                    + "BOOTSTRAP_ADMIN_EMAIL, and BOOTSTRAP_ADMIN_PASSWORD to create the first admin.");
            return;
        }
        if (username.isBlank() || email.isBlank() || password.length() < 12
                || password.length() > 72 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Initial admin requires BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_EMAIL, "
                            + "and a 12-72 character BOOTSTRAP_ADMIN_PASSWORD"
            );
        }

        Long userId = insertAdmin();
        int roleAssigned = jdbcTemplate.update(
                """
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, role_id FROM app_roles WHERE role_name = 'ADMIN'
                """,
                userId
        );
        if (roleAssigned != 1) {
            throw new IllegalStateException("The ADMIN role is missing from APP_ROLES");
        }
        LOGGER.info("Created the initial administrator account from bootstrap configuration.");
    }

    private Long insertAdmin() {
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO app_users
                        (username, email, password_hash, is_enabled, is_account_locked)
                    VALUES (?, ?, ?, 1, 0)
                    """,
                    new String[]{"USER_ID"}
            );
            statement.setString(1, username.trim());
            statement.setString(2, email.trim().toLowerCase(Locale.ROOT));
            statement.setString(3, passwordEncoder.encode(password));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Oracle did not return the bootstrap administrator ID");
        }
        return key.longValue();
    }
}
