package local.mutualfund.screener.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import local.mutualfund.screener.dto.FundHouseResponse;
import local.mutualfund.screener.dto.FundSchemeResponse;
import local.mutualfund.screener.dto.NavDtos.DashboardResponse;
import local.mutualfund.screener.dto.NavDtos.SchemeDetailsResponse;
import local.mutualfund.screener.service.NavRefreshService;
import local.mutualfund.screener.service.FundCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({FundHouseController.class, FundSchemeController.class, NavController.class})
@AutoConfigureMockMvc(addFilters = false)
class FundCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FundCatalogService fundCatalogService;

    @MockitoBean
    private NavRefreshService navRefreshService;

    @Test
    void listsFundHouses() throws Exception {
        when(fundCatalogService.getFundHouses(true)).thenReturn(List.of(
                new FundHouseResponse(1L, "DEMO-AMC", "Demonstration AMC", "IN", null)
        ));

        mockMvc.perform(get("/api/fund-houses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("DEMO-AMC"))
                .andExpect(jsonPath("$[0].countryCode").value("IN"));

        verify(fundCatalogService).getFundHouses(true);
    }

    @Test
    void listsSchemesWithNormalizedParametersAndPagination() throws Exception {
        when(fundCatalogService.getSchemes("DEMO-AMC", "INDEX", "DIRECT", "GROWTH", "nifty", 0, 10))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/schemes")
                        .param("houseCode", "DEMO-AMC")
                        .param("category", "INDEX")
                        .param("planType", "DIRECT")
                        .param("optionType", "GROWTH")
                        .param("search", "nifty")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(fundCatalogService).getSchemes(
                eq("DEMO-AMC"), eq("INDEX"), eq("DIRECT"), eq("GROWTH"),
                eq("nifty"), eq(0), eq(10)
        );
    }

    @Test
    void rejectsPageSizesAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/schemes").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameters"));
    }

    @Test
    void rejectsNavDateRangeWhenStartIsAfterEnd() throws Exception {
        mockMvc.perform(get("/api/schemes/1/nav")
                        .param("fromDate", "2026-02-01")
                        .param("toDate", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNavDashboardMetrics() throws Exception {
        when(navRefreshService.getDashboard()).thenReturn(
                new DashboardResponse(12, 9, java.time.LocalDate.of(2026, 11, 8),
                        null, "SUCCESS", null, 18)
        );

        mockMvc.perform(get("/api/nav/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSchemes").value(12))
                .andExpect(jsonPath("$.schemesWithNav").value(9))
                .andExpect(jsonPath("$.lastRefreshRecords").value(18));
    }

    @Test
    void returnsFundDetailsWithLatestNav() throws Exception {
        when(fundCatalogService.getSchemeDetails(1L)).thenReturn(
                new SchemeDetailsResponse(1L, "SCHEME-1", "Example Fund", "AMC-1",
                        "Example AMC", "INDEX", "DIRECT", "GROWTH", null, null,
                        java.time.LocalDate.of(2026, 11, 8), new java.math.BigDecimal("12.34"), "AMFI")
        );

        mockMvc.perform(get("/api/schemes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Example Fund"))
                .andExpect(jsonPath("$.latestNav").value(12.34))
                .andExpect(jsonPath("$.latestNavProvider").value("AMFI"));
    }
}
