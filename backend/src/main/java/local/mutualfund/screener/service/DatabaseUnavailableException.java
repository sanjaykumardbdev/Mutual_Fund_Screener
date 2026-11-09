package local.mutualfund.screener.service;

public class DatabaseUnavailableException extends RuntimeException {

    public DatabaseUnavailableException(Throwable cause) {
        super("Database connection is unavailable", cause);
    }
}
