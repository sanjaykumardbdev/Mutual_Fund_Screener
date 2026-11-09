# Mutual Fund Screener - Phase 1 database

This folder contains the Oracle 19c schema and architecture foundation for the
phased mutual-fund tracker. The Spring Boot API and React frontend, including AMFI NAV integration and
dashboard, are documented in the sibling
[`backend`](../backend/README.md) and [`frontend`](../frontend/README.md)
folders.

## What's here

- `00_create_schema_and_tablespace_for_mutualfund.sql` - DBA-only tablespace,
  application schema user, quota, and minimum schema-creation grants.
- `01_install_mutual_fund_schema.sql` - 21 tables, keys, checks, indexes,
  ADMIN/USER roles, permissions, role grants, and fictional sample fund/NAV
  data.
- `02_validate_mutual_fund_schema.sql` - checks tables, constraints, seed
  records, and duplicate/orphan conditions.
- `03_phase3_security_migration.sql` - one-time additive migration for existing
  databases to add the admin-managed permission override policy.
- `04_phase8_offline_investment_migration.sql` - one-time additive migration
  for existing databases to add the investment request idempotency key/index.
- NAV mappings, history, refresh logs, and provider logs are part of the
  Phase 1 schema; Phase 5/6 use these existing tables without a new migration.
- `architecture.md` - architecture and ER diagram plus key data rules.

## Prerequisites

1. Oracle Database 19c running locally, or a reachable Oracle 19c server.
2. A target pluggable database (PDB), for example `ORCLPDB`.
3. Oracle SQL Developer, SQLcl, or SQL*Plus.
4. DBA access to create the tablespace and application schema. After setup,
   use the `MUTUAL_FUND_APP` credentials for the schema install and validation.

`JAVA_HOME` and Maven are not required to install the Phase 1 database. They
are required to run the Spring Boot API in
[the backend module](../backend/README.md). Node.js and npm are required for
the React frontend in [the frontend module](../frontend/README.md).

## Install the database

1. Start the Oracle 19c database and make sure its PDB is open.
2. Open `00_create_schema_and_tablespace_for_mutualfund.sql`.
3. Change `DEFINE data_dir` to a directory on the **database host** that
   already exists and is writable by the Oracle database service account.
   The default is only an example. SQL Developer's machine and Oracle's
   database host may differ.
4. Connect as a DBA to the target PDB (not the container root). In SQL
   Developer, use **Run Script (F5)**. Enter a strong password when prompted.
   This script creates a tablespace and a schema; do not run it if either
   already exists.
5. Connect as `MUTUAL_FUND_APP` to the same PDB and run
   `01_install_mutual_fund_schema.sql` with **Run Script (F5)**.
6. Still connected as `MUTUAL_FUND_APP`, run
   `02_validate_mutual_fund_schema.sql` with **Run Script (F5)**.

For SQL*Plus on Windows, the equivalent sequence is:

```text
sqlplus /nolog
CONNECT sys@//localhost:1521/ORCLPDB AS SYSDBA
@Mutual_Fund_DB\00_create_schema_and_tablespace_for_mutualfund.sql
EXIT
```

When prompted, enter the DBA password and then the new application-schema
password. Then connect as the application schema and install/validate:

```text
sqlplus /nolog
CONNECT mutual_fund_app@//localhost:1521/ORCLPDB
@Mutual_Fund_DB\01_install_mutual_fund_schema.sql
@Mutual_Fund_DB\02_validate_mutual_fund_schema.sql
EXIT
```

Change `localhost`, port `1521`, and `ORCLPDB` if your Oracle listener or
service uses different values. SQL*Plus must be installed and available on
`PATH`; SQL Developer or SQLcl can also be used.

The install script is for a **new, empty schema**. Oracle commits DDL
implicitly, so a failed run may have created some objects already. Read the
first SQL error and fix the issue; for a clean rerun, have a DBA recreate the
empty application schema rather than repeatedly running the script over
partially created objects. Do not drop a schema containing data.

## Expected validation results

- Exactly 21 application tables.
- `ADMIN` and `USER` roles; eight base permissions.
- `ADMIN` has all eight seeded permissions. `USER` has four base permissions.
- One fictional demo AMC, one demo scheme, one provider mapping, and two demo
  NAV history rows.
- Duplicate NAV key query, duplicate watchlist key query, and orphan query
  return no rows.
- No application user is created by the database seed. Registration and
  administrator user creation belong to the authentication phase; no weak or
  placeholder login is installed.

## Run the application locally

After installing the schema (and applying the Phase 3 and Phase 8 migrations
once if this is an older database), start the local application services:

1. Start Oracle 19c and open the PDB.
2. Start the Spring Boot backend following the
   [backend README](../backend/README.md); it serves the API at
   `http://localhost:8080` by default.
3. Start the React development server following the
   [frontend README](../frontend/README.md); browse to
   `http://localhost:5173`.
4. To import real NAV data, add verified `AMFI` rows to
   `NAV_SCHEME_MAPPINGS` as described in the
   [backend integration guide](../backend/README.md#amfi-nav-integration-and-refresh).
