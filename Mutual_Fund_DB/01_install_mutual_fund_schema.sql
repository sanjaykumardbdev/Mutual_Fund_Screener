-- Run as MUTUAL_FUND_APP in a newly created application schema.
-- Oracle DDL commits implicitly. If this script fails, fix the reported
-- statement and recreate the empty schema before rerunning it.

WHENEVER SQLERROR EXIT SQL.SQLCODE
SET DEFINE OFF

CREATE TABLE app_users (
    user_id             NUMBER GENERATED ALWAYS AS IDENTITY,
    username            VARCHAR2(60) NOT NULL,
    email               VARCHAR2(254) NOT NULL,
    password_hash       VARCHAR2(255) NOT NULL,
    first_name          VARCHAR2(100),
    last_name           VARCHAR2(100),
    is_enabled          NUMBER(1) DEFAULT 1 NOT NULL,
    is_account_locked   NUMBER(1) DEFAULT 0 NOT NULL,
    created_at          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    last_login_at       TIMESTAMP(6),
    CONSTRAINT pk_app_users PRIMARY KEY (user_id),
    CONSTRAINT uq_app_users_username UNIQUE (username),
    CONSTRAINT uq_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_enabled CHECK (is_enabled IN (0, 1)),
    CONSTRAINT ck_app_users_locked CHECK (is_account_locked IN (0, 1))
);

CREATE TABLE app_roles (
    role_id       NUMBER GENERATED ALWAYS AS IDENTITY,
    role_name     VARCHAR2(80) NOT NULL,
    description   VARCHAR2(255),
    created_at    TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_app_roles PRIMARY KEY (role_id),
    CONSTRAINT uq_app_roles_name UNIQUE (role_name)
);

