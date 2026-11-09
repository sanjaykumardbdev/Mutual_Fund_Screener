package local.mutualfund.screener.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledNavRefresh {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledNavRefresh.class);
    private final NavRefreshService navRefreshService;

    public ScheduledNavRefresh(NavRefreshService navRefreshService) {
        this.navRefreshService = navRefreshService;
    }

    @Scheduled(cron = "${app.nav.refresh.cron:0 30 19 * * *}", zone = "Asia/Kolkata")
    public void refreshAllMappedSchemes() {
        try {
            var result = navRefreshService.refresh("ALL", null, null);
            LOGGER.info("Scheduled AMFI NAV refresh completed: result={}, records={}",
                    result.result(), result.recordsProcessed());
        } catch (RuntimeException exception) {
            LOGGER.error("Scheduled AMFI NAV refresh failed.", exception);
        }
    }
}
