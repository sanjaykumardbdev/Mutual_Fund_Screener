-- Run once as MUTUAL_FUND_APP against the same PDB as the application.
-- Adds policy metadata used to restrict which permissions an admin may
-- explicitly ALLOW for an individual user. Default is deny/no override.
WHENEVER SQLERROR EXIT SQL.SQLCODE

ALTER TABLE app_permissions
    ADD (user_overridable NUMBER(1) DEFAULT 0 NOT NULL);

ALTER TABLE app_permissions
    ADD CONSTRAINT ck_app_permissions_overridable
    CHECK (user_overridable IN (0, 1));

CREATE UNIQUE INDEX ux_app_users_username_ci ON app_users (UPPER(username));
CREATE UNIQUE INDEX ux_app_users_email_ci ON app_users (UPPER(email));

UPDATE app_permissions
SET user_overridable = 1
WHERE permission_code IN (
    'NAV_VIEW',
    'WATCHLIST_MANAGE',
    'INVESTMENT_MANAGE',
    'REPORT_EXPORT'
);

COMMIT;

PROMPT Phase 3 permission-override policy installed.
