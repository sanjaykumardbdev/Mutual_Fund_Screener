package local.mutualfund.screener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MutualFundScreenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MutualFundScreenerApplication.class, args);
    }
}
