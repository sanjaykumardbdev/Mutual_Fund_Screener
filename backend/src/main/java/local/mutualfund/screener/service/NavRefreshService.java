package local.mutualfund.screener.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import local.mutualfund.screener.dto.NavDtos.DashboardResponse;
import local.mutualfund.screener.dto.NavDtos.RefreshResponse;
import local.mutualfund.screener.dto.NavDtos.SchemeDetailsResponse;
import local.mutualfund.screener.nav.AmfiNavProvider;
import local.mutualfund.screener.nav.AmfiNavProvider.Feed;
import local.mutualfund.screener.nav.AmfiNavProvider.NavRecord;
import local.mutualfund.screener.nav.AmfiNavProvider.NavProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NavRefreshService {

    private static final String NAV_MERGE_SQL = """
            MERGE INTO mutual_fund_nav_history target
            USING (SELECT ? scheme_id, ? nav_date FROM dual) src
            ON (target.scheme_id = src.scheme_id AND target.nav_date = src.nav_date)
            WHEN MATCHED THEN UPDATE SET
                target.nav_value = ?,
                target.provider_name = ?,
                target.published_at = NULL,
                target.retrieved_at = SYSTIMESTAMP,
                target.source_record_hash = ?
            WHEN NOT MATCHED THEN INSERT (
                scheme_id, nav_date, nav_value, provider_name, published_at,
                retrieved_at, source_record_hash
            ) VALUES (
                src.scheme_id, src.nav_date, ?, ?, NULL, SYSTIMESTAMP, ?
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AmfiNavProvider provider;
    private final TransactionTemplate transactionTemplate;

    public NavRefreshService(
            JdbcTemplate jdbcTemplate,
            AmfiNavProvider provider,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.provider = provider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public DashboardResponse getDashboard() {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    (SELECT COUNT(*) FROM mutual_fund_schemes WHERE is_active = 1) total_schemes,
                    (SELECT COUNT(DISTINCT n.scheme_id)
                       FROM mutual_fund_nav_history n
                       JOIN mutual_fund_schemes s ON s.scheme_id = n.scheme_id
                      WHERE s.is_active = 1) schemes_with_nav,
                    (SELECT MAX(nav_date) FROM mutual_fund_nav_history) latest_nav_date,
                    (SELECT MAX(retrieved_at) FROM mutual_fund_nav_history) last_retrieved_at,
                    (SELECT result FROM nav_refresh_log
                      ORDER BY refresh_started_at DESC FETCH FIRST 1 ROWS ONLY) last_refresh_result,
                    (SELECT refresh_started_at FROM nav_refresh_log
                      ORDER BY refresh_started_at DESC FETCH FIRST 1 ROWS ONLY) last_refresh_at,
                    (SELECT records_processed FROM nav_refresh_log
                      ORDER BY refresh_started_at DESC FETCH FIRST 1 ROWS ONLY) last_refresh_records
                FROM dual
                """,
                (resultSet, rowNumber) -> new DashboardResponse(
                        resultSet.getLong("total_schemes"),
                        resultSet.getLong("schemes_with_nav"),
                        resultSet.getDate("latest_nav_date") == null
                                ? null : resultSet.getDate("latest_nav_date").toLocalDate(),
                        resultSet.getTimestamp("last_retrieved_at") == null
                                ? null : resultSet.getTimestamp("last_retrieved_at").toLocalDateTime(),
                        resultSet.getString("last_refresh_result"),
                        resultSet.getTimestamp("last_refresh_at") == null
                                ? null : resultSet.getTimestamp("last_refresh_at").toLocalDateTime(),
                        resultSet.getLong("last_refresh_records")
                )
        );
    }

    public SchemeDetailsResponse getSchemeDetails(long schemeId) {
        List<SchemeDetailsResponse> details = jdbcTemplate.query(
                """
                SELECT s.scheme_id, s.scheme_code, s.scheme_name, h.house_code, h.house_name,
                       s.category, s.plan_type, s.option_type, s.benchmark, s.isin,
                       n.nav_date latest_nav_date, n.nav_value latest_nav, n.provider_name latest_nav_provider
                  FROM mutual_fund_schemes s
                  JOIN mutual_fund_houses h ON h.house_id = s.house_id
                  LEFT JOIN (
                      SELECT scheme_id, nav_date, nav_value, provider_name,
                             ROW_NUMBER() OVER (PARTITION BY scheme_id ORDER BY nav_date DESC) row_num
                        FROM mutual_fund_nav_history
                  ) n ON n.scheme_id = s.scheme_id AND n.row_num = 1
                 WHERE s.scheme_id = ? AND s.is_active = 1
                """,
                (resultSet, rowNumber) -> new SchemeDetailsResponse(
                        resultSet.getLong("scheme_id"),
                        resultSet.getString("scheme_code"),
                        resultSet.getString("scheme_name"),
                        resultSet.getString("house_code"),
                        resultSet.getString("house_name"),
                        resultSet.getString("category"),
                        resultSet.getString("plan_type"),
                        resultSet.getString("option_type"),
                        resultSet.getString("benchmark"),
                        resultSet.getString("isin"),
                        resultSet.getDate("latest_nav_date") == null
                                ? null : resultSet.getDate("latest_nav_date").toLocalDate(),
                        resultSet.getBigDecimal("latest_nav"),
                        resultSet.getString("latest_nav_provider")
                ),
                schemeId
        );
        if (details.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheme not found");
        }
        return details.get(0);
    }

    public RefreshResponse refresh(String scope, Long schemeId, Long requestedByUserId) {
        validateRefreshRequest(scope, schemeId);
        String normalizedScope = scope.toUpperCase(java.util.Locale.ROOT);
        long refreshId = createRefreshLog(normalizedScope, schemeId, requestedByUserId);
        LocalDateTime startedAt = LocalDateTime.now();
        List<Mapping> mappings;
        try {
            mappings = getMappings(normalizedScope, schemeId, requestedByUserId);
        } catch (org.springframework.dao.DataAccessException exception) {
            String message = "Could not load active AMFI scheme mappings.";
            logSourceResult("NOT_REQUESTED", null, 0, message);
            finishFailedRefresh(refreshId, message);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
        }
        if (mappings.isEmpty()) {
            String message = "No active AMFI scheme mappings are available for this refresh.";
            finishFailedRefresh(refreshId, message);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
        }

        Feed feed;
        try {
            feed = provider.fetchLatest();
        } catch (NavProviderException exception) {
            logSourceFailure(exception);
            finishFailedRefresh(refreshId, exception.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "AMFI NAV refresh failed: " + exception.getMessage()
            );
        }

        try {
            return persistFeed(refreshId, normalizedScope, mappings, feed, startedAt);
        } catch (ResponseStatusException exception) {
            logSourceResult("200", 200, feed.records().size(), exception.getReason());
            finishFailedRefresh(refreshId, exception.getReason());
            throw exception;
        } catch (org.springframework.dao.DataAccessException
                 | org.springframework.transaction.TransactionException exception) {
            String message = safeMessage(exception);
            logSourceResult("ERROR", null, 0, message);
            finishFailedRefresh(refreshId, message);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "NAV data could not be saved.");
        }
    }

    private RefreshResponse persistFeed(
            long refreshId,
            String scope,
            List<Mapping> mappings,
            Feed feed,
            LocalDateTime startedAt
    ) {
        Map<String, NavRecord> feedByCode = new HashMap<>();
        for (NavRecord record : feed.records()) {
            if (feedByCode.putIfAbsent(record.externalSchemeCode(), record) != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AMFI feed contains duplicate scheme code " + record.externalSchemeCode()
                );
            }
        }

        List<ImportRow> importRows = new ArrayList<>();
        for (Mapping mapping : mappings) {
            NavRecord record = feedByCode.get(mapping.externalCode());
            if (record != null) {
                importRows.add(new ImportRow(mapping.schemeId(), record));
            }
        }
        if (importRows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "The AMFI feed did not contain any mapped schemes."
            );
        }

        int missingMappings = mappings.size() - importRows.size();
        String result = missingMappings == 0 ? "SUCCESS" : "PARTIAL";
        String message = missingMappings == 0
                ? "NAV data refreshed successfully."
                : "Refreshed " + importRows.size() + " mapped schemes; "
                        + missingMappings + " mapped schemes were absent from the feed.";
        LocalDateTime completedAt = LocalDateTime.now();

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.batchUpdate(NAV_MERGE_SQL, importRows, 500, (statement, importRow) -> {
                NavRecord record = importRow.record();
                String hash = sourceHash(importRow.schemeId(), record);
                statement.setLong(1, importRow.schemeId());
                statement.setDate(2, Date.valueOf(record.navDate()));
                statement.setBigDecimal(3, record.navValue());
                statement.setString(4, AmfiNavProvider.PROVIDER_NAME);
                statement.setString(5, hash);
                statement.setBigDecimal(6, record.navValue());
                statement.setString(7, AmfiNavProvider.PROVIDER_NAME);
                statement.setString(8, hash);
            });
            jdbcTemplate.update(
                    """
                    INSERT INTO nav_data_source_log (
                        provider_name, operation_name, response_status, response_code,
                        response_payload, fetched_at
                    ) VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP)
                    """,
                    AmfiNavProvider.PROVIDER_NAME, "NAV_ALL_IMPORT", "200", 200,
                    "records=" + feed.records().size() + "; skipped=" + feed.skippedRecords()
            );
            jdbcTemplate.update(
                    """
                    UPDATE nav_refresh_log
                       SET refresh_completed_at = SYSTIMESTAMP, result = ?,
                           records_processed = ?, error_message = ?
                     WHERE refresh_id = ?
                    """,
                    result, importRows.size(), missingMappings == 0 ? null : message, refreshId
            );
            jdbcTemplate.update(
                    """
                    UPDATE data_sync_status
                       SET status = 'SUCCESS', last_success_at = SYSTIMESTAMP,
                           record_count = ?, last_error = ?, updated_at = SYSTIMESTAMP
                     WHERE sync_name = 'NAV_DAILY' AND provider_name = ?
                    """,
                    importRows.size(), missingMappings == 0 ? null : message, AmfiNavProvider.PROVIDER_NAME
            );
        });

        return new RefreshResponse(
                refreshId, scope, result, importRows.size(), AmfiNavProvider.PROVIDER_NAME,
                startedAt, completedAt, message
        );
    }

    private List<Mapping> getMappings(String scope, Long schemeId, Long userId) {
        String sql = """
                SELECT m.scheme_id, m.external_scheme_code
                  FROM nav_scheme_mappings m
                  JOIN mutual_fund_schemes s ON s.scheme_id = m.scheme_id
                """;
        return switch (scope) {
            case "SCHEME" -> jdbcTemplate.query(
                    sql + """
                    WHERE m.provider_name = ? AND m.is_active = 1 AND s.is_active = 1
                      AND m.scheme_id = ?
                    """,
                    (rs, row) -> new Mapping(rs.getLong("scheme_id"), rs.getString("external_scheme_code")),
                    AmfiNavProvider.PROVIDER_NAME, schemeId
            );
            case "WATCHLIST" -> jdbcTemplate.query(
                    sql + """
                    JOIN user_fund_watchlist w ON w.scheme_id = s.scheme_id
                    WHERE m.provider_name = ? AND m.is_active = 1 AND s.is_active = 1
                      AND w.user_id = ? AND w.is_active = 1
                    """,
                    (rs, row) -> new Mapping(rs.getLong("scheme_id"), rs.getString("external_scheme_code")),
                    AmfiNavProvider.PROVIDER_NAME, userId
            );
            default -> jdbcTemplate.query(
                    sql + """
                    WHERE m.provider_name = ? AND m.is_active = 1 AND s.is_active = 1
                    """,
                    (rs, row) -> new Mapping(rs.getLong("scheme_id"), rs.getString("external_scheme_code")),
                    AmfiNavProvider.PROVIDER_NAME
            );
        };
    }

    private long createRefreshLog(String scope, Long schemeId, Long userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO nav_refresh_log (
                        scheme_id, requested_by_user_id, refresh_scope, result, provider_name
                    ) VALUES (?, ?, ?, 'RUNNING', ?)
                    """,
                    new String[]{"REFRESH_ID"}
            );
            if (schemeId == null) {
                statement.setNull(1, java.sql.Types.NUMERIC);
            } else {
                statement.setLong(1, schemeId);
            }
            if (userId == null) {
                statement.setNull(2, java.sql.Types.NUMERIC);
            } else {
                statement.setLong(2, userId);
            }
            statement.setString(3, scope);
            statement.setString(4, AmfiNavProvider.PROVIDER_NAME);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Oracle did not return the NAV refresh log ID.");
        }
        jdbcTemplate.update(
                """
                MERGE INTO data_sync_status target
                USING (SELECT 'NAV_DAILY' sync_name, ? provider_name FROM dual) src
                   ON (target.sync_name = src.sync_name AND target.provider_name = src.provider_name)
                WHEN MATCHED THEN UPDATE SET
                    target.status = 'RUNNING',
                    target.last_attempt_at = SYSTIMESTAMP,
                    target.last_error = NULL,
                    target.updated_at = SYSTIMESTAMP
                WHEN NOT MATCHED THEN INSERT (
                    sync_name, provider_name, status, last_attempt_at, record_count, updated_at
                ) VALUES (
                    src.sync_name, src.provider_name, 'RUNNING', SYSTIMESTAMP, 0, SYSTIMESTAMP
                )
                """,
                AmfiNavProvider.PROVIDER_NAME
        );
        return key.longValue();
    }

    private void validateRefreshRequest(String scope, Long schemeId) {
        if (!List.of("ALL", "SCHEME", "WATCHLIST").contains(scope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported refresh scope.");
        }
        if ("SCHEME".equals(scope) != (schemeId != null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "schemeId is required only for SCHEME refreshes."
            );
        }
    }

    private void finishFailedRefresh(long refreshId, String message) {
        jdbcTemplate.update(
                """
                UPDATE nav_refresh_log
                   SET refresh_completed_at = SYSTIMESTAMP, result = 'FAILURE',
                       error_message = ?
                 WHERE refresh_id = ?
                """,
                truncate(message, 2000), refreshId
        );
        jdbcTemplate.update(
                """
                UPDATE data_sync_status
                   SET status = 'FAILED', last_error = ?, updated_at = SYSTIMESTAMP
                 WHERE sync_name = 'NAV_DAILY' AND provider_name = ?
                """,
                truncate(message, 2000), AmfiNavProvider.PROVIDER_NAME
        );
    }

    private void logSourceFailure(NavProviderException exception) {
        jdbcTemplate.update(
                """
                INSERT INTO nav_data_source_log (
                    provider_name, operation_name, response_status, response_code,
                    error_message, fetched_at
                ) VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP)
                """,
                AmfiNavProvider.PROVIDER_NAME,
                "NAV_ALL_IMPORT",
                exception.getResponseCode() == null ? "ERROR" : String.valueOf(exception.getResponseCode()),
                exception.getResponseCode(),
                truncate(exception.getMessage(), 2000)
        );
    }

    private void logSourceResult(String responseStatus, Integer responseCode, int count, String message) {
        jdbcTemplate.update(
                """
                INSERT INTO nav_data_source_log (
                    provider_name, operation_name, response_status, response_code,
                    response_payload, error_message, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, SYSTIMESTAMP)
                """,
                AmfiNavProvider.PROVIDER_NAME, "NAV_ALL_IMPORT", responseStatus, responseCode,
                "records=" + count, truncate(message, 2000)
        );
    }

    private String sourceHash(long schemeId, NavRecord record) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (schemeId + "|" + record.navDate() + "|" + record.navValue() + "|" + AmfiNavProvider.PROVIDER_NAME)
                            .getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return truncate(message == null || message.isBlank() ? exception.getClass().getSimpleName() : message, 2000);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record Mapping(long schemeId, String externalCode) {
    }

    private record ImportRow(long schemeId, NavRecord record) {
    }
}
