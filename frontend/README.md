# React fund explorer

The React/Vite frontend provides session login, mutual-fund scheme search and
filters, pagination, a user-specific watchlist, NAV summary metrics, fund
detail/history views, and an admin NAV refresh action. Oracle and the Spring
Boot API must be running first. Follow the
[backend setup guide](../backend/README.md) to configure the database
connection, AMFI scheme mappings, and initial administrator.

## Requirements

- Node.js 20.19+ or 22.12+ and npm (Vite 7 requirement).
- Spring Boot API running at `http://localhost:8080`.

## Start the development server (Windows PowerShell)

In a new PowerShell window:

```powershell
cd C:\Users\Sanjay\OneDrive\Documents\PLSQL-Programs\01_plsql_engine\Mutual_Fund_Screener\frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Keep the terminal open while using the page and
press `Ctrl+C` to stop Vite. Use `localhost` (not `127.0.0.1`) for the page:
the API's credentialed CORS policy allows `http://localhost:5173` by default,
and the browser needs the API's CSRF cookie on the same host.

To use another API URL, set `VITE_API_BASE_URL` before starting Vite and set
the backend's `APP_CORS_ALLOWED_ORIGIN` in the backend terminal before
starting Spring Boot. Both origins must match their respective server URLs.
For example:

```powershell
$env:VITE_API_BASE_URL = "http://localhost:8080"
npm run dev
```

In the backend PowerShell window, before starting `run-local.ps1`:

```powershell
$env:APP_CORS_ALLOWED_ORIGIN = "http://localhost:5173"
```

## Build

```powershell
npm run build
npm run preview
```

The production bundle is written to `dist/`. The preview server is for local
verification; production hosting and deployment are not configured here.

The NAV dashboard/detail pages read imported data from the database. The
ADMIN-only **Refresh NAV** button requests the AMFI daily feed and refreshes
all active mapped schemes; the backend also runs its configured daily
scheduled refresh. Non-admin users can view NAV dashboard/details and their
watchlist but cannot trigger a global refresh.

The demo AMC, scheme, and NAV history in the database are fictional and are
not live market data. Real schemes require verified AMFI mapping rows; see the
[backend AMFI setup instructions](../backend/README.md#amfi-nav-integration-and-refresh).
