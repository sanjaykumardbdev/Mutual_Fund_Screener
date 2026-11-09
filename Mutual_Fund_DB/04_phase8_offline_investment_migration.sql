-- Run once as MUTUAL_FUND_APP on an existing installation before Phase 8.
-- Adds a per-user idempotency key used when queued offline transactions sync.
WHENEVER SQLERROR EXIT SQL.SQLCODE

ALTER TABLE user_investments ADD (client_request_id VARCHAR2(36));

CREATE UNIQUE INDEX ux_investments_client_request
    ON user_investments (user_id, client_request_id);

COMMIT;

PROMPT Phase 8 offline investment migration completed.
