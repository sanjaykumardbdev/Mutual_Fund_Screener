package local.mutualfund.screener.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler exceptionHandler = new ApiExceptionHandler();

    @Test
    void showsActionableAmfiRefreshErrors() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        var response = exceptionHandler.handleResponseStatus(
                new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No active AMFI scheme mappings are available for this refresh."
                ),
                request
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(
                "No active AMFI scheme mappings are available for this refresh.",
                response.getBody().message()
        );
    }

    @Test
    void keepsNotFoundErrorsGeneric() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        var response = exceptionHandler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"),
                request
        );

        assertEquals("Resource not found", response.getBody().message());
    }
}
