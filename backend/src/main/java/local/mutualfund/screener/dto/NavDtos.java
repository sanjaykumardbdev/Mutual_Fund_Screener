package local.mutualfund.screener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class NavDtos {

    private NavDtos() {
    }

    public record DashboardResponse(
            long totalSchemes,
            long schemesWithNav,
            LocalDate latestNavDate,
            LocalDateTime lastRetrievedAt,
            String lastRefreshResult,
            LocalDateTime lastRefreshAt,
            long lastRefreshRecords
    ) {
    }

    public record SchemeDetailsResponse(
            Long id,
            String code,
            String name,
            String houseCode,
            String houseName,
            String category,
            String planType,
            String optionType,
            String benchmark,
            String isin,
            LocalDate latestNavDate,
            BigDecimal latestNav,
            String latestNavProvider
    ) {
    }

    public record RefreshRequest(
            @NotBlank @Pattern(regexp = "ALL|SCHEME|WATCHLIST") String scope,
            @Positive Long schemeId
    ) {
    }

    public record RefreshResponse(
            long refreshId,
            String scope,
            String result,
            int recordsProcessed,
            String providerName,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String message
    ) {
    }
}
