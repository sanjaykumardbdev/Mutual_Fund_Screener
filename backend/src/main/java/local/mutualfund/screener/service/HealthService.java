package local.mutualfund.screener.service;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final DataSource dataSource;

    public HealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void verifyDatabaseConnection() {
        try (var connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                throw new DatabaseUnavailableException(
                        new SQLException("Database connection validation failed")
                );
            }
        } catch (SQLException exception) {
            throw new DatabaseUnavailableException(exception);
        }
    }
}
