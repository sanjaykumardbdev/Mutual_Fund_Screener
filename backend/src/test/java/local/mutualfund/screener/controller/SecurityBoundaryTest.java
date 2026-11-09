package local.mutualfund.screener.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import local.mutualfund.screener.config.SecurityConfig;
import local.mutualfund.screener.dto.AuthDtos.WatchlistItem;
import local.mutualfund.screener.dto.AuthDtos.PortfolioResponse;
import local.mutualfund.screener.service.HealthService;
import local.mutualfund.screener.security.AppUserDetailsService;
import local.mutualfund.screener.security.AppUserPrincipal;
import local.mutualfund.screener.security.PermissionService;
import local.mutualfund.screener.service.FundCatalogService;
import local.mutualfund.screener.service.UserDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({FundHouseController.class, HealthController.class, MyDataController.class})
@Import(SecurityConfig.class)
class SecurityBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FundCatalogService fundCatalogService;

    @MockitoBean
    private UserDataService userDataService;

    @MockitoBean
    private HealthService healthService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @MockitoBean(name = "permissionService")
    private PermissionService permissionService;

    @Test
    void healthCheckRemainsPublic() throws Exception {

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void fundCatalogRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/fund-houses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCatalogRequiresNavViewPermission() throws Exception {
        var principal = new AppUserPrincipal(42L, "alice", "hash", true, true, List.of("USER"));
        when(permissionService.hasPermission(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("NAV_VIEW")
        )).thenReturn(true);
        when(fundCatalogService.getFundHouses(true)).thenReturn(List.of());

        mockMvc.perform(get("/api/fund-houses").with(user(principal)))
                .andExpect(status().isOk());

        verify(fundCatalogService).getFundHouses(true);
    }

    @Test
    void privateWatchlistRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/watchlist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedWatchlistUsesPrincipalIdNotRequestData() throws Exception {
        var principal = new AppUserPrincipal(42L, "alice", "hash", true, true, List.of("USER"));
        when(permissionService.hasPermission(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("WATCHLIST_MANAGE")
        )).thenReturn(true);
        when(permissionService.isAccountActive(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(userDataService.getWatchlist(42L)).thenReturn(List.<WatchlistItem>of());

        mockMvc.perform(get("/api/me/watchlist").with(user(principal)))
                .andExpect(status().isOk());

        verify(userDataService).getWatchlist(42L);
    }

    @Test
    void authenticatedUserWithoutPermissionCannotReadPrivateData() throws Exception {
        var principal = new AppUserPrincipal(42L, "alice", "hash", true, true, List.of("USER"));
        when(permissionService.hasPermission(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("WATCHLIST_MANAGE")
        )).thenReturn(false);
        when(permissionService.isAccountActive(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        mockMvc.perform(get("/api/me/watchlist").with(user(principal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void privateWriteRequiresCsrfToken() throws Exception {
        var principal = new AppUserPrincipal(42L, "alice", "hash", true, true, List.of("USER"));
        mockMvc.perform(post("/api/me/watchlist/1").with(user(principal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void privateWriteAcceptsCsrfAndScopesToAuthenticatedUser() throws Exception {
        var principal = new AppUserPrincipal(42L, "alice", "hash", true, true, List.of("USER"));
        when(permissionService.hasPermission(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("WATCHLIST_MANAGE")
        )).thenReturn(true);
        when(permissionService.isAccountActive(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        mockMvc.perform(post("/api/me/watchlist/1").with(user(principal)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(userDataService).addToWatchlist(42L, 1L);
    }

    @Test
    void portfolioIsScopedToAuthenticatedUserAndRequiresInvestmentPermission() throws Exception {
        var principal = new AppUserPrincipal(42L, "alice", "hash", true, true, List.of("USER"));
        when(permissionService.hasPermission(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("INVESTMENT_MANAGE")
        )).thenReturn(true);
        when(userDataService.getPortfolio(42L)).thenReturn(new PortfolioResponse(
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, null, List.of()
        ));

        mockMvc.perform(get("/api/me/portfolio").with(user(principal)))
                .andExpect(status().isOk());

        verify(userDataService).getPortfolio(42L);
    }
}
