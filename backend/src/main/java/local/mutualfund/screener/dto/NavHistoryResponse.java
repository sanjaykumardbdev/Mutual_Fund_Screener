package local.mutualfund.screener.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import local.mutualfund.screener.entity.NavHistoryEntity;

public record NavHistoryResponse(
        Long id,
        Long schemeId,
        LocalDate navDate,
        BigDecimal navValue,
        String providerName,
        LocalDateTime publishedAt,
        LocalDateTime retrievedAt
) {
    public static NavHistoryResponse from(NavHistoryEntity entity) {
        return new NavHistoryResponse(
                entity.getId(),
                entity.getScheme().getId(),
                entity.getNavDate(),
                entity.getNavValue(),
                entity.getProviderName(),
                entity.getPublishedAt(),
                entity.getRetrievedAt()
        );
    }
}