CREATE TABLE user_roles (
    user_id      NUMBER NOT NULL,
    role_id      NUMBER NOT NULL,
    assigned_at  TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES app_users (user_id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES app_roles (role_id)
);

CREATE TABLE app_permissions (
    permission_id    NUMBER GENERATED ALWAYS AS IDENTITY,
    permission_code  VARCHAR2(100) NOT NULL,
    permission_name  VARCHAR2(150) NOT NULL,
    description      VARCHAR2(500),
    user_overridable NUMBER(1) DEFAULT 0 NOT NULL,
    created_at       TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_app_permissions PRIMARY KEY (permission_id),
    CONSTRAINT uq_app_permissions_code UNIQUE (permission_code),
    CONSTRAINT ck_app_permissions_overridable CHECK (user_overridable IN (0, 1))
);

CREATE TABLE role_permissions (
    role_id        NUMBER NOT NULL,
    permission_id  NUMBER NOT NULL,
    granted_at     TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES app_roles (role_id),
    CONSTRAINT fk_role_permissions_perm FOREIGN KEY (permission_id) REFERENCES app_permissions (permission_id)
);

CREATE TABLE user_permissions (
    user_id          NUMBER NOT NULL,
    permission_id    NUMBER NOT NULL,
    permission_value VARCHAR2(10) NOT NULL,
    created_at       TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at       TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_user_permissions PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES app_users (user_id),
    CONSTRAINT fk_user_permissions_perm FOREIGN KEY (permission_id) REFERENCES app_permissions (permission_id),
    CONSTRAINT ck_user_permissions_value CHECK (permission_value IN ('ALLOW', 'DENY'))
);

CREATE TABLE mutual_fund_houses (
    house_id      NUMBER GENERATED ALWAYS AS IDENTITY,
    house_code    VARCHAR2(40) NOT NULL,
    house_name    VARCHAR2(200) NOT NULL,
    country_code  CHAR(2) DEFAULT 'IN' NOT NULL,
    website_url   VARCHAR2(500),
    is_active     NUMBER(1) DEFAULT 1 NOT NULL,
    created_at    TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_mutual_fund_houses PRIMARY KEY (house_id),
    CONSTRAINT uq_mutual_fund_houses_code UNIQUE (house_code),
    CONSTRAINT ck_mutual_fund_houses_active CHECK (is_active IN (0, 1))
);

CREATE TABLE mutual_fund_schemes (
    scheme_id         NUMBER GENERATED ALWAYS AS IDENTITY,
    house_id          NUMBER NOT NULL,
    scheme_code       VARCHAR2(80) NOT NULL,
    scheme_name       VARCHAR2(300) NOT NULL,
    category          VARCHAR2(50) NOT NULL,
    plan_type         VARCHAR2(20) NOT NULL,
    option_type       VARCHAR2(20) NOT NULL,
    benchmark         VARCHAR2(200),
    isin              VARCHAR2(20),
    is_active         NUMBER(1) DEFAULT 1 NOT NULL,
    created_at        TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at        TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_mutual_fund_schemes PRIMARY KEY (scheme_id),
    CONSTRAINT uq_mutual_fund_schemes_code UNIQUE (scheme_code),
    CONSTRAINT fk_mutual_fund_schemes_house FOREIGN KEY (house_id) REFERENCES mutual_fund_houses (house_id),
    CONSTRAINT ck_mutual_fund_schemes_category CHECK (
        category IN ('EQUITY', 'DEBT', 'HYBRID', 'INDEX', 'ELSS', 'LIQUID', 'OTHER')
    ),
    CONSTRAINT ck_mutual_fund_schemes_plan CHECK (plan_type IN ('DIRECT', 'REGULAR')),
    CONSTRAINT ck_mutual_fund_schemes_option CHECK (option_type IN ('GROWTH', 'IDCW')),
    CONSTRAINT ck_mutual_fund_schemes_active CHECK (is_active IN (0, 1))
);

-- A provider's identifier is kept separately from the internal scheme key.
CREATE TABLE nav_scheme_mappings (
    mapping_id          NUMBER GENERATED ALWAYS AS IDENTITY,
    scheme_id           NUMBER NOT NULL,
    provider_name       VARCHAR2(80) NOT NULL,
    external_scheme_code VARCHAR2(100) NOT NULL,
    is_active           NUMBER(1) DEFAULT 1 NOT NULL,
    created_at          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_nav_scheme_mappings PRIMARY KEY (mapping_id),
    CONSTRAINT uq_nav_mapping_provider_code UNIQUE (provider_name, external_scheme_code),
    CONSTRAINT uq_nav_mapping_scheme_provider UNIQUE (scheme_id, provider_name),
    CONSTRAINT fk_nav_mapping_scheme FOREIGN KEY (scheme_id) REFERENCES mutual_fund_schemes (scheme_id),
    CONSTRAINT ck_nav_mapping_active CHECK (is_active IN (0, 1))
);

CREATE TABLE mutual_fund_nav_history (
    nav_id              NUMBER GENERATED ALWAYS AS IDENTITY,
    scheme_id           NUMBER NOT NULL,
    nav_date            DATE NOT NULL,
    nav_value           NUMBER(18, 6) NOT NULL,
    provider_name       VARCHAR2(80) NOT NULL,
    published_at        TIMESTAMP(6),
    retrieved_at        TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    source_record_hash  VARCHAR2(128),
    CONSTRAINT pk_mutual_fund_nav_history PRIMARY KEY (nav_id),
    CONSTRAINT uq_mutual_fund_nav_scheme_date UNIQUE (scheme_id, nav_date),
    CONSTRAINT fk_mutual_fund_nav_scheme FOREIGN KEY (scheme_id) REFERENCES mutual_fund_schemes (scheme_id),
    CONSTRAINT ck_mutual_fund_nav_date CHECK (nav_date = TRUNC(nav_date)),
    CONSTRAINT ck_mutual_fund_nav_value CHECK (nav_value >= 0)
);

CREATE TABLE user_fund_watchlist (
    watchlist_id  NUMBER GENERATED ALWAYS AS IDENTITY,
    user_id       NUMBER NOT NULL,
    scheme_id     NUMBER NOT NULL,
    added_at      TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    is_active     NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_user_fund_watchlist PRIMARY KEY (watchlist_id),
    CONSTRAINT uq_watchlist_user_scheme UNIQUE (user_id, scheme_id),
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES app_users (user_id),
    CONSTRAINT fk_watchlist_scheme FOREIGN KEY (scheme_id) REFERENCES mutual_fund_schemes (scheme_id),
    CONSTRAINT ck_watchlist_active CHECK (is_active IN (0, 1))
);

CREATE TABLE user_sip_details (
    sip_id                    NUMBER GENERATED ALWAYS AS IDENTITY,
    user_id                   NUMBER NOT NULL,
    scheme_id                 NUMBER NOT NULL,
    sip_amount                NUMBER(18, 2) NOT NULL,
    sip_frequency             VARCHAR2(20) NOT NULL,
    start_date                DATE NOT NULL,
    end_date                  DATE,
    next_installment_date     DATE,
    sip_status                VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at                TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at                TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_user_sip_details PRIMARY KEY (sip_id),
    CONSTRAINT uq_user_sip_owner_scheme UNIQUE (sip_id, user_id, scheme_id),
    CONSTRAINT fk_user_sip_user FOREIGN KEY (user_id) REFERENCES app_users (user_id),
    CONSTRAINT fk_user_sip_scheme FOREIGN KEY (scheme_id) REFERENCES mutual_fund_schemes (scheme_id),
    CONSTRAINT ck_user_sip_amount CHECK (sip_amount > 0),
    CONSTRAINT ck_user_sip_frequency CHECK (sip_frequency IN ('WEEKLY', 'MONTHLY', 'QUARTERLY')),
    CONSTRAINT ck_user_sip_status CHECK (sip_status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_user_sip_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE TABLE user_investments (
    investment_id       NUMBER GENERATED ALWAYS AS IDENTITY,
    user_id             NUMBER NOT NULL,
    scheme_id           NUMBER NOT NULL,
    sip_id              NUMBER,
    transaction_type    VARCHAR2(20) NOT NULL,
    transaction_date    DATE NOT NULL,
    transaction_nav     NUMBER(18, 6),
    transaction_amount  NUMBER(18, 2) NOT NULL,
    units               NUMBER(18, 6) NOT NULL,
    remarks              VARCHAR2(500),
    created_at          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_user_investments PRIMARY KEY (investment_id),
    CONSTRAINT fk_user_investments_user FOREIGN KEY (user_id) REFERENCES app_users (user_id),
    CONSTRAINT fk_user_investments_scheme FOREIGN KEY (scheme_id) REFERENCES mutual_fund_schemes (scheme_id),
    CONSTRAINT fk_investment_sip_owner_scheme
        FOREIGN KEY (sip_id, user_id, scheme_id)
        REFERENCES user_sip_details (sip_id, user_id, scheme_id),
    CONSTRAINT ck_user_investments_type CHECK (transaction_type IN ('LUMPSUM', 'SIP', 'REDEMPTION', 'IDCW')),
    CONSTRAINT ck_user_investments_amount CHECK (transaction_amount > 0),
    CONSTRAINT ck_user_investments_units CHECK (
        (transaction_type = 'IDCW' AND units = 0)
        OR (transaction_type <> 'IDCW' AND units > 0)
    ),
    CONSTRAINT ck_user_investments_nav CHECK (
        transaction_nav IS NULL OR transaction_nav > 0
    ),
    CONSTRAINT ck_user_investments_idcw_nav CHECK (
        transaction_type = 'IDCW' OR transaction_nav IS NOT NULL
    ),
    CONSTRAINT ck_user_investments_sip_link CHECK (
        transaction_type <> 'SIP' OR sip_id IS NOT NULL
    )
);

CREATE TABLE user_nav_alerts (
    alert_id             NUMBER GENERATED ALWAYS AS IDENTITY,
    user_id              NUMBER NOT NULL,
    scheme_id            NUMBER NOT NULL,
    alert_type           VARCHAR2(20) NOT NULL,
    threshold_value      NUMBER(18, 6) NOT NULL,
    is_active            NUMBER(1) DEFAULT 1 NOT NULL,
    created_at           TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at           TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    last_triggered_at    TIMESTAMP(6),
    CONSTRAINT pk_user_nav_alerts PRIMARY KEY (alert_id),
    CONSTRAINT fk_user_nav_alerts_user FOREIGN KEY (user_id) REFERENCES app_users (user_id),
    CONSTRAINT fk_user_nav_alerts_scheme FOREIGN KEY (scheme_id) REFERENCES mutual_fund_schemes (scheme_id),
    CONSTRAINT ck_user_nav_alerts_type CHECK (alert_type IN ('NAV_ABOVE', 'NAV_BELOW', 'PERCENT_UP', 'PERCENT_DOWN')),
    CONSTRAINT ck_user_nav_alerts_threshold CHECK (threshold_value > 0),
    CONSTRAINT ck_user_nav_alerts_active CHECK (is_active IN (0, 1))
);

CREATE TABLE user_preferences (
    user_id                     NUMBER NOT NULL,
    default_theme               VARCHAR2(20) DEFAULT 'LIGHT' NOT NULL,
    refresh_frequency_minutes   NUMBER(6) DEFAULT 60 NOT NULL,
    offline_cache_days          NUMBER(3) DEFAULT 30 NOT NULL,
    created_at                  TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_user_preferences PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES app_users (user_id),
    CONSTRAINT ck_user_preferences_theme CHECK (default_theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    CONSTRAINT ck_user_preferences_refresh CHECK (refresh_frequency_minutes > 0),
    CONSTRAINT ck_user_preferences_cache CHECK (offline_cache_days > 0)
);

CREATE TABLE user_login_audit (
    login_audit_id    NUMBER GENERATED ALWAYS AS IDENTITY,
    user_id           NUMBER,
    username          VARCHAR2(60) NOT NULL,
    event_type        VARCHAR2(20) NOT NULL,
    event_at          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    ip_address        VARCHAR2(64),
    user_agent        VARCHAR2(1000),
    failure_reason    VARCHAR2(500),
    session_id        VARCHAR2(128),
    CONSTRAINT pk_user_login_audit PRIMARY KEY (login_audit_id),
    CONSTRAINT fk_login_audit_user FOREIGN KEY (user_id) REFERENCES app_users (user_id),
    CONSTRAINT ck_login_audit_event CHECK (event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT'))
);

CREATE TABLE user_activity_audit (
    activity_id       NUMBER GENERATED ALWAYS AS IDENTITY,
    user_id           NUMBER,
    activity_type     VARCHAR2(100) NOT NULL,
    activity_details  CLOB,
    created_at        TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_user_activity_audit PRIMARY KEY (activity_id),
    CONSTRAINT fk_activity_audit_user FOREIGN KEY (user_id) REFERENCES app_users (user_id)
);

CREATE TABLE nav_refresh_log (
    refresh_id            NUMBER GENERATED ALWAYS AS IDENTITY,
    scheme_id             NUMBER,
    requested_by_user_id  NUMBER,
    refresh_scope         VARCHAR2(20) NOT NULL,
    refresh_started_at    TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    refresh_completed_at  TIMESTAMP(6),
    result                VARCHAR2(20) DEFAULT 'RUNNING' NOT NULL,
    records_processed     NUMBER(10) DEFAULT 0 NOT NULL,
    error_message         VARCHAR2(2000),
    provider_name         VARCHAR2(80),
    CONSTRAINT pk_nav_refresh_log PRIMARY KEY (refresh_id),
    CONSTRAINT fk_nav_refresh_scheme FOREIGN KEY (scheme_id) REFERENCES mutual_fund_schemes (scheme_id),
    CONSTRAINT fk_nav_refresh_user FOREIGN KEY (requested_by_user_id) REFERENCES app_users (user_id),
    CONSTRAINT ck_nav_refresh_scope CHECK (refresh_scope IN ('SCHEME', 'WATCHLIST', 'ALL')),
    CONSTRAINT ck_nav_refresh_result CHECK (result IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILURE')),
    CONSTRAINT ck_nav_refresh_records CHECK (records_processed >= 0),
    CONSTRAINT ck_nav_refresh_dates CHECK (
        refresh_completed_at IS NULL OR refresh_completed_at >= refresh_started_at
    ),
    CONSTRAINT ck_nav_refresh_scheme_scope CHECK (
        (refresh_scope = 'SCHEME' AND scheme_id IS NOT NULL)
        OR (refresh_scope <> 'SCHEME' AND scheme_id IS NULL)
    )
);

CREATE TABLE nav_data_source_log (
    source_log_id       NUMBER GENERATED ALWAYS AS IDENTITY,
    provider_name       VARCHAR2(80) NOT NULL,
    operation_name      VARCHAR2(100) NOT NULL,
    external_scheme_code VARCHAR2(100),
    request_payload     CLOB,
    response_status     VARCHAR2(30),
    response_code       NUMBER(3),
    response_payload    CLOB,
    error_message       VARCHAR2(2000),
    fetched_at          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_nav_data_source_log PRIMARY KEY (source_log_id)
);

CREATE TABLE user_report_history (
    report_id       NUMBER GENERATED ALWAYS AS IDENTITY,
    user_id         NUMBER NOT NULL,
    report_type     VARCHAR2(80) NOT NULL,
    file_name       VARCHAR2(255),
    generated_at    TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    status          VARCHAR2(20) NOT NULL,
    storage_key     VARCHAR2(500),
    CONSTRAINT pk_user_report_history PRIMARY KEY (report_id),
    CONSTRAINT fk_user_report_history_user FOREIGN KEY (user_id) REFERENCES app_users (user_id),
    CONSTRAINT ck_user_report_history_status CHECK (status IN ('SUCCESS', 'FAILED', 'PENDING'))
);

CREATE TABLE data_sync_status (
    sync_id           NUMBER GENERATED ALWAYS AS IDENTITY,
    sync_name         VARCHAR2(100) NOT NULL,
    provider_name     VARCHAR2(80) NOT NULL,
    status            VARCHAR2(20) DEFAULT 'IDLE' NOT NULL,
    last_attempt_at   TIMESTAMP(6),
    last_success_at   TIMESTAMP(6),
    last_error        VARCHAR2(2000),
    record_count      NUMBER(10) DEFAULT 0 NOT NULL,
    updated_at        TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_data_sync_status PRIMARY KEY (sync_id),
    CONSTRAINT uq_data_sync_name_provider UNIQUE (sync_name, provider_name),
    CONSTRAINT ck_data_sync_status CHECK (status IN ('IDLE', 'RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_data_sync_record_count CHECK (record_count >= 0)
);

-- Index foreign keys and common list/history access paths.
CREATE UNIQUE INDEX ux_app_users_username_ci ON app_users (UPPER(username));
CREATE UNIQUE INDEX ux_app_users_email_ci ON app_users (UPPER(email));
CREATE INDEX ix_user_roles_role ON user_roles (role_id);
CREATE INDEX ix_role_permissions_perm ON role_permissions (permission_id);
CREATE INDEX ix_user_permissions_perm ON user_permissions (permission_id);
CREATE INDEX ix_schemes_house_active ON mutual_fund_schemes (house_id, is_active, category);
CREATE INDEX ix_nav_mapping_scheme ON nav_scheme_mappings (scheme_id, is_active);
CREATE INDEX ix_watchlist_scheme ON user_fund_watchlist (scheme_id);
CREATE INDEX ix_user_sip_user_status ON user_sip_details (user_id, sip_status);
CREATE INDEX ix_user_sip_scheme ON user_sip_details (scheme_id);
CREATE INDEX ix_investments_user_date ON user_investments (user_id, transaction_date);
CREATE INDEX ix_investments_scheme ON user_investments (scheme_id);
CREATE INDEX ix_investments_sip_owner_scheme ON user_investments (sip_id, user_id, scheme_id);
CREATE INDEX ix_alerts_user_active ON user_nav_alerts (user_id, is_active);
CREATE INDEX ix_alerts_scheme ON user_nav_alerts (scheme_id);
CREATE INDEX ix_login_audit_user_time ON user_login_audit (user_id, event_at);
CREATE INDEX ix_login_audit_name_time ON user_login_audit (username, event_at);
CREATE INDEX ix_activity_audit_user_time ON user_activity_audit (user_id, created_at);
CREATE INDEX ix_nav_refresh_started ON nav_refresh_log (refresh_started_at);
CREATE INDEX ix_nav_refresh_scheme_time ON nav_refresh_log (scheme_id, refresh_started_at);
CREATE INDEX ix_nav_refresh_user_time ON nav_refresh_log (requested_by_user_id, refresh_started_at);
CREATE INDEX ix_nav_source_log_time ON nav_data_source_log (provider_name, fetched_at);
CREATE INDEX ix_report_history_user_time ON user_report_history (user_id, generated_at);

-- Bootstrap roles are safe to seed here; user accounts are created by the
-- application's registration/admin workflow in the authentication phase.
INSERT INTO app_roles (role_name, description)
SELECT 'ADMIN', 'Application administrator'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_roles WHERE role_name = 'ADMIN');

INSERT INTO app_roles (role_name, description)
SELECT 'USER', 'Standard application user'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_roles WHERE role_name = 'USER');

INSERT INTO app_permissions (permission_code, permission_name, description)
SELECT 'NAV_VIEW', 'View NAV data', 'View fund scheme and NAV information'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_permissions WHERE permission_code = 'NAV_VIEW');

INSERT INTO app_permissions (permission_code, permission_name, description)
SELECT 'WATCHLIST_MANAGE', 'Manage own watchlist', 'Add and remove own watchlist entries'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_permissions WHERE permission_code = 'WATCHLIST_MANAGE');

INSERT INTO app_permissions (permission_code, permission_name, description)
SELECT 'INVESTMENT_MANAGE', 'Manage own investments', 'Create and manage own investment records'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_permissions WHERE permission_code = 'INVESTMENT_MANAGE');

INSERT INTO app_permissions (permission_code, permission_name, description)
SELECT 'REPORT_EXPORT', 'Export own reports', 'Generate and export own reports'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_permissions WHERE permission_code = 'REPORT_EXPORT');

INSERT INTO app_permissions (permission_code, permission_name, description)
SELECT 'USER_MANAGE', 'Manage users', 'Create, enable, disable, and assign roles to users'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_permissions WHERE permission_code = 'USER_MANAGE');

INSERT INTO app_permissions (permission_code, permission_name, description)
SELECT 'PERMISSION_MANAGE', 'Manage permissions', 'Manage roles and user permission overrides'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_permissions WHERE permission_code = 'PERMISSION_MANAGE');

