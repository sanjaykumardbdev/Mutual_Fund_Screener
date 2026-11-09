package local.mutualfund.screener.controller;

import java.time.Instant;
import local.mutualfund.screener.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public HealthResponse getHealth() {
        healthService.verifyDatabaseConnection();
        return new HealthResponse("UP", "UP", Instant.now());
    }

    public record HealthResponse(String status, String database, Instant checkedAt) {
    }
}
