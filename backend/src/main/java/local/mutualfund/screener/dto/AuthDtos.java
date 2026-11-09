package local.mutualfund.screener.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 60) String username,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 60)
            @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 12, max = 72) String newPassword
    ) {
    }

    public record UserResponse(
            Long id,
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            List<String> roles
    ) {
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }

    public record LoginAuditResponse(
            String username,
            Instant loginAttemptAt,
            String loginResult,
            String ipAddress,
            String userAgent,
            String failureReason
    ) {
    }

    public record WatchlistRequest(@NotNull @jakarta.validation.constraints.Positive Long schemeId) {
    }

    public record WatchlistItem(
            Long schemeId,
            String schemeCode,
            String schemeName,
            String houseName,
            String category,
            String planType,
            String optionType,
            LocalDate latestNavDate,
            java.math.BigDecimal latestNav,
            Instant addedAt
    ) {
    }

    public record InvestmentRequest(
            @NotNull @jakarta.validation.constraints.Positive Long schemeId,
            @NotBlank @Pattern(regexp = "LUMPSUM|SIP|REDEMPTION|IDCW") String transactionType,
            @NotNull LocalDate transactionDate,
            @NotNull @jakarta.validation.constraints.DecimalMin(value = "0.01")
                    java.math.BigDecimal amount,
            @jakarta.validation.constraints.DecimalMin(value = "0.000001") java.math.BigDecimal units,
            @jakarta.validation.constraints.DecimalMin(value = "0.000001") java.math.BigDecimal nav,
            @jakarta.validation.constraints.Positive Long sipId,
            @Size(max = 500) String remarks
    ) {
    }

    public record InvestmentResponse(
            Long id,
            Long schemeId,
            String schemeName,
            String transactionType,
            LocalDate transactionDate,
            java.math.BigDecimal amount,
            java.math.BigDecimal units,
            java.math.BigDecimal nav,
            String remarks
    ) {
    }
}
