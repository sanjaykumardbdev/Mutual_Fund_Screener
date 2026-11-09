package local.mutualfund.screener.service;

import java.util.List;
import local.mutualfund.screener.dto.AuthDtos.InvestmentRequest;
import local.mutualfund.screener.dto.AuthDtos.InvestmentResponse;
import local.mutualfund.screener.dto.AuthDtos.WatchlistItem;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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

        jdbcTemplate.update(
                """
                INSERT INTO user_investments (
                    user_id, scheme_id, sip_id, transaction_type, transaction_date,
                    transaction_nav, transaction_amount, units, remarks
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                investment.schemeId(),
                sipId,
                type,
                java.sql.Date.valueOf(investment.transactionDate()),
                investment.nav(),
                investment.amount(),
                "IDCW".equals(type) ? java.math.BigDecimal.ZERO : investment.units(),
                investment.remarks()
        );
    }
}
