import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "./api.js";
import { calculatePortfolioTotals } from "./portfolioMath.js";

const CATEGORIES = [
  ["", "All categories"],
  ["EQUITY", "Equity"],
  ["DEBT", "Debt"],
  ["HYBRID", "Hybrid"],
  ["INDEX", "Index"],
  ["ELSS", "ELSS"],
  ["LIQUID", "Liquid"],
  ["OTHER", "Other"],
];

const PAGE_SIZE = 12;

function formatNav(value) {
  if (value === null || value === undefined) return "—";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(value);
}

function NavChart({ history }) {
  const points = [...history].reverse();
  if (points.length < 2) {
    return <div className="chart-empty">At least two NAV records are needed to draw a trend.</div>;
  }
  const values = points.map((point) => Number(point.navValue));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const spread = max - min || max * 0.01 || 1;
  const coordinates = values.map((value, index) => ({
    x: 24 + (index * 752) / (values.length - 1),
    y: 210 - ((value - min) / spread) * 180,
  }));
  const path = coordinates.map((point, index) =>
    `${index === 0 ? "M" : "L"}${point.x},${point.y}`,
  ).join(" ");

  return (
    <div className="nav-chart-wrap">
      <svg className="nav-chart" viewBox="0 0 800 240" role="img" aria-label="NAV history trend">
        <path className="chart-gridline" d="M24 30H776 M24 120H776 M24 210H776" />
        <path className="chart-line" d={path} />
        {coordinates.map((point, index) => (
          index % Math.ceil(coordinates.length / 40) === 0 || index === coordinates.length - 1
            ? (
          <circle key={`${points[index].navDate}-${index}`} cx={point.x} cy={point.y} r="3">
            <title>{`${formatDate(points[index].navDate)}: ${formatNav(points[index].navValue)}`}</title>
          </circle>
            )
            : null
        ))}
      </svg>
      <div className="chart-axis"><span>{formatDate(points[0].navDate)}</span><span>{formatDate(points.at(-1).navDate)}</span></div>
    </div>
  );
}

