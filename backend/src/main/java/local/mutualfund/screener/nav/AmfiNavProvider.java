package local.mutualfund.screener.nav;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AmfiNavProvider {

    public static final String PROVIDER_NAME = "AMFI";
    private static final DateTimeFormatter AMFI_DATE =
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH);

    private final String feedUrl;
    private final HttpClient httpClient;

    public AmfiNavProvider(@Value("${app.nav.amfi-url:https://www.amfiindia.com/spages/NAVAll.txt}") String feedUrl) {
        this.feedUrl = feedUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Feed fetchLatest() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                    .timeout(Duration.ofSeconds(45))
                    .header("Accept", "text/plain")
                    .header("User-Agent", "MutualFundScreener/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8)
            );
            if (response.statusCode() != 200) {
                throw new NavProviderException(
                        "AMFI NAV feed returned HTTP " + response.statusCode(), response.statusCode()
                );
            }
            Feed feed = parseFeed(response.body());
            if (feed.records().isEmpty()) {
                throw new NavProviderException("AMFI NAV feed contained no valid NAV records", response.statusCode());
            }
            return feed;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NavProviderException("AMFI NAV feed request was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new NavProviderException("Could not retrieve or parse the AMFI NAV feed", exception);
        }
    }

    static Feed parseFeed(String content) {
        List<NavRecord> records = new ArrayList<>();
        int skipped = 0;
        for (String line : content.split("\\R")) {
            String[] fields = line.replace("\uFEFF", "").split(";", -1);
            if (fields.length < 6 || !fields[0].trim().matches("\\d+")) {
                continue;
            }
            try {
                String schemeCode = fields[0].trim();
                BigDecimal value = new BigDecimal(fields[4].trim());
                LocalDate date = LocalDate.parse(fields[5].trim(), AMFI_DATE);
                if (value.signum() < 0) {
                    skipped++;
                    continue;
                }
                records.add(new NavRecord(schemeCode, date, value));
            } catch (NumberFormatException | DateTimeParseException exception) {
                skipped++;
            }
        }
        return new Feed(List.copyOf(records), skipped);
    }

    public record NavRecord(String externalSchemeCode, LocalDate navDate, BigDecimal navValue) {
    }

    public record Feed(List<NavRecord> records, int skippedRecords) {
    }

    public static final class NavProviderException extends RuntimeException {
        private final Integer responseCode;

        public NavProviderException(String message, Integer responseCode) {
            super(message);
            this.responseCode = responseCode;
        }

        public NavProviderException(String message, Throwable cause) {
            super(message, cause);
            this.responseCode = null;
        }

        public Integer getResponseCode() {
            return responseCode;
        }
    }
}
