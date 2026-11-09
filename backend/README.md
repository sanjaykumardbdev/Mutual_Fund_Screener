# Spring Boot API - Phases 2 through 8

This Java 17 / Spring Boot 3 API connects to the Oracle 19c schema. It provides
health, authenticated fund catalog/NAV reads, session authentication,
authorization, admin, watchlist, and investment APIs. The React fund explorer
is documented in the sibling `frontend` folder. It imports mapped daily NAV
data from AMFI, retains dated history, and provides NAV dashboard/detail,
offline synchronization preferences, watchlist/investment, SIP, and
user-scoped portfolio analytics APIs.

## Requirements

- Oracle 19c running with the target PDB open (`ORCLPDB` by default).
- The Phase 1 schema and 21 tables installed in that PDB.
- Java 17 and Maven 3.9+ on `PATH`.
- A database password for the `MUTUAL_FUND_APP` schema supplied as an
  environment variable; credentials are not committed in this project.
- The Phase 3 security migration in
  `Mutual_Fund_DB/03_phase3_security_migration.sql` applied to existing
  databases. Fresh installs already include the new permission policy column.
- Existing databases must also apply
  `Mutual_Fund_DB/04_phase8_offline_investment_migration.sql` once before
  recording Phase 8 transactions. Fresh installs already contain its
  idempotency column/index.

## Configure and run (Windows PowerShell)

Start Oracle 19c and verify that the PDB is open. In a new PowerShell window,
apply required one-time migrations first, then start the backend:

1. Connect as `MUTUAL_FUND_APP` to the same PDB in SQL Developer.
2. Open and run each required migration once with **Run Script (F5)**:
   `Mutual_Fund_DB\03_phase3_security_migration.sql` (older databases) and
   `Mutual_Fund_DB\04_phase8_offline_investment_migration.sql` (existing
   databases). Do not rerun a migration after it succeeds.
3. If this is the first application user, set the bootstrap username/email
   in PowerShell before starting the API:

```powershell
$env:BOOTSTRAP_ADMIN_USERNAME = "admin"
$env:BOOTSTRAP_ADMIN_EMAIL = "your-admin-email@example.com"
```

Then run the local launcher from the backend folder:

```powershell
cd C:\Users\Sanjay\OneDrive\Documents\PLSQL-Programs\01_plsql_engine\Mutual_Fund_Screener\backend
.\run-local.ps1
```

It securely prompts for the Oracle password. When bootstrap username and
email are set, it also prompts without echoing the initial admin password;
the application creates an ADMIN user only if `APP_USERS` is empty. The
password is hashed with BCrypt, and the launcher clears the database password
and any bootstrap password it prompted for when Maven exits. Once the first admin is created,
clear the optional username/email values:

```powershell
Remove-Item Env:BOOTSTRAP_ADMIN_USERNAME, Env:BOOTSTRAP_ADMIN_EMAIL -ErrorAction SilentlyContinue
```

If PowerShell blocks the launcher due to execution policy, run it for this
process only:

```powershell
powershell -ExecutionPolicy Bypass -File .\run-local.ps1
```

The backend binds to `127.0.0.1:8080` by default. Keep this terminal open
while using the API. Stop a running backend with `Ctrl+C` before restarting
it. Do not put passwords in source control or committed properties files.

Optional environment settings:

```powershell
$env:SERVER_PORT = "8080"
$env:SERVER_ADDRESS = "127.0.0.1"
$env:APP_CORS_ALLOWED_ORIGIN = "http://localhost:5173"
```

If Oracle uses another service name or port, set `DB_URL` to that listener
service. For example:

```text
jdbc:oracle:thin:@//localhost:1521/ORCLPDB
```

On startup, Hibernate validates the existing mapped columns but does not
create, update, or delete database objects. A startup failure due to missing
or mismatched schema objects is intentional and should be resolved against
the Phase 1 scripts rather than bypassing schema validation.

The Phase 1 `MUTUAL_FUND_HOUSES.COUNTRY_CODE` column is Oracle `CHAR(2)`.
The Java mapping explicitly uses JDBC `CHAR` so Hibernate validation matches
that existing schema; do not change the database column to work around an
entity mapping mismatch.

## API endpoints

### CSRF and authentication

Session authentication is cookie-based. Before a login or other state-changing
request, fetch a CSRF token and send the returned token in the named header.
Keep the session cookie and CSRF cookie in the same browser/API-client session.

PowerShell example using a cookie-preserving web session:

```powershell
$web = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod http://localhost:8080/api/auth/csrf -WebSession $web
$headers = @{}
$headers[$csrf.headerName] = $csrf.token
$body = @{ username = "admin"; password = "your-password" } | ConvertTo-Json
Invoke-RestMethod -Method Post http://localhost:8080/api/auth/login `
  -WebSession $web -Headers $headers -ContentType "application/json" -Body $body
