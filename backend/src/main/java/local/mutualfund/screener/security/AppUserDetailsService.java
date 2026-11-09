package local.mutualfund.screener.security;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public AppUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<AppUserPrincipal> users = jdbcTemplate.query(
                """
                SELECT u.user_id, u.username, u.password_hash, u.is_enabled, u.is_account_locked,
                       LISTAGG(r.role_name, ',') WITHIN GROUP (ORDER BY r.role_name) AS roles
                FROM app_users u
                LEFT JOIN user_roles ur ON ur.user_id = u.user_id
                LEFT JOIN app_roles r ON r.role_id = ur.role_id
                WHERE UPPER(u.username) = UPPER(?)
                GROUP BY u.user_id, u.username, u.password_hash, u.is_enabled, u.is_account_locked
                """,
                (resultSet, rowNumber) -> new AppUserPrincipal(
                        resultSet.getLong("user_id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        resultSet.getInt("is_enabled") == 1,
                        resultSet.getInt("is_account_locked") == 0,
                        resultSet.getString("roles") == null
                                ? List.of()
                                : List.of(resultSet.getString("roles").split(","))
                ),
                username
        );
        if (users.isEmpty()) {
            throw new UsernameNotFoundException("Invalid username or password");
        }
        return users.get(0);
    }
}
