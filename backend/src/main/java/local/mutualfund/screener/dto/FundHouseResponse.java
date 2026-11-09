package local.mutualfund.screener.dto;

import local.mutualfund.screener.entity.FundHouseEntity;

public record FundHouseResponse(
        Long id,
        String code,
        String name,
        String countryCode,
        String websiteUrl
) {
    public static FundHouseResponse from(FundHouseEntity entity) {
        return new FundHouseResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getCountryCode(),
                entity.getWebsiteUrl()
        );
    }
}