Invoke-RestMethod http://localhost:8080/api/auth/me -WebSession $web
```

Keep the same `$web` session and CSRF header for POST/PUT/PATCH/DELETE calls.
After login, fetch `/api/auth/csrf` again to obtain the rotated CSRF token.

```text
GET  /api/auth/csrf
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/password
POST /api/auth/logout
POST /api/auth/register       (404 unless APP_REGISTRATION_ENABLED=true)
```

Login JSON:

```json
{"username":"admin","password":"your-password"}
```

Password change JSON:

```json
{"currentPassword":"current-password","newPassword":"new-password-at-least-12-characters"}
```

Passwords must be 12-72 characters and no more than 72 UTF-8 bytes (BCrypt's
input limit). Registration is disabled by default.
If explicitly enabled with `APP_REGISTRATION_ENABLED=true`, new public
registrations receive only the USER role. Do not enable public registration
unless the local network and deployment are appropriately controlled.

Login successes, failures, and logout events are written to
`USER_LOGIN_AUDIT`. Failure responses do not reveal whether the username
exists. Existing sessions are blocked on private API calls while the account
is disabled or locked. Password changes invalidate existing sessions, so the
user must sign in again.

### User-private APIs

The user ID is always read from the authenticated session; these endpoints
accept no user ID from the caller:

```text
GET    /api/me/watchlist
POST   /api/me/watchlist/{schemeId}
DELETE /api/me/watchlist/{schemeId}
GET    /api/me/investments
POST   /api/me/investments
GET    /api/me/portfolio
GET    /api/me/sips
POST   /api/me/sips
PATCH  /api/me/sips/{sipId}/status
GET    /api/me/preferences
PUT    /api/me/preferences
```

Watchlist operations reactivate or soft-remove the caller's unique
user/scheme row. Investment transactions are created and listed only for the
authenticated user. SIP transactions must reference a SIP belonging to that
same user and scheme. The records are not editable/deletable by these APIs.

Investment transaction POST bodies accept an optional UUID `clientRequestId`.
The browser supplies it for offline queue replay; a repeated key from the same
user and identical transaction is idempotent. The additive Phase 8 migration
is required on databases installed before this feature.

Portfolio analytics group the caller's investment ledger by scheme. Units held
equal purchase/SIP units less redemption units; current value uses the latest
stored NAV. Absolute gain/loss is current value plus redemption proceeds and
IDCW payouts less recorded contributions. Returns are not annualized and do
not include unrecorded cash flows, taxes, or fees. This is informational
tracking, not tax or investment advice.

SIP plan APIs manage the user's schedule and status only. They do not place
orders, move money, or automatically generate installment transactions;
record each installment explicitly through the investment transaction API.

### Admin APIs

All admin endpoints require the corresponding live permission; ADMIN role
grants are seeded in Phase 1. User and permission changes are written to
`USER_ACTIVITY_AUDIT`.

```text
GET   /api/admin/users
GET   /api/admin/login-audit
POST  /api/admin/users
PATCH /api/admin/users/{userId}/enabled
PUT   /api/admin/users/{userId}/roles
GET   /api/admin/roles
PUT   /api/admin/roles/{roleName}/permissions
GET   /api/admin/permissions
PUT   /api/admin/permissions/{permissionCode}/user-overridable
GET   /api/admin/users/{userId}/permissions
PUT   /api/admin/users/{userId}/permissions/{permissionCode}
DELETE /api/admin/users/{userId}/permissions/{permissionCode}
```

Permission evaluation is explicit user DENY, explicit user ALLOW, role grants,
then default deny. ALLOW overrides require the permission's
`USER_OVERRIDABLE` policy flag. It defaults to false; the ADMIN can manage this
policy. Making a permission non-overridable clears existing ALLOW overrides.
An explicit DENY always wins over a role grant. The API prevents removal of
the last enabled administrator and preserves management permissions on the
ADMIN role.

### Health and database connectivity

```text
GET http://localhost:8080/api/health
```

Example response:

```json
{
  "status": "UP",
  "database": "UP",
  "checkedAt": "2026-11-08T19:00:00Z"
}
```

Returns HTTP 503 with a generic error body if Oracle cannot be reached.

### Mutual fund houses

```text
GET http://localhost:8080/api/fund-houses
GET http://localhost:8080/api/fund-houses?activeOnly=false
```

### Scheme search and filters

```text
GET http://localhost:8080/api/schemes
GET http://localhost:8080/api/schemes?houseCode=DEMO-AMC
GET http://localhost:8080/api/schemes?category=INDEX&planType=DIRECT&optionType=GROWTH
GET http://localhost:8080/api/schemes?search=nifty&page=0&size=20
```

Supported categories: `EQUITY`, `DEBT`, `HYBRID`, `INDEX`, `ELSS`, `LIQUID`,
`OTHER`. Plans: `DIRECT`, `REGULAR`. Options: `GROWTH`, `IDCW`. Search is a
case-insensitive substring match against the scheme name. Results are
paginated; `size` must be between 1 and 100.

### Scheme NAV history

```text
GET http://localhost:8080/api/schemes/1/nav
GET http://localhost:8080/api/schemes/1/nav?fromDate=2026-01-01&toDate=2026-12-31
```

Dates use ISO `YYYY-MM-DD`. Results are sorted newest NAV date first. The
scheme ID is the internal `SCHEME_ID`, not an external provider code.

### AMFI NAV integration and refresh

The backend fetches AMFI's daily NAV text feed from
`https://www.amfiindia.com/spages/NAVAll.txt`. Only active rows in
`NAV_SCHEME_MAPPINGS` with provider name `AMFI` are imported. The schema's
`SAMPLE` mapping and NAV rows are fictional test data; they are not recognized
as AMFI mappings and are never overwritten by the AMFI importer.

