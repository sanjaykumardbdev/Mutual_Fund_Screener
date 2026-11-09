package local.mutualfund.screener.dto;

import local.mutualfund.screener.entity.FundSchemeEntity;

public record FundSchemeResponse(
        Long id,
        String code,
        String name,
        String houseCode,
        String houseName,
        String category,
        String planType,
        String optionType,
        String benchmark,
        String isin
) {
    public static FundSchemeResponse from(FundSchemeEntity entity) {
        return new FundSchemeResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getHouse().getCode(),
                entity.getHouse().getName(),
                entity.getCategory(),
                entity.getPlanType(),
                entity.getOptionType(),
                entity.getBenchmark(),
                entity.getIsin()
        );
    }
}
