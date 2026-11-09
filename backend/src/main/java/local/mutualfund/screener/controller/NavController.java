package local.mutualfund.screener.controller;

import jakarta.validation.Valid;
import local.mutualfund.screener.dto.NavDtos.DashboardResponse;
import local.mutualfund.screener.dto.NavDtos.RefreshRequest;
import local.mutualfund.screener.dto.NavDtos.RefreshResponse;
import local.mutualfund.screener.security.AppUserPrincipal;
import local.mutualfund.screener.service.NavRefreshService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nav")
public class NavController {

    private final NavRefreshService navRefreshService;

    public NavController(NavRefreshService navRefreshService) {
        this.navRefreshService = navRefreshService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'NAV_VIEW')")
    public DashboardResponse dashboard() {
        return navRefreshService.getDashboard();
    }

    @PostMapping("/refresh")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'NAV_REFRESH')")
    public RefreshResponse refresh(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody RefreshRequest request
    ) {
        return navRefreshService.refresh(request.scope(), request.schemeId(), principal.getUserId());
    }
}