INSERT INTO app_permissions (permission_code, permission_name, description)
SELECT 'NAV_REFRESH', 'Refresh NAV data', 'Request synchronization of NAV data'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_permissions WHERE permission_code = 'NAV_REFRESH');

INSERT INTO app_permissions (permission_code, permission_name, description)
SELECT 'ADMIN_DASHBOARD', 'View administration dashboard', 'View administrative tools and audit information'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_permissions WHERE permission_code = 'ADMIN_DASHBOARD');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM app_roles r
CROSS JOIN app_permissions p
WHERE r.role_name = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
  );

UPDATE app_permissions
SET user_overridable = 1
WHERE permission_code IN ('NAV_VIEW', 'WATCHLIST_MANAGE', 'INVESTMENT_MANAGE', 'REPORT_EXPORT');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM app_roles r
JOIN app_permissions p
  ON p.permission_code IN ('NAV_VIEW', 'WATCHLIST_MANAGE', 'INVESTMENT_MANAGE', 'REPORT_EXPORT')
WHERE r.role_name = 'USER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
  );

-- Clearly fictional fixture data; replace it with provider-verified records
-- during the NAV integration phase.
INSERT INTO mutual_fund_houses (house_code, house_name, country_code, is_active)
VALUES ('DEMO-AMC', 'Demonstration Mutual Fund House', 'IN', 1);

