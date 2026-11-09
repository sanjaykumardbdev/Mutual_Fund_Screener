package local.mutualfund.screener.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import local.mutualfund.screener.dto.FundSchemeResponse;
import local.mutualfund.screener.dto.NavHistoryResponse;
import local.mutualfund.screener.dto.NavDtos.SchemeDetailsResponse;
import local.mutualfund.screener.service.FundCatalogService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Validated
@RestController
@RequestMapping("/api/schemes")
public class FundSchemeController {

    private final FundCatalogService fundCatalogService;

    public FundSchemeController(FundCatalogService fundCatalogService) {
        this.fundCatalogService = fundCatalogService;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'NAV_VIEW')")
    public Page<FundSchemeResponse> getSchemes(
            @RequestParam(required = false) String houseCode,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String planType,
            @RequestParam(required = false) String optionType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return fundCatalogService.getSchemes(
                houseCode, category, planType, optionType, search, page, size
        );
    }

    @GetMapping("/{schemeId}/nav")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'NAV_VIEW')")
    public List<NavHistoryResponse> getNavHistory(
            @PathVariable @Min(1) Long schemeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }
        return fundCatalogService.getNavHistory(schemeId, fromDate, toDate);
    }

    @GetMapping("/{schemeId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'NAV_VIEW')")
    public SchemeDetailsResponse getSchemeDetails(@PathVariable @Min(1) Long schemeId) {
        return fundCatalogService.getSchemeDetails(schemeId);
    }
}