Add a verified AMFI scheme code for each real local scheme before refreshing.
Run as `MUTUAL_FUND_APP` after replacing the example internal scheme ID and
provider scheme code:

```sql
INSERT INTO nav_scheme_mappings (scheme_id, provider_name, external_scheme_code)
VALUES (1, 'AMFI', 'REPLACE_WITH_VERIFIED_AMFI_SCHEME_CODE');
COMMIT;
```

Verify the provider code and scheme identity before inserting it. The unique
constraints prevent assigning the same AMFI scheme code to multiple local
schemes or creating duplicate provider mappings for one local scheme. Daily
imports upsert on the internal scheme/date key, preserving one record per
scheme and NAV date while updating corrected values and source hashes.
Historical records accumulate as scheduled daily refreshes run; the current
AMFI daily feed is not a historical archive backfill.

The refresh operation requires `NAV_REFRESH` (granted to the seeded ADMIN
role). It supports all mapped schemes, one mapped scheme, or the caller's
mapped watchlist:

```text
GET  /api/nav/dashboard
GET  /api/schemes/{schemeId}
POST /api/nav/refresh
```

POST requests require the authenticated session cookie and CSRF header as
described above. Example request bodies:

```json
{"scope":"ALL"}
{"scope":"SCHEME","schemeId":1}
{"scope":"WATCHLIST"}
```

Refresh attempts and outcomes are recorded in `NAV_REFRESH_LOG`; provider
request summaries and failures are recorded in `NAV_DATA_SOURCE_LOG`. Full
feed bodies are not stored. A scheduled refresh runs daily at 19:30 India
Standard Time. Configure it using `NAV_REFRESH_CRON` (Spring cron expression),
and optionally set `AMFI_NAV_URL` to a compatible feed URL.

The dashboard reports catalog coverage, most recent stored NAV date, and
refresh status. Fund details expose latest NAV and historical records. The
frontend refresh control is shown to ADMIN-role accounts only; API
authorization is independently enforced.

### Offline and automatic synchronization

The production frontend caches its static application shell with a service
worker and caches authenticated GET responses in IndexedDB under the signed-in
user ID. Retention uses the user's `offline_cache_days` preference. A prior
successful sign-in is required before offline reload can identify the local
workspace; cached identity is only a local-device convenience and does not
authenticate server requests.

Watchlist add/remove and investment transaction writes are queued offline.
Queued transactions carry a per-user UUID idempotency key and replay in order
after connectivity returns, using the normal session and CSRF protections.
Changes that fail server validation are retained as failed outbox entries; the
user must resolve them while online. SIP plan changes and administrative NAV
refresh require an online request and are not queued. Signing out clears that
user's local cached data and queued writes after confirmation.

The app also refreshes visible data on reconnect/focus and at the preference's
refresh interval. This refreshes reads; it does not fetch provider NAV itself.
AMFI NAV ingestion remains a separate backend daily schedule and admin action.

Example PowerShell probe:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8080/api/fund-houses
Invoke-RestMethod "http://localhost:8080/api/schemes?category=INDEX"
```

## What to start to use the application?

Start:

1. Oracle 19c and the configured PDB.
2. Apply required database migrations if needed, including the Phase 8
   migration for existing databases.
3. Start the Spring Boot backend using the command above.
4. Start the frontend using [frontend/README.md](../frontend/README.md) and
   open `http://localhost:5173`.

## Local security notes

The API binds to localhost by default. If exposing it to LAN users, configure
HTTPS at the host/reverse proxy, set `SESSION_COOKIE_SECURE=true`, and set the
allowed frontend origin explicitly. Do not use wildcard origins with
credentials. The React client must send credentials and the CSRF header on
state-changing requests. Authentication is not a substitute for protecting
the host and Oracle database credentials.
