package local.mutualfund.screener.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AmfiNavProviderTest {

    @Test
    void parsesAmfiSemicolonRecordsAndSkipsHeadersAndInvalidValues() {
        String feed = """
                Scheme Code;ISIN Div Payout/ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date
                100001;INF000000001;INF000000002;Example Direct Growth;123.4567;08-Nov-2026
                100002;INF000000003;INF000000004;Suspended Scheme;N.A.;08-Nov-2026
                Open Ended Schemes (Equity)
                """;

        AmfiNavProvider.Feed parsed = AmfiNavProvider.parseFeed(feed);

        assertEquals(1, parsed.records().size());
        assertEquals(1, parsed.skippedRecords());
        assertEquals("100001", parsed.records().get(0).externalSchemeCode());
        assertEquals(new BigDecimal("123.4567"), parsed.records().get(0).navValue());
        assertEquals(LocalDate.of(2026, 11, 8), parsed.records().get(0).navDate());
    }

    @Test
    void parsesUtf8BomAndZeroNav() {
        AmfiNavProvider.Feed parsed = AmfiNavProvider.parseFeed(
                "\uFEFF100003;INF000000005;INF000000006;Example;0;1-Jan-2026"
        );

        assertEquals(1, parsed.records().size());
        assertEquals(BigDecimal.ZERO, parsed.records().get(0).navValue());
    }
}