function FundDetailsModal({ details, history, loading, range, onRangeChange, onClose }) {
  if (!details) return null;
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section className="fund-modal" role="dialog" aria-modal="true" aria-labelledby="fund-modal-title">
        <button className="modal-close" onClick={onClose} aria-label="Close fund details">×</button>
        <div className="eyebrow">{details.houseName} · {details.category}</div>
        <h2 id="fund-modal-title">{details.name}</h2>
        <p className="muted">{titleCase(details.planType)} · {titleCase(details.optionType)}{details.benchmark ? ` · Benchmark: ${details.benchmark}` : ""}</p>
        <div className="fund-detail-stats">
          <div><span>Latest NAV</span><strong>{formatNav(details.latestNav)}</strong></div>
          <div><span>NAV date</span><strong>{details.latestNavDate ? formatDate(details.latestNavDate) : "—"}</strong></div>
          <div><span>Source</span><strong>{details.latestNavProvider || "Not available"}</strong></div>
        </div>
        <div className="history-heading">
          <div><h3>NAV history</h3><span>{history.length} records</span></div>
          <div className="range-tabs" aria-label="NAV history date range">
            {[["1M", "1M"], ["6M", "6M"], ["1Y", "1Y"], ["ALL", "All"]].map(([value, label]) => (
              <button key={value} className={range === value ? "selected" : ""} onClick={() => onRangeChange(value)}>{label}</button>
            ))}
          </div>
        </div>
        {loading ? <div className="loading-panel"><span className="spinner" /> Loading NAV history...</div>
          : history.length === 0 ? <div className="chart-empty">No NAV history is available for this period.</div>
            : <NavChart history={history} />}
        <div className="nav-history-table">
          <div className="nav-history-header"><span>Date</span><span>NAV</span><span>Provider</span></div>
          {history.slice(0, 10).map((item) => (
            <div className="nav-history-row" key={`${item.navDate}-${item.id}`}>
              <span>{formatDate(item.navDate)}</span><strong>{formatNav(item.navValue)}</strong><span>{item.providerName}</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function PortfolioView({ portfolio, sips, onRefresh, onMessage, preferences, onSavePreferences }) {
  const [schemes, setSchemes] = useState([]);
  const [transactionType, setTransactionType] = useState("LUMPSUM");
  const [investmentForm, setInvestmentForm] = useState({
    schemeId: "", transactionDate: new Date().toISOString().slice(0, 10),
    amount: "", nav: "", units: "", sipId: "",
  });
  const [sipForm, setSipForm] = useState({
    schemeId: "", amount: "", frequency: "MONTHLY",
    startDate: new Date().toISOString().slice(0, 10), endDate: "",
  });
  const [saving, setSaving] = useState(false);
  const [refreshMinutes, setRefreshMinutes] = useState(preferences.refreshFrequencyMinutes);
  const [cacheDays, setCacheDays] = useState(preferences.offlineCacheDays);
  const totals = calculatePortfolioTotals(portfolio.holdings ?? []);

  useEffect(() => {
    api.schemes({ page: 0, size: 100 })
      .then((result) => setSchemes(result.content))
      .catch((error) => onMessage(error.message, "error"));
  }, [onMessage]);

  useEffect(() => {
    setRefreshMinutes(preferences.refreshFrequencyMinutes);
    setCacheDays(preferences.offlineCacheDays);
  }, [preferences]);

  async function submitInvestment(event) {
    event.preventDefault();
    setSaving(true);
    try {
      const request = {
        schemeId: Number(investmentForm.schemeId),
        transactionType,
        transactionDate: investmentForm.transactionDate,
        amount: Number(investmentForm.amount),
        clientRequestId: crypto.randomUUID(),
        ...(transactionType === "IDCW" ? {} : {
          nav: Number(investmentForm.nav),
          units: Number(investmentForm.units),
        }),
        ...(transactionType === "SIP" ? { sipId: Number(investmentForm.sipId) } : {}),
      };
      const result = await api.addInvestment(request);
      onMessage(result?.queuedOffline
        ? "Investment saved offline and queued for secure synchronization."
        : "Investment transaction recorded.");
      setInvestmentForm((current) => ({ ...current, amount: "", nav: "", units: "", sipId: "" }));
      await onRefresh();
    } catch (error) {
      onMessage(error.message, "error");
    } finally {
      setSaving(false);
    }
  }

  async function submitSip(event) {
    event.preventDefault();
    setSaving(true);
    try {
      await api.addSip({
        schemeId: Number(sipForm.schemeId),
        amount: Number(sipForm.amount),
        frequency: sipForm.frequency,
        startDate: sipForm.startDate,
        ...(sipForm.endDate ? { endDate: sipForm.endDate } : {}),
      });
      onMessage("SIP plan created.");
      setSipForm((current) => ({ ...current, amount: "" }));
      await onRefresh();
    } catch (error) {
      onMessage(error.message, "error");
    } finally {
      setSaving(false);
    }
  }

  async function changeSipStatus(sip, status) {
    try {
      await api.updateSipStatus(sip.id, status);
      onMessage(`SIP ${status.toLowerCase()}.`);
      await onRefresh();
    } catch (error) {
      onMessage(error.message, "error");
    }
  }

  async function savePreferences(event) {
    event.preventDefault();
    try {
      await onSavePreferences({
        refreshFrequencyMinutes: Number(refreshMinutes),
        offlineCacheDays: Number(cacheDays),
      });
      onMessage("Synchronization preferences saved.");
    } catch (error) {
      onMessage(error.message, "error");
    }
  }

  return (
    <div className="portfolio-page">
      <section className="portfolio-summary" aria-label="Portfolio performance">
        <Metric label="Net contributions" value={formatNav(totals.totalInvested)} />
        <Metric label="Current value" value={totals.currentValue == null ? "NAV unavailable" : formatNav(totals.currentValue)} />
        <Metric label="Realized proceeds + IDCW" value={formatNav(totals.redemptionProceeds + totals.dividends)} />
        <Metric label="Total gain / loss" value={totals.gainLoss == null ? "NAV unavailable" : formatNav(totals.gainLoss)}
          detail={totals.returnPercent == null ? "Absolute return" : `${totals.returnPercent.toFixed(2)}% absolute return`} />
      </section>
      <p className="portfolio-disclaimer">Returns are ledger-based absolute returns: current value + redemption proceeds + IDCW payouts − contributions. They are not annualized and exclude taxes, fees, and external cash flows not recorded here.</p>

      <section className="portfolio-section">
        <div className="section-title"><div><h2>Holdings and performance</h2><span>Latest stored NAV values</span></div></div>
        {(portfolio.holdings ?? []).length === 0 ? <div className="chart-empty">Record an investment to start tracking your portfolio.</div> : (
          <div className="portfolio-table-wrap">
            <table className="portfolio-table">
              <thead><tr><th>Scheme</th><th>Units</th><th>Latest NAV</th><th>Current value</th><th>Gain / loss</th><th>Return</th></tr></thead>
              <tbody>{portfolio.holdings.map((holding) => (
                <tr key={holding.schemeId}>
                  <td><strong>{holding.schemeName}</strong><span>{holding.houseName}</span></td>
                  <td>{Number(holding.unitsHeld).toFixed(4)}</td>
                  <td>{formatNav(holding.latestNav)}<span>{holding.latestNavDate ? formatDate(holding.latestNavDate) : "No NAV date"}</span></td>
                  <td>{formatNav(holding.currentValue)}</td>
                  <td>{formatNav(holding.gainLoss)}</td>
                  <td>{holding.returnPercent == null ? "—" : `${Number(holding.returnPercent).toFixed(2)}%`}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </section>

      <div className="portfolio-forms">
        <section className="portfolio-section">
          <div className="section-title"><div><h2>Record transaction</h2><span>Manual entry; no trades are executed</span></div></div>
          <form className="data-form" onSubmit={submitInvestment}>
            <label>Transaction type<select value={transactionType} onChange={(event) => {
              setTransactionType(event.target.value);
              setInvestmentForm((current) => ({ ...current, sipId: "" }));
            }}>
              <option value="LUMPSUM">Lump sum</option><option value="SIP">SIP installment</option>
              <option value="REDEMPTION">Redemption</option><option value="IDCW">IDCW payout</option>
            </select></label>
            <label>Scheme<select required value={investmentForm.schemeId} onChange={(event) => setInvestmentForm({ ...investmentForm, schemeId: event.target.value })}>
              <option value="">Select a scheme</option>{schemes.map((scheme) => <option key={scheme.id} value={scheme.id}>{scheme.name}</option>)}
            </select></label>
            {transactionType === "SIP" && <label>SIP plan<select required value={investmentForm.sipId} onChange={(event) => {
              const sip = sips.find((item) => item.id === Number(event.target.value));
              setInvestmentForm({ ...investmentForm, sipId: event.target.value, schemeId: sip ? String(sip.schemeId) : investmentForm.schemeId });
            }}>
              <option value="">Select a plan</option>{sips.filter((sip) => sip.status === "ACTIVE").map((sip) => <option key={sip.id} value={sip.id}>{sip.schemeName} · {formatNav(sip.amount)}/{sip.frequency.toLowerCase()}</option>)}
            </select></label>}
            <label>Date<input type="date" required value={investmentForm.transactionDate} onChange={(event) => setInvestmentForm({ ...investmentForm, transactionDate: event.target.value })} /></label>
            <label>Amount (INR)<input type="number" min="0.01" step="0.01" required value={investmentForm.amount} onChange={(event) => setInvestmentForm({ ...investmentForm, amount: event.target.value })} /></label>
            {transactionType !== "IDCW" && <>
              <label>Transaction NAV<input type="number" min="0.000001" step="0.000001" required value={investmentForm.nav} onChange={(event) => setInvestmentForm({ ...investmentForm, nav: event.target.value })} /></label>
              <label>Units<input type="number" min="0.000001" step="0.000001" required value={investmentForm.units} onChange={(event) => setInvestmentForm({ ...investmentForm, units: event.target.value })} /></label>
            </>}
            <button className="button button-primary" disabled={saving}>Save transaction</button>
          </form>
        </section>
        <section className="portfolio-section">
          <div className="section-title"><div><h2>SIP plans</h2><span>Track schedules; record installments separately</span></div></div>
          {sips.map((sip) => (
            <div className="sip-row" key={sip.id}>
              <div><strong>{sip.schemeName}</strong><span>{formatNav(sip.amount)} · {sip.frequency} · {sip.status}</span></div>
              {sip.status === "ACTIVE" && <button className="text-button" onClick={() => changeSipStatus(sip, "PAUSED")}>Pause</button>}
              {sip.status === "PAUSED" && <button className="text-button" onClick={() => changeSipStatus(sip, "ACTIVE")}>Resume</button>}
              {["ACTIVE", "PAUSED"].includes(sip.status) && <button className="add-link remove-link" onClick={() => changeSipStatus(sip, "CANCELLED")}>Cancel</button>}
            </div>
          ))}
          <form className="data-form sip-form" onSubmit={submitSip}>
            <label>Scheme<select required value={sipForm.schemeId} onChange={(event) => setSipForm({ ...sipForm, schemeId: event.target.value })}>
              <option value="">Select a scheme</option>{schemes.map((scheme) => <option key={scheme.id} value={scheme.id}>{scheme.name}</option>)}
            </select></label>
            <label>Installment amount<input type="number" min="0.01" step="0.01" required value={sipForm.amount} onChange={(event) => setSipForm({ ...sipForm, amount: event.target.value })} /></label>
            <label>Frequency<select value={sipForm.frequency} onChange={(event) => setSipForm({ ...sipForm, frequency: event.target.value })}>
              <option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option><option value="QUARTERLY">Quarterly</option>
            </select></label>
            <label>Start date<input type="date" required value={sipForm.startDate} onChange={(event) => setSipForm({ ...sipForm, startDate: event.target.value })} /></label>
            <label>End date (optional)<input type="date" value={sipForm.endDate} onChange={(event) => setSipForm({ ...sipForm, endDate: event.target.value })} /></label>
            <button className="button button-secondary" disabled={saving}>Create SIP plan</button>
          </form>
        </section>
      </div>

      <section className="portfolio-section sync-preferences">
        <div className="section-title"><div><h2>Offline and synchronization</h2><span>Cached data is stored only for this signed-in account on this browser.</span></div></div>
        <form className="preferences-form" onSubmit={savePreferences}>
          <label>Refresh interval<select value={refreshMinutes} onChange={(event) => setRefreshMinutes(event.target.value)}>
            <option value="15">Every 15 minutes</option><option value="60">Every hour</option>
            <option value="240">Every 4 hours</option><option value="1440">Daily</option>
          </select></label>
          <label>Offline cache retention<select value={cacheDays} onChange={(event) => setCacheDays(event.target.value)}>
            <option value="7">7 days</option><option value="30">30 days</option><option value="90">90 days</option>
          </select></label>
          <button className="button button-secondary">Save preferences</button>
        </form>
      </section>
    </div>
  );
}

function Metric({ label, value, detail }) {
  return <div className="portfolio-metric"><span>{label}</span><strong>{value}</strong>{detail && <small>{detail}</small>}</div>;
}

function initials(name = "") {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function Login({ onLogin }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onLogin(username.trim(), password);
    } catch (loginError) {
      setError(loginError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <Brand />
        <p className="eyebrow">YOUR INVESTING, IN FOCUS</p>
        <h1>Welcome back.</h1>
        <p className="muted">Sign in to explore funds and manage your personal watchlist.</p>
        <form className="login-form" onSubmit={submit}>
          <label htmlFor="username">Username</label>
          <input
            id="username"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
          />
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="button button-primary login-submit" disabled={busy}>
            {busy ? "Signing in..." : "Sign in"}
          </button>
        </form>
        <p className="login-footnote">Private access · Your data stays on your server</p>
      </section>
      <aside className="login-aside">
        <div className="aside-orbit orbit-one" />
        <div className="aside-orbit orbit-two" />
        <div className="aside-copy">
          <span className="aside-kicker">INDIA · MUTUAL FUNDS</span>
          <h2>Make room for a clearer view.</h2>
          <p>Explore schemes across fund houses. Keep the ones that matter close.</p>
          <div className="aside-stat">
            <span className="stat-dot" />
            <span>Thoughtful tracking starts here</span>
          </div>
        </div>
        <span className="aside-decoration">₹</span>
      </aside>
    </main>
  );
}

function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark" aria-hidden="true">
        <span />
        <span />
        <span />
      </span>
      <span>fundwise</span>
    </div>
  );
}

function App() {
  const [user, setUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [activeView, setActiveView] = useState("explore");
  const [houses, setHouses] = useState([]);
  const [pageData, setPageData] = useState(null);
  const [watchlist, setWatchlist] = useState([]);
  const [selectedHouse, setSelectedHouse] = useState("");
  const [category, setCategory] = useState("");
  const [planType, setPlanType] = useState("");
  const [optionType, setOptionType] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [watchlistLoading, setWatchlistLoading] = useState(false);
  const [busyScheme, setBusyScheme] = useState(null);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [dashboard, setDashboard] = useState(null);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedScheme, setSelectedScheme] = useState(null);
  const [schemeDetails, setSchemeDetails] = useState(null);
  const [navHistory, setNavHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyRange, setHistoryRange] = useState("ALL");
  const [online, setOnline] = useState(navigator.onLine);
  const [usingCachedData, setUsingCachedData] = useState(false);
  const [syncMessage, setSyncMessage] = useState("");
  const [pendingCount, setPendingCount] = useState(0);
  const [portfolio, setPortfolio] = useState(null);
  const [sips, setSips] = useState([]);
  const [preferences, setPreferences] = useState({
    refreshFrequencyMinutes: 60,
    offlineCacheDays: 30,
  });
  const [portfolioLoading, setPortfolioLoading] = useState(false);

  const filters = useMemo(() => ({
    houseCode: selectedHouse,
    category,
    planType,
    optionType,
    search: search.trim(),
    page,
    size: PAGE_SIZE,
  }), [selectedHouse, category, planType, optionType, search, page]);

  useEffect(() => {
    let alive = true;
    api.currentUser()
      .then((currentUser) => {
        if (alive) setUser(currentUser);
      })
      .catch((loadError) => {
        const offlineUser = api.offlineIdentity();
        if (alive && loadError.isNetworkError && offlineUser) {
          api.configureOfflineUser(offlineUser.id);
          setUser({ ...offlineUser, enabled: true, offline: true });
          setUsingCachedData(true);
          setSyncMessage("Offline mode. Cached data is available; queued changes sync after reconnecting.");
        } else if (alive) {
          setUser(null);
        }
      })
      .finally(() => {
        if (alive) setAuthLoading(false);
      });
    return () => { alive = false; };
  }, []);

  const loadCatalog = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [houseData, schemes] = await Promise.all([
        api.fundHouses(),
        api.schemes(filters),
      ]);
      setHouses(houseData);
      setPageData(schemes);
    } catch (loadError) {
      setError(loadError.message);
      if (loadError.message.includes("session has expired")) setUser(null);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  const loadWatchlist = useCallback(async () => {
    setWatchlistLoading(true);
    try {
      setWatchlist(await api.watchlist());
    } catch (loadError) {
      setError(loadError.message);
      if (loadError.message.includes("session has expired")) setUser(null);
    } finally {
      setWatchlistLoading(false);
    }
  }, []);

  const loadPortfolio = useCallback(async () => {
    setPortfolioLoading(true);
    try {
      const [portfolioData, sipData] = await Promise.all([api.portfolio(), api.sips()]);
      setPortfolio(portfolioData);
      setSips(sipData);
    } catch (loadError) {
      setError(loadError.message);
      if (loadError.message.includes("session has expired")) setUser(null);
    } finally {
      setPortfolioLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!user || activeView !== "explore") return;
    loadCatalog();
  }, [user, activeView, loadCatalog]);

  useEffect(() => {
    if (!user) return;
    loadWatchlist();
  }, [user, loadWatchlist]);

  useEffect(() => {
    if (!user) return;
    api.navDashboard()
      .then(setDashboard)
      .catch((loadError) => {
        setError(loadError.message);
        if (loadError.message.includes("session has expired")) setUser(null);
      });
  }, [user]);

  useEffect(() => {
    if (!user || activeView !== "watchlist") return;
    loadWatchlist();
  }, [user, activeView, loadWatchlist]);

  useEffect(() => {
    if (!user) return;
    let active = true;
    Promise.all([api.preferences(), api.pendingMutations()])
      .then(([nextPreferences, pending]) => {
        if (!active) return;
        setPreferences(nextPreferences);
        api.configureOfflineUser(user.id, nextPreferences.offlineCacheDays);
        setPendingCount(pending.length);
      })
      .catch((loadError) => {
        if (active) setError(loadError.message);
      });
    return () => { active = false; };
  }, [user]);

  useEffect(() => {
    if (!user || activeView !== "portfolio") return;
    loadPortfolio();
  }, [user, activeView, loadPortfolio]);

  useEffect(() => {
    const handleOffline = () => {
      setOnline(false);
      setUsingCachedData(true);
      setSyncMessage("Offline mode. Changes will be synchronized when the connection returns.");
    };
    const handleOnline = async () => {
      setOnline(true);
      setUsingCachedData(false);
      setSyncMessage("Synchronizing queued changes...");
      try {
        const result = await api.synchronize();
        setPendingCount(result.pending);
        if (result.blocked) {
          setSyncMessage("Sign in again to synchronize your queued changes.");
        } else if (result.synchronized) {
          setSyncMessage(`${result.synchronized} queued change(s) synchronized.`);
          await Promise.all([loadWatchlist(), loadCatalog(), loadPortfolio()]);
          setDashboard(await api.navDashboard());
        } else {
          setSyncMessage(result.pending ? `${result.pending} change(s) waiting to sync.` : "");
        }
      } catch (syncError) {
        setSyncMessage(syncError.message);
      }
    };
    const handleCache = () => {
      setUsingCachedData(true);
      setSyncMessage("Backend unavailable. Displaying cached data.");
    };
    const handleOutbox = async () => {
      setPendingCount((await api.pendingMutations()).length);
    };
    const handleStorageError = () => setSyncMessage("Browser offline storage is unavailable.");
    window.addEventListener("offline", handleOffline);
    window.addEventListener("online", handleOnline);
    window.addEventListener("fundwise:offline-cache", handleCache);
    window.addEventListener("fundwise:outbox-changed", handleOutbox);
    window.addEventListener("fundwise:offline-storage-error", handleStorageError);
    return () => {
      window.removeEventListener("offline", handleOffline);
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("fundwise:offline-cache", handleCache);
      window.removeEventListener("fundwise:outbox-changed", handleOutbox);
      window.removeEventListener("fundwise:offline-storage-error", handleStorageError);
    };
  }, [loadCatalog, loadPortfolio, loadWatchlist]);

  useEffect(() => {
    if (!user || !online) return;
    const synchronizeReads = async () => {
      try {
        await api.synchronize();
        setPendingCount((await api.pendingMutations()).length);
        setDashboard(await api.navDashboard());
        if (activeView === "explore") await loadCatalog();
        if (activeView === "watchlist") await loadWatchlist();
        if (activeView === "portfolio") await loadPortfolio();
      } catch (syncError) {
        setSyncMessage(syncError.message);
      }
    };
    const delay = Math.max(15, preferences.refreshFrequencyMinutes) * 60 * 1000;
    const timer = window.setInterval(synchronizeReads, delay);
    window.addEventListener("focus", synchronizeReads);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", synchronizeReads);
    };
  }, [user, online, activeView, preferences.refreshFrequencyMinutes,
    loadCatalog, loadPortfolio, loadWatchlist]);

  useEffect(() => {
    if (!toast) return undefined;
    const timeout = window.setTimeout(() => setToast(""), 2800);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  async function login(username, password) {
    const authenticatedUser = await api.login(username, password);
    setUser(authenticatedUser);
    setActiveView("explore");
    setError("");
    setUsingCachedData(false);
    setSyncMessage("");
  }

  async function logout() {
    setError("");
    const pending = await api.pendingMutations();
    if (pending.length && !window.confirm(
      `${pending.length} offline change(s) have not synchronized. Signing out will discard them from this browser. Continue?`,
    )) return;
    try {
      if (online) await api.logout();
      await api.clearOfflineSession(user.id);
      setUser(null);
      setWatchlist([]);
      setPortfolio(null);
      setSips([]);
      setPendingCount(0);
      setUsingCachedData(false);
      setSyncMessage("");
    } catch (logoutError) {
      setError(logoutError.message);
    }
  }

  function updateFilter(setter) {
    return (event) => {
      setter(event.target.value);
      setPage(0);
    };
  }

  async function toggleWatchlist(scheme) {
    const alreadyAdded = watchlist.some((item) => item.schemeId === scheme.id);
    setBusyScheme(scheme.id);
    setError("");
    try {
      if (alreadyAdded) {
        await api.removeWatchlist(scheme.id);
        setWatchlist((current) => current.filter((item) => item.schemeId !== scheme.id));
        setToast("Removed from your watchlist");
      } else {
        const result = await api.addWatchlist(scheme.id);
        if (result?.queuedOffline) {
          setWatchlist((current) => current.some((item) => item.schemeId === scheme.id) ? current : [
            ...current,
            {
              schemeId: scheme.id,
              schemeCode: scheme.code,
              schemeName: scheme.name,
              houseName: scheme.houseName,
              category: scheme.category,
              planType: scheme.planType,
              optionType: scheme.optionType,
              latestNavDate: null,
              latestNav: null,
              addedAt: new Date().toISOString(),
            },
          ]);
          setToast("Saved offline; watchlist change is queued to sync.");
        } else {
          await loadWatchlist();
          setToast("Added to your watchlist");
        }
      }
    } catch (actionError) {
      setError(actionError.message);
      if (actionError.message.includes("session has expired")) setUser(null);
    } finally {
      setBusyScheme(null);
    }
  }

  async function removeWatchlistItem(schemeId) {
    setBusyScheme(schemeId);
    setError("");
    try {
      await api.removeWatchlist(schemeId);
      setWatchlist((current) => current.filter((item) => item.schemeId !== schemeId));
      setToast("Removed from your watchlist");
    } catch (actionError) {
      setError(actionError.message);
      if (actionError.message.includes("session has expired")) setUser(null);
    } finally {
      setBusyScheme(null);
    }
  }

  async function openSchemeDetails(schemeId) {
    setSelectedScheme(schemeId);
    setSchemeDetails(null);
    setNavHistory([]);
    setHistoryRange("ALL");
    setHistoryLoading(true);
    try {
      const [details, history] = await Promise.all([
        api.schemeDetails(schemeId),
        api.navHistory(schemeId),
      ]);
      setSchemeDetails(details);
      setNavHistory(history);
    } catch (detailError) {
      setError(detailError.message);
      setSelectedScheme(null);
      if (detailError.message.includes("session has expired")) setUser(null);
    } finally {
      setHistoryLoading(false);
    }
  }

  async function changeHistoryRange(range) {
    setHistoryRange(range);
    if (!selectedScheme) return;
    const months = { "1M": 1, "6M": 6, "1Y": 12 }[range];
    let fromDate;
    if (months) {
      const from = new Date();
      from.setMonth(from.getMonth() - months);
      fromDate = `${from.getFullYear()}-${String(from.getMonth() + 1).padStart(2, "0")}-${String(from.getDate()).padStart(2, "0")}`;
    }
    setHistoryLoading(true);
    try {
      setNavHistory(await api.navHistory(selectedScheme, fromDate));
    } catch (historyError) {
      setError(historyError.message);
      if (historyError.message.includes("session has expired")) setUser(null);
    } finally {
      setHistoryLoading(false);
    }
  }

  async function refreshNav() {
    setRefreshing(true);
    setError("");
    try {
      const result = await api.refreshNav("ALL");
      setToast(result.message);
      setDashboard(await api.navDashboard());
      if (activeView === "explore") await loadCatalog();
      await loadWatchlist();
      if (selectedScheme) await openSchemeDetails(selectedScheme);
    } catch (refreshError) {
      setError(refreshError.message);
    } finally {
      setRefreshing(false);
    }
  }

  function showMessage(message, type = "success") {
    if (type === "error") setError(message);
    else setToast(message);
  }

  async function savePreferences(nextPreferences) {
    const saved = await api.updatePreferences(nextPreferences);
    setPreferences(saved);
    api.configureOfflineUser(user.id, saved.offlineCacheDays);
  }

  if (authLoading) {
    return <div className="startup-screen"><Brand /><span>Connecting to your account...</span></div>;
  }
  if (!user) return <Login onLogin={login} />;

  const schemes = pageData?.content ?? [];
  const totalSchemes = pageData?.totalElements ?? 0;
  const totalPages = pageData?.totalPages ?? 0;

  return (
    <div className="app-shell">
      <header className="topbar">
        <Brand />
        <div className="topbar-right">
          <span className="market-chip"><span className="india-dot" /> India market</span>
          <span className="topbar-divider" />
          <div className="user-chip">
            <span className="avatar">{initials(user.firstName || user.username)}</span>
            <span className="user-name">{user.firstName || user.username}</span>
          </div>
          <button className="icon-button logout-button" onClick={logout} title="Sign out" aria-label="Sign out">
            <LogoutIcon />
          </button>
        </div>
      </header>

      <div className="workspace">
        <aside className="sidebar">
          <div className="sidebar-label">WORKSPACE</div>
          <button
            className={`nav-item ${activeView === "explore" ? "active" : ""}`}
            onClick={() => { setActiveView("explore"); setError(""); }}
          >
            <ExploreIcon />
            <span>Fund explorer</span>
          </button>
          <button
            className={`nav-item ${activeView === "watchlist" ? "active" : ""}`}
            onClick={() => { setActiveView("watchlist"); setError(""); }}
          >
            <BookmarkIcon />
            <span>My watchlist</span>
            <span className="nav-count">{watchlist.length}</span>
          </button>
          <button
            className={`nav-item ${activeView === "portfolio" ? "active" : ""}`}
            onClick={() => { setActiveView("portfolio"); setError(""); }}
          >
            <PortfolioIcon />
            <span>Portfolio</span>
          </button>
          <div className="sidebar-bottom">
            <div className="help-card">
              <span className="help-icon">i</span>
              <p>NAV is published once per business day. Always check the displayed NAV date.</p>
            </div>
            <div className="sidebar-user">
              <span className="avatar avatar-small">{initials(user.firstName || user.username)}</span>
              <div><strong>{user.firstName || user.username}</strong><span>Personal workspace</span></div>
            </div>
          </div>
        </aside>

        <main className="main-content">
          {(usingCachedData || !online || pendingCount > 0) && (
            <div className={`sync-banner ${!online ? "offline" : ""}`} role="status">
              <span className="sync-status-dot" />
              <span>{!online ? "Offline" : pendingCount ? `${pendingCount} pending` : "Cached mode"}</span>
              <span className="sync-banner-message">{syncMessage || (pendingCount ? "Queued changes will sync automatically." : "Showing saved browser data.")}</span>
            </div>
          )}
          <div className="page-heading">
            <div>
              <div className="eyebrow">{activeView === "explore" ? "DISCOVER & SELECT" : activeView === "portfolio" ? "YOUR INVESTMENTS" : "YOUR SAVED FUNDS"}</div>
              <h1>{activeView === "explore" ? "Fund explorer" : activeView === "portfolio" ? "Investment portfolio" : "My watchlist"}</h1>
              <p>{activeView === "explore"
                ? "Find a scheme that fits your research. Add it to your personal watchlist."
                : activeView === "portfolio"
                  ? "Track contributions, holdings, SIP schedules, and ledger-based returns."
                  : "Your selected schemes, gathered in one place."}</p>
            </div>
            {activeView === "explore" && (
              <div className="heading-market"><span className="heading-india">IN</span><div><span>Market</span><strong>India</strong></div></div>
            )}
          </div>

          <section className="dashboard-strip" aria-label="NAV data dashboard">
            <div className="dashboard-stat"><span>Catalog schemes</span><strong>{dashboard?.totalSchemes ?? "—"}</strong></div>
            <div className="dashboard-stat"><span>Schemes with NAV</span><strong>{dashboard?.schemesWithNav ?? "—"}</strong></div>
            <div className="dashboard-stat"><span>Latest NAV date</span><strong>{dashboard?.latestNavDate ? formatDate(dashboard.latestNavDate) : "—"}</strong></div>
            <div className="dashboard-stat dashboard-refresh-status">
              <span>Last refresh</span>
              <strong>{dashboard?.lastRefreshResult
                ? `${titleCase(dashboard.lastRefreshResult)} · ${dashboard.lastRefreshRecords} records`
                : "Not run yet"}</strong>
            </div>
            {user.roles?.includes("ADMIN") && (
              <button className="button button-secondary refresh-button" onClick={refreshNav} disabled={refreshing}>
                {refreshing ? <><span className="mini-spinner" /> Refreshing...</> : <><RefreshIcon /> Refresh NAV</>}
              </button>
            )}
          </section>

          {error && (
            <div className="alert alert-error" role="alert">
              <span>{error}</span>
              <button onClick={() => setError("")} aria-label="Dismiss error">×</button>
            </div>
          )}

          {activeView === "portfolio" ? (
            portfolioLoading || !portfolio ? (
              <div className="loading-panel"><span className="spinner" /> Loading portfolio...</div>
            ) : (
              <PortfolioView
                portfolio={portfolio}
                sips={sips}
                onRefresh={loadPortfolio}
                onMessage={showMessage}
                preferences={preferences}
                onSavePreferences={savePreferences}
              />
            )
          ) : activeView === "explore" ? (
            <>
              <section className="filter-panel" aria-label="Fund filters">
                <div className="filter-top">
                  <label className="search-field">
                    <SearchIcon />
                    <input
                      type="search"
                      placeholder="Search schemes by name..."
                      value={search}
                      onChange={updateFilter(setSearch)}
                    />
                    {search && <button className="clear-search" onClick={() => { setSearch(""); setPage(0); }} aria-label="Clear search">×</button>}
                  </label>
                  <label className="select-field">
                    <span>Fund house</span>
                    <select value={selectedHouse} onChange={updateFilter(setSelectedHouse)}>
                      <option value="">All fund houses</option>
                      {houses.map((house) => <option key={house.id} value={house.code}>{house.name}</option>)}
                    </select>
                    <ChevronIcon />
                  </label>
                  <label className="select-field">
                    <span>Category</span>
                    <select value={category} onChange={updateFilter(setCategory)}>
                      {CATEGORIES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select>
                    <ChevronIcon />
                  </label>
                </div>
                <div className="filter-bottom">
                  <div className="filter-group">
                    <span className="filter-label">Plan</span>
                    <div className="segmented-control">
                      {[["", "All"], ["DIRECT", "Direct"], ["REGULAR", "Regular"]].map(([value, label]) => (
                        <button key={value} className={planType === value ? "selected" : ""} onClick={() => { setPlanType(value); setPage(0); }}>{label}</button>
                      ))}
                    </div>
                  </div>
                  <span className="filter-separator" />
                  <div className="filter-group">
                    <span className="filter-label">Option</span>
                    <div className="segmented-control">
                      {[["", "All"], ["GROWTH", "Growth"], ["IDCW", "IDCW"]].map(([value, label]) => (
                        <button key={value} className={optionType === value ? "selected" : ""} onClick={() => { setOptionType(value); setPage(0); }}>{label}</button>
                      ))}
                    </div>
                  </div>
                  <button className="reset-button" onClick={() => {
                    setSelectedHouse(""); setCategory(""); setPlanType(""); setOptionType(""); setSearch(""); setPage(0);
                  }}>Reset filters</button>
                </div>
              </section>

              <div className="results-toolbar">
                <div><strong>{totalSchemes}</strong> {totalSchemes === 1 ? "scheme" : "schemes"} found</div>
                <div className="results-hint"><span className="hint-dot" /> Latest stored scheme data</div>
              </div>

              {loading ? (
                <div className="loading-panel"><span className="spinner" /> Loading schemes...</div>
              ) : schemes.length === 0 ? (
                <div className="empty-panel">
                  <span className="empty-icon"><SearchIcon /></span>
                  <h2>No schemes match these filters</h2>
                  <p>Try changing your search or clearing one of the filters.</p>
                  <button className="button button-secondary" onClick={() => {
                    setSelectedHouse(""); setCategory(""); setPlanType(""); setOptionType(""); setSearch(""); setPage(0);
                  }}>Clear all filters</button>
                </div>
              ) : (
                <div className="scheme-grid">
                  {schemes.map((scheme) => {
                    const inWatchlist = watchlist.some((item) => item.schemeId === scheme.id);
                    return (
                      <article className="scheme-card" key={scheme.id}>
                        <div className="scheme-card-head">
                          <span className={`amc-avatar amc-${(scheme.houseCode || "x").slice(0, 1).toLowerCase()}`}>{initials(scheme.houseName)}</span>
                          <div className="scheme-house"><strong>{scheme.houseName}</strong><span>{scheme.houseCode}</span></div>
                          <button
                            className={`bookmark-toggle ${inWatchlist ? "saved" : ""}`}
                            onClick={() => toggleWatchlist(scheme)}
                            disabled={busyScheme === scheme.id}
                            aria-label={inWatchlist ? `Remove ${scheme.name} from watchlist` : `Add ${scheme.name} to watchlist`}
                            title={inWatchlist ? "Remove from watchlist" : "Add to watchlist"}
                          >
                            {busyScheme === scheme.id ? <span className="mini-spinner" /> : <BookmarkIcon filled={inWatchlist} />}
                          </button>
                        </div>
                        <h2>{scheme.name}</h2>
                        <div className="scheme-tags">
                          <span className="tag tag-category">{categoryLabel(scheme.category)}</span>
                          <span className="tag">{titleCase(scheme.planType)}</span>
                          <span className="tag">{titleCase(scheme.optionType)}</span>
                        </div>
                        {scheme.benchmark && <p className="scheme-benchmark">Benchmark <strong>{scheme.benchmark}</strong></p>}
                        <div className="scheme-card-foot">
                          <button className="details-link" onClick={() => openSchemeDetails(scheme.id)}>Fund details <span>↗</span></button>
                          <button className={`add-link ${inWatchlist ? "added" : ""}`} onClick={() => toggleWatchlist(scheme)} disabled={busyScheme === scheme.id}>
                            {inWatchlist ? "Added" : "Add to watchlist"} <span>{inWatchlist ? "✓" : "↗"}</span>
                          </button>
                        </div>
                      </article>
                    );
                  })}
                </div>
              )}

              {!loading && totalPages > 1 && (
                <div className="pagination">
                  <span>Page {page + 1} of {totalPages}</span>
                  <div>
                    <button className="button button-secondary" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button>
                    <button className="button button-secondary" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}>Next</button>
                  </div>
                </div>
              )}
            </>
          ) : (
            <>
              <div className="results-toolbar">
                <div><strong>{watchlist.length}</strong> {watchlist.length === 1 ? "saved scheme" : "saved schemes"}</div>
                <button className="text-button" onClick={() => setActiveView("explore")}>Browse funds <span>↗</span></button>
              </div>
              {watchlistLoading ? (
                <div className="loading-panel"><span className="spinner" /> Loading your watchlist...</div>
              ) : watchlist.length === 0 ? (
                <div className="empty-panel">
                  <span className="empty-icon"><BookmarkIcon /></span>
                  <h2>Your watchlist is ready</h2>
                  <p>Explore the fund catalog and save schemes to follow them here.</p>
                  <button className="button button-primary" onClick={() => setActiveView("explore")}>Explore schemes</button>
                </div>
              ) : (
                <div className="scheme-grid">
                  {watchlist.map((scheme) => (
                    <article className="scheme-card watch-card" key={scheme.schemeId}>
                      <div className="scheme-card-head">
                        <span className={`amc-avatar amc-${(scheme.schemeCode || "x").slice(0, 1).toLowerCase()}`}>{initials(scheme.houseName)}</span>
                        <div className="scheme-house"><strong>{scheme.houseName}</strong><span>{scheme.schemeCode}</span></div>
                        <button
                          className="bookmark-toggle saved"
                          onClick={() => removeWatchlistItem(scheme.schemeId)}
                          disabled={busyScheme === scheme.schemeId}
                          aria-label={`Remove ${scheme.schemeName} from watchlist`}
                        >
                          {busyScheme === scheme.schemeId ? <span className="mini-spinner" /> : <BookmarkIcon filled />}
                        </button>
                      </div>
                      <h2>{scheme.schemeName}</h2>
                      <div className="scheme-tags">
                        <span className="tag tag-category">{categoryLabel(scheme.category)}</span>
                        <span className="tag">{titleCase(scheme.planType)}</span>
                        <span className="tag">{titleCase(scheme.optionType)}</span>
                      </div>
                      <div className="watch-nav">
                        <div><span>Latest stored NAV</span><strong>{scheme.latestNav == null ? "Not available" : `₹${Number(scheme.latestNav).toFixed(4)}`}</strong></div>
                        <div><span>NAV date</span><strong>{scheme.latestNavDate ? formatDate(scheme.latestNavDate) : "Not available"}</strong></div>
                      </div>
                      <div className="scheme-card-foot">
                        <button className="details-link" onClick={() => openSchemeDetails(scheme.schemeId)}>Fund details <span>↗</span></button>
                        <button className="add-link remove-link" onClick={() => removeWatchlistItem(scheme.schemeId)} disabled={busyScheme === scheme.schemeId}>Remove <span>×</span></button>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </>
          )}
          <footer className="page-footer">
            <span>Fundwise · Mutual fund research, made personal</span>
            <span>Values shown are informational, not investment advice.</span>
          </footer>
        </main>
      </div>
      {toast && <div className="toast" role="status"><span>✓</span>{toast}</div>}
      <FundDetailsModal
        details={schemeDetails}
        history={navHistory}
        loading={historyLoading}
        range={historyRange}
        onRangeChange={changeHistoryRange}
        onClose={() => { setSelectedScheme(null); setSchemeDetails(null); }}
      />
    </div>
  );
}

function categoryLabel(value) {
  return CATEGORIES.find(([key]) => key === value)?.[1] ?? titleCase(value);
}

function titleCase(value = "") {
  return value.toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatDate(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${value}T00:00:00Z`));
}

function ExploreIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.8" /><path d="m16 16 4.2 4.2M8.2 11.1l1.8-2 2 2.7 1.5-1.6" /></svg>;
}

function BookmarkIcon({ filled = false }) {
  return <svg viewBox="0 0 24 24" aria-hidden="true" className={filled ? "icon-filled" : ""}><path d="M6.5 4.8c0-.7.6-1.3 1.3-1.3h8.4c.7 0 1.3.6 1.3 1.3v15l-5.5-3.5-5.5 3.5v-15Z" /></svg>;
}

function SearchIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.8" /><path d="m16 16 4.2 4.2" /></svg>;
}

function ChevronIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg>;
}

function LogoutIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 4H5.8A1.8 1.8 0 0 0 4 5.8v12.4A1.8 1.8 0 0 0 5.8 20H10M14 8l4 4-4 4M8 12h10" /></svg>;
}

function PortfolioIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 19V5M4 19h17M7 15l4-4 3 2 6-7" /><circle cx="20" cy="6" r="1" /></svg>;
}

function RefreshIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 7v5h-5M4 17v-5h5" /><path d="M5.7 9a7 7 0 0 1 11.6-2L20 12M4 12l2.7 5a7 7 0 0 0 11.6-2" /></svg>;
}

export default App;
