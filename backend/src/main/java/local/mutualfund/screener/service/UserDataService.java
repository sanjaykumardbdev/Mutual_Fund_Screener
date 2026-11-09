package local.mutualfund.screener.service;

import java.util.List;
import local.mutualfund.screener.dto.AuthDtos.InvestmentRequest;
import local.mutualfund.screener.dto.AuthDtos.InvestmentResponse;
import local.mutualfund.screener.dto.AuthDtos.PortfolioHolding;
import local.mutualfund.screener.dto.AuthDtos.PortfolioResponse;
import local.mutualfund.screener.dto.AuthDtos.SipRequest;
import local.mutualfund.screener.dto.AuthDtos.SipResponse;
import local.mutualfund.screener.dto.AuthDtos.UserPreferencesRequest;
import local.mutualfund.screener.dto.AuthDtos.UserPreferencesResponse;
import local.mutualfund.screener.dto.AuthDtos.WatchlistItem;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserDataService {

    private final JdbcTemplate jdbcTemplate;

    public UserDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WatchlistItem> getWatchlist(long userId) {
        return jdbcTemplate.query(
                """
                SELECT s.scheme_id, s.scheme_code, s.scheme_name, h.house_name,
                       s.category, s.plan_type, s.option_type, w.added_at,
                       nav.nav_date AS latest_nav_date, nav.nav_value AS latest_nav
                FROM user_fund_watchlist w
                JOIN mutual_fund_schemes s ON s.scheme_id = w.scheme_id
                JOIN mutual_fund_houses h ON h.house_id = s.house_id
                OUTER APPLY (
                    SELECT n.nav_date, n.nav_value
                    FROM mutual_fund_nav_history n
                    WHERE n.scheme_id = s.scheme_id
                    ORDER BY n.nav_date DESC
                    FETCH FIRST 1 ROW ONLY
                ) nav
                WHERE w.user_id = ? AND w.is_active = 1
                ORDER BY h.house_name, s.scheme_name
                """,
                (resultSet, rowNumber) -> new WatchlistItem(
                        resultSet.getLong("scheme_id"),
                        resultSet.getString("scheme_code"),
                        resultSet.getString("scheme_name"),
                        resultSet.getString("house_name"),
                        resultSet.getString("category"),
                        resultSet.getString("plan_type"),
                        resultSet.getString("option_type"),
                        resultSet.getDate("latest_nav_date") == null
                                ? null : resultSet.getDate("latest_nav_date").toLocalDate(),
                        resultSet.getBigDecimal("latest_nav"),
                        resultSet.getTimestamp("added_at").toInstant()
                ),
                userId
        );
    }

    @Transactional
    public void addToWatchlist(long userId, long schemeId) {
        if (jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mutual_fund_schemes WHERE scheme_id = ? AND is_active = 1",
                Integer.class,
                schemeId
        ) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active scheme not found");
        }
        try {
            jdbcTemplate.update(
                    """
                    MERGE INTO user_fund_watchlist target
                    USING (SELECT ? AS user_id, ? AS scheme_id FROM dual) source
                    ON (target.user_id = source.user_id AND target.scheme_id = source.scheme_id)
                    WHEN MATCHED THEN UPDATE SET
                        target.is_active = 1, target.added_at = SYSTIMESTAMP
                    WHEN NOT MATCHED THEN INSERT (user_id, scheme_id, is_active)
                        VALUES (source.user_id, source.scheme_id, 1)
                    """,
                    userId,
                    schemeId
            );
        } catch (DuplicateKeyException exception) {
            // A concurrent request may insert the same unique user/scheme pair.
            if (jdbcTemplate.update(
                    """
                    UPDATE user_fund_watchlist SET is_active = 1, added_at = SYSTIMESTAMP
                    WHERE user_id = ? AND scheme_id = ?
                    """,
                    userId,
                    schemeId
            ) == 0) {
                throw exception;
            }
        }
    }

    @Transactional
    public void removeFromWatchlist(long userId, long schemeId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE user_fund_watchlist SET is_active = 0
                WHERE user_id = ? AND scheme_id = ? AND is_active = 1
                """,
                userId,
                schemeId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist entry not found");
        }
    }

    public List<InvestmentResponse> getInvestments(long userId) {
        return jdbcTemplate.query(
                """
                SELECT i.investment_id, i.scheme_id, s.scheme_name, i.transaction_type,
                       i.transaction_date, i.transaction_amount, i.units, i.transaction_nav, i.remarks
                FROM user_investments i
                JOIN mutual_fund_schemes s ON s.scheme_id = i.scheme_id
                WHERE i.user_id = ?
                ORDER BY i.transaction_date DESC, i.investment_id DESC
                """,
                (resultSet, rowNumber) -> new InvestmentResponse(
                        resultSet.getLong("investment_id"),
                        resultSet.getLong("scheme_id"),
                        resultSet.getString("scheme_name"),
                        resultSet.getString("transaction_type"),
                        resultSet.getDate("transaction_date").toLocalDate(),
                        resultSet.getBigDecimal("transaction_amount"),
                        resultSet.getBigDecimal("units"),
                        resultSet.getBigDecimal("transaction_nav"),
                        resultSet.getString("remarks")
                ),
                userId
        );
    }

    public PortfolioResponse getPortfolio(long userId) {
        List<PortfolioHolding> holdings = jdbcTemplate.query(
                """
                SELECT s.scheme_id, s.scheme_name, h.house_name,
                       NVL(SUM(CASE WHEN i.transaction_type IN ('LUMPSUM', 'SIP')
                                    THEN i.transaction_amount ELSE 0 END), 0) invested_amount,
                       NVL(SUM(CASE WHEN i.transaction_type IN ('LUMPSUM', 'SIP')
                                    THEN i.units WHEN i.transaction_type = 'REDEMPTION'
                                    THEN -i.units ELSE 0 END), 0) units_held,
                       nav.nav_value latest_nav, nav.nav_date latest_nav_date,
                       NVL(SUM(CASE WHEN i.transaction_type = 'REDEMPTION'
                                    THEN i.transaction_amount ELSE 0 END), 0) redemption_proceeds,
                       NVL(SUM(CASE WHEN i.transaction_type = 'IDCW'
                                    THEN i.transaction_amount ELSE 0 END), 0) dividends
                  FROM user_investments i
                  JOIN mutual_fund_schemes s ON s.scheme_id = i.scheme_id
                  JOIN mutual_fund_houses h ON h.house_id = s.house_id
                  OUTER APPLY (
                      SELECT n.nav_value, n.nav_date
                        FROM mutual_fund_nav_history n
                       WHERE n.scheme_id = s.scheme_id
                       ORDER BY n.nav_date DESC FETCH FIRST 1 ROW ONLY
                  ) nav
                 WHERE i.user_id = ?
                 GROUP BY s.scheme_id, s.scheme_name, h.house_name, nav.nav_value, nav.nav_date
                 ORDER BY s.scheme_name
                """,
                (rs, row) -> {
                    java.math.BigDecimal invested = rs.getBigDecimal("invested_amount");
                    java.math.BigDecimal units = rs.getBigDecimal("units_held");
                    java.math.BigDecimal nav = rs.getBigDecimal("latest_nav");
                    java.math.BigDecimal redemption = rs.getBigDecimal("redemption_proceeds");
                    java.math.BigDecimal dividends = rs.getBigDecimal("dividends");
                    java.math.BigDecimal value = nav == null ? null : nav.multiply(units);
                    java.math.BigDecimal gain = value == null
                            ? null : value.add(redemption).add(dividends).subtract(invested);
                    java.math.BigDecimal percent = gain == null || invested.signum() == 0
                            ? null : gain.multiply(java.math.BigDecimal.valueOf(100))
                                    .divide(invested, 2, java.math.RoundingMode.HALF_UP);
                    return new PortfolioHolding(
                            rs.getLong("scheme_id"), rs.getString("scheme_name"), rs.getString("house_name"),
                            invested, units, nav,
                            rs.getDate("latest_nav_date") == null ? null : rs.getDate("latest_nav_date").toLocalDate(),
                            value, redemption, dividends, gain, percent
                    );
                },
                userId
        );
        java.math.BigDecimal invested = sum(holdings, PortfolioHolding::investedAmount);
        java.math.BigDecimal currentValue = sumNullable(holdings, PortfolioHolding::currentValue);
        java.math.BigDecimal redemptions = sum(holdings, PortfolioHolding::redemptionProceeds);
        java.math.BigDecimal dividends = sum(holdings, PortfolioHolding::dividends);
        java.math.BigDecimal gain = currentValue == null
                ? null : currentValue.add(redemptions).add(dividends).subtract(invested);
        java.math.BigDecimal percent = gain == null || invested.signum() == 0
                ? null : gain.multiply(java.math.BigDecimal.valueOf(100))
                        .divide(invested, 2, java.math.RoundingMode.HALF_UP);
        return new PortfolioResponse(invested, currentValue, redemptions, dividends, gain, percent, holdings);
    }

    public List<SipResponse> getSips(long userId) {
        return jdbcTemplate.query(
                """
                SELECT sip.sip_id, sip.scheme_id, scheme.scheme_name, sip.sip_amount,
                       sip.sip_frequency, sip.start_date, sip.end_date,
                       sip.next_installment_date, sip.sip_status
                  FROM user_sip_details sip
                  JOIN mutual_fund_schemes scheme ON scheme.scheme_id = sip.scheme_id
                 WHERE sip.user_id = ?
                 ORDER BY sip.created_at DESC
                """,
                (rs, row) -> new SipResponse(
                        rs.getLong("sip_id"), rs.getLong("scheme_id"), rs.getString("scheme_name"),
                        rs.getBigDecimal("sip_amount"), rs.getString("sip_frequency"),
                        rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date") == null ? null : rs.getDate("end_date").toLocalDate(),
                        rs.getDate("next_installment_date") == null
                                ? null : rs.getDate("next_installment_date").toLocalDate(),
                        rs.getString("sip_status")
                ),
                userId
        );
    }

    @Transactional
    public long addSip(long userId, SipRequest sip) {
        if (sip.endDate() != null && sip.endDate().isBefore(sip.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SIP end date must be on or after start date");
        }
        if (jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mutual_fund_schemes WHERE scheme_id = ? AND is_active = 1",
                Integer.class, sip.schemeId()
        ) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active scheme not found");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO user_sip_details (
                    user_id, scheme_id, sip_amount, sip_frequency, start_date, end_date,
                    next_installment_date, sip_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """,
                new String[]{"SIP_ID"}
            );
            statement.setLong(1, userId);
            statement.setLong(2, sip.schemeId());
            statement.setBigDecimal(3, sip.amount());
            statement.setString(4, sip.frequency());
            statement.setDate(5, java.sql.Date.valueOf(sip.startDate()));
            if (sip.endDate() == null) statement.setNull(6, java.sql.Types.DATE);
            else statement.setDate(6, java.sql.Date.valueOf(sip.endDate()));
            statement.setDate(7, java.sql.Date.valueOf(sip.startDate()));
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("Oracle did not return the SIP plan ID.");
        }
        return generatedId.longValue();
    }

    @Transactional
    public void updateSipStatus(long userId, long sipId, String status) {
        int updated = jdbcTemplate.update(
                """
                UPDATE user_sip_details SET sip_status = ?, updated_at = SYSTIMESTAMP
                 WHERE sip_id = ? AND user_id = ?
                """,
                status, sipId, userId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SIP plan not found");
        }
    }

    public UserPreferencesResponse getPreferences(long userId) {
        jdbcTemplate.update(
                """
                MERGE INTO user_preferences target
                USING (SELECT ? user_id FROM dual) src
                   ON (target.user_id = src.user_id)
                WHEN NOT MATCHED THEN INSERT (user_id) VALUES (src.user_id)
                """,
                userId
        );
        return jdbcTemplate.queryForObject(
                """
                SELECT refresh_frequency_minutes, offline_cache_days
                  FROM user_preferences WHERE user_id = ?
                """,
                (rs, row) -> new UserPreferencesResponse(
                        rs.getInt("refresh_frequency_minutes"), rs.getInt("offline_cache_days")
                ),
                userId
        );
    }

    @Transactional
    public UserPreferencesResponse updatePreferences(long userId, UserPreferencesRequest preferences) {
        jdbcTemplate.update(
                """
                MERGE INTO user_preferences target
                USING (SELECT ? user_id FROM dual) src
                   ON (target.user_id = src.user_id)
                WHEN MATCHED THEN UPDATE SET
                    target.refresh_frequency_minutes = ?,
                    target.offline_cache_days = ?,
                    target.updated_at = SYSTIMESTAMP
                WHEN NOT MATCHED THEN INSERT (
                    user_id, refresh_frequency_minutes, offline_cache_days
                ) VALUES (src.user_id, ?, ?)
                """,
                userId, preferences.refreshFrequencyMinutes(), preferences.offlineCacheDays(),
                preferences.refreshFrequencyMinutes(), preferences.offlineCacheDays()
        );
        return new UserPreferencesResponse(
                preferences.refreshFrequencyMinutes(), preferences.offlineCacheDays()
        );
    }

    @Transactional
    public void addInvestment(long userId, InvestmentRequest investment) {
        String type = investment.transactionType().toUpperCase();
        if (jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mutual_fund_schemes WHERE scheme_id = ?",
                Integer.class,
                investment.schemeId()
        ) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheme not found");
        }
        if ("IDCW".equals(type)) {
            if (investment.units() != null && investment.units().signum() != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDCW transactions must have zero units");
            }
            if (investment.sipId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDCW cannot reference a SIP");
            }
        } else if (investment.nav() == null || investment.units() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "NAV and units are required for this transaction type"
            );
        }

        Long sipId = investment.sipId();
        if ("SIP".equals(type)) {
            if (sipId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SIP transactions require sipId");
            }
            int ownedSip = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM user_sip_details
                    WHERE sip_id = ? AND user_id = ? AND scheme_id = ?
                    """,
                    Integer.class,
                    sipId,
                    userId,
                    investment.schemeId()
            );
            if (ownedSip == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SIP plan not found");
            }
        } else if (sipId != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only SIP transactions can reference a SIP plan"
            );
        }

        Object[] values = {
                userId, investment.schemeId(), sipId, type,
                java.sql.Date.valueOf(investment.transactionDate()), investment.nav(),
                investment.amount(),
                "IDCW".equals(type) ? java.math.BigDecimal.ZERO : investment.units(),
                investment.remarks(), investment.clientRequestId()
        };
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO user_investments (
                        user_id, scheme_id, sip_id, transaction_type, transaction_date,
                        transaction_nav, transaction_amount, units, remarks, client_request_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    values
            );
        } catch (DuplicateKeyException exception) {
            if (investment.clientRequestId() == null || !investmentMatches(userId, investment)) {
                throw exception;
            }
        }
    }

    private boolean investmentMatches(long userId, InvestmentRequest investment) {
        List<Boolean> matches = jdbcTemplate.query(
                """
                SELECT 1 FROM user_investments
                 WHERE user_id = ? AND client_request_id = ?
                   AND scheme_id = ? AND transaction_type = ?
                   AND transaction_date = ? AND transaction_amount = ?
                   AND units = ? AND (transaction_nav = ? OR (transaction_nav IS NULL AND ? IS NULL))
                   AND NVL(remarks, CHR(0)) = NVL(?, CHR(0))
                """,
                (rs, row) -> Boolean.TRUE,
                userId, investment.clientRequestId(), investment.schemeId(),
                investment.transactionType().toUpperCase(),
                java.sql.Date.valueOf(investment.transactionDate()), investment.amount(),
                "IDCW".equalsIgnoreCase(investment.transactionType())
                        ? java.math.BigDecimal.ZERO : investment.units(),
                investment.nav(), investment.nav(), investment.remarks()
        );
        return !matches.isEmpty();
    }

    private java.math.BigDecimal sum(
            List<PortfolioHolding> holdings,
            java.util.function.Function<PortfolioHolding, java.math.BigDecimal> value
    ) {
        return holdings.stream().map(value).filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    private java.math.BigDecimal sumNullable(
            List<PortfolioHolding> holdings,
            java.util.function.Function<PortfolioHolding, java.math.BigDecimal> value
    ) {
        if (holdings.stream().anyMatch(holding -> value.apply(holding) == null)) {
            return null;
        }
        return sum(holdings, value);
    }
}
