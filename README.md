# Mutual Fund Screener

The project is being implemented one phase at a time. **Phase 1** provides the
Oracle 19c schema, sample reference data, validation queries, and
architecture/ER documentation. **Phase 2** adds the Spring Boot API for
database health, AMC, scheme, and NAV-history reads. **Phase 3** adds session
authentication, role/permission management, and user-scoped watchlists and
investments. **Phase 4** adds the React fund explorer and personal watchlist.
**Phase 5** imports mapped daily NAV records from AMFI and retains dated
history. **Phase 6** adds NAV dashboard metrics, per-scheme details/history,
and an admin-triggered refresh control. **Phase 7** adds a service-worker app
shell, user-scoped browser data cache, queued offline watchlist/investment
changes, and automatic reconnect/interval synchronization. **Phase 8** adds
user-scoped holdings, manual investment transactions, SIP plan management,
and ledger-based return analytics.

## Run locally (Windows)

Prerequisites: Oracle 19c with the project schema installed, Java 17, Maven
3.9+, and Node.js/npm. The database, API, and frontend run as separate
processes; start them in this order:

1. Start Oracle 19c and make sure the target PDB (default `ORCLPDB`) is open.
   The initial schema setup is documented in [Mutual_Fund_DB/README.md](Mutual_Fund_DB/README.md).
2. In PowerShell, start the Spring Boot backend as documented in
   [backend/README.md](backend/README.md). It listens on
   `http://localhost:8080`. If there is no application user yet, configure
   `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_EMAIL` before starting it;
   the launcher prompts securely for the initial admin password.
3. In a separate PowerShell window, start the React dev server:

   ```powershell
   cd C:\Users\Sanjay\OneDrive\Documents\PLSQL-Programs\01_plsql_engine\Mutual_Fund_Screener\frontend
   npm install
   npm run dev
   ```

4. Open `http://localhost:5173` and sign in with the application account.

Keep Oracle, the backend, and the frontend terminals running while using the
page. Stop the frontend with `Ctrl+C`, then the backend with `Ctrl+C`. See
[frontend/README.md](frontend/README.md) for frontend configuration and build
commands. Demo fund and NAV records are fictional, not live market data.
