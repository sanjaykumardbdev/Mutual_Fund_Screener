package local.mutualfund.screener.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import local.mutualfund.screener.service.DatabaseUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.core.AuthenticationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DatabaseUnavailableException.class)
    public ResponseEntity<ApiError> handleDatabaseUnavailable(
            DatabaseUnavailableException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailure(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request parameters", request);
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            org.springframework.web.server.ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = status == HttpStatus.NOT_FOUND
                ? "Resource not found"
                : status == HttpStatus.UNPROCESSABLE_ENTITY || status == HttpStatus.BAD_GATEWAY
                        ? exception.getReason()
                        : status.getReasonPhrase();
        return error(status, message, request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                new ApiError(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        request.getRequestURI()
                )
        );
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path
    ) {
    }
}
