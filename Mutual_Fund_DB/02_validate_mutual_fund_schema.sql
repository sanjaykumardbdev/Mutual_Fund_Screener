-- Run as MUTUAL_FUND_APP after 01_install_mutual_fund_schema.sql.
SET PAGESIZE 100
SET LINESIZE 220

PROMPT === Required application tables (21 expected) ===
SELECT table_name
FROM user_tables
WHERE table_name IN (
    'APP_USERS', 'APP_ROLES', 'USER_ROLES', 'APP_PERMISSIONS',
    'ROLE_PERMISSIONS', 'USER_PERMISSIONS', 'MUTUAL_FUND_HOUSES',
    'MUTUAL_FUND_SCHEMES', 'NAV_SCHEME_MAPPINGS',
    'MUTUAL_FUND_NAV_HISTORY', 'USER_FUND_WATCHLIST',
    'USER_INVESTMENTS', 'USER_SIP_DETAILS', 'USER_NAV_ALERTS',
    'USER_PREFERENCES', 'USER_LOGIN_AUDIT', 'USER_ACTIVITY_AUDIT',
    'NAV_REFRESH_LOG', 'NAV_DATA_SOURCE_LOG', 'USER_REPORT_HISTORY',
    'DATA_SYNC_STATUS'
)
ORDER BY table_name;

PROMPT === Table count (expected 21) ===
SELECT COUNT(*) AS table_count
FROM user_tables
WHERE table_name IN (
    'APP_USERS', 'APP_ROLES', 'USER_ROLES', 'APP_PERMISSIONS',
    'ROLE_PERMISSIONS', 'USER_PERMISSIONS', 'MUTUAL_FUND_HOUSES',
    'MUTUAL_FUND_SCHEMES', 'NAV_SCHEME_MAPPINGS',
    'MUTUAL_FUND_NAV_HISTORY', 'USER_FUND_WATCHLIST',
    'USER_INVESTMENTS', 'USER_SIP_DETAILS', 'USER_NAV_ALERTS',
    'USER_PREFERENCES', 'USER_LOGIN_AUDIT', 'USER_ACTIVITY_AUDIT',
    'NAV_REFRESH_LOG', 'NAV_DATA_SOURCE_LOG', 'USER_REPORT_HISTORY',
    'DATA_SYNC_STATUS'
);

PROMPT === Constraints (PK, FK, unique, and checks) ===
SELECT table_name, constraint_type, COUNT(*) AS constraint_count
FROM user_constraints
WHERE table_name IN (
    'APP_USERS', 'APP_ROLES', 'USER_ROLES', 'APP_PERMISSIONS',
    'ROLE_PERMISSIONS', 'USER_PERMISSIONS', 'MUTUAL_FUND_HOUSES',
    'MUTUAL_FUND_SCHEMES', 'NAV_SCHEME_MAPPINGS',
    'MUTUAL_FUND_NAV_HISTORY', 'USER_FUND_WATCHLIST',
    'USER_INVESTMENTS', 'USER_SIP_DETAILS', 'USER_NAV_ALERTS',
    'USER_PREFERENCES', 'USER_LOGIN_AUDIT', 'USER_ACTIVITY_AUDIT',
    'NAV_REFRESH_LOG', 'NAV_DATA_SOURCE_LOG', 'USER_REPORT_HISTORY',
    'DATA_SYNC_STATUS'
)
GROUP BY table_name, constraint_type
ORDER BY table_name, constraint_type;

PROMPT === Bootstrap roles and permission counts (ADMIN and USER expected) ===
SELECT r.role_name, COUNT(rp.permission_id) AS permission_count
FROM app_roles r
LEFT JOIN role_permissions rp ON rp.role_id = r.role_id
GROUP BY r.role_name
ORDER BY r.role_name;

PROMPT === Permission override policy (four standard user permissions expected enabled) ===
SELECT permission_code, user_overridable
FROM app_permissions
ORDER BY permission_code;

PROMPT === Sample reference data (one demo house, scheme, and two NAV rows expected) ===
SELECT h.house_code, COUNT(DISTINCT s.scheme_id) AS scheme_count,
       COUNT(n.nav_id) AS nav_row_count
FROM mutual_fund_houses h
LEFT JOIN mutual_fund_schemes s ON s.house_id = h.house_id
LEFT JOIN mutual_fund_nav_history n ON n.scheme_id = s.scheme_id
WHERE h.house_code = 'DEMO-AMC'
GROUP BY h.house_code;

PROMPT === Duplicate NAV scheme/date keys (no rows expected) ===
SELECT scheme_id, nav_date, COUNT(*) AS duplicate_count
FROM mutual_fund_nav_history
GROUP BY scheme_id, nav_date
HAVING COUNT(*) > 1;

PROMPT === Duplicate watchlist user/scheme keys (no rows expected) ===
SELECT user_id, scheme_id, COUNT(*) AS duplicate_count
FROM user_fund_watchlist
GROUP BY user_id, scheme_id
HAVING COUNT(*) > 1;

PROMPT === Invalid/orphaned user-role and scheme-NAV relationships (no rows expected) ===
SELECT 'USER_ROLE' AS issue_type, COUNT(*) AS issue_count
FROM user_roles ur
LEFT JOIN app_users u ON u.user_id = ur.user_id
LEFT JOIN app_roles r ON r.role_id = ur.role_id
WHERE u.user_id IS NULL OR r.role_id IS NULL
UNION ALL
SELECT 'SCHEME_NAV', COUNT(*)
FROM mutual_fund_nav_history n
LEFT JOIN mutual_fund_schemes s ON s.scheme_id = n.scheme_id
WHERE s.scheme_id IS NULL;

PROMPT === Expected: 21 tables, ADMIN and USER roles, 8 permissions, one fictional demo scheme with two NAV rows. ===