INSERT INTO mutual_fund_schemes (
    house_id, scheme_code, scheme_name, category, plan_type, option_type, is_active
)
SELECT house_id, 'DEMO-INDEX-DIRECT-GROWTH',
       'Demonstration Nifty Index Fund - Direct Growth',
       'INDEX', 'DIRECT', 'GROWTH', 1
FROM mutual_fund_houses
WHERE house_code = 'DEMO-AMC';

INSERT INTO nav_scheme_mappings (scheme_id, provider_name, external_scheme_code)
SELECT scheme_id, 'SAMPLE', 'DEMO-100001'
FROM mutual_fund_schemes
WHERE scheme_code = 'DEMO-INDEX-DIRECT-GROWTH';

INSERT INTO mutual_fund_nav_history (
    scheme_id, nav_date, nav_value, provider_name, published_at
)
SELECT scheme_id, DATE '2026-01-02', 100.000000, 'SAMPLE',
       TIMESTAMP '2026-01-02 18:00:00'
FROM mutual_fund_schemes
WHERE scheme_code = 'DEMO-INDEX-DIRECT-GROWTH';

INSERT INTO mutual_fund_nav_history (
    scheme_id, nav_date, nav_value, provider_name, published_at
)
SELECT scheme_id, DATE '2026-01-05', 101.250000, 'SAMPLE',
       TIMESTAMP '2026-01-05 18:00:00'
FROM mutual_fund_schemes
WHERE scheme_code = 'DEMO-INDEX-DIRECT-GROWTH';

COMMIT;

PROMPT Schema installation and fictional reference data completed.
