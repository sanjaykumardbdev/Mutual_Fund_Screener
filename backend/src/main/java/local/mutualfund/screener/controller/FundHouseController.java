package local.mutualfund.screener.controller;

import java.util.List;
import local.mutualfund.screener.dto.FundHouseResponse;
import local.mutualfund.screener.service.FundCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/fund-houses")
public class FundHouseController {

    private final FundCatalogService fundCatalogService;

    public FundHouseController(FundCatalogService fundCatalogService) {
        this.fundCatalogService = fundCatalogService;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'NAV_VIEW')")
    public List<FundHouseResponse> getFundHouses(
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        return fundCatalogService.getFundHouses(activeOnly);
    }
}
