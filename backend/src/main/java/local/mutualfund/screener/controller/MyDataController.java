package local.mutualfund.screener.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import local.mutualfund.screener.dto.AuthDtos.InvestmentRequest;
import local.mutualfund.screener.dto.AuthDtos.InvestmentResponse;
import local.mutualfund.screener.dto.AuthDtos.WatchlistItem;
import local.mutualfund.screener.security.AppUserPrincipal;
import local.mutualfund.screener.service.UserDataService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/me")
public class MyDataController {

    private final UserDataService userDataService;

    public MyDataController(UserDataService userDataService) {
        this.userDataService = userDataService;
    }

    @GetMapping("/watchlist")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'WATCHLIST_MANAGE')")
    public List<WatchlistItem> getWatchlist(@AuthenticationPrincipal AppUserPrincipal principal) {
        return userDataService.getWatchlist(principal.getUserId());
    }

    @PostMapping("/watchlist/{schemeId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'WATCHLIST_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addToWatchlist(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable @Positive long schemeId
    ) {
        userDataService.addToWatchlist(principal.getUserId(), schemeId);
    }

    @DeleteMapping("/watchlist/{schemeId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'WATCHLIST_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromWatchlist(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable @Positive long schemeId
    ) {
        userDataService.removeFromWatchlist(principal.getUserId(), schemeId);
    }

    @GetMapping("/investments")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'INVESTMENT_MANAGE')")
    public List<InvestmentResponse> getInvestments(@AuthenticationPrincipal AppUserPrincipal principal) {
        return userDataService.getInvestments(principal.getUserId());
    }

    @PostMapping("/investments")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'INVESTMENT_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public void addInvestment(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody InvestmentRequest investment
    ) {
        userDataService.addInvestment(principal.getUserId(), investment);
    }
}
