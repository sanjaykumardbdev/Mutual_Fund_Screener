import {
  cacheResponse,
  clearUserOfflineData,
  configureOfflineUser,
  enqueueMutation,
  listPendingMutations,
  readCachedResponse,
  synchronizePendingMutations,
} from "./offline.js";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class NetworkError extends Error {
  constructor(message) {
    super(message);
    this.name = "NetworkError";
    this.isNetworkError = true;
  }
}

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      credentials: "include",
      headers: {
        Accept: "application/json",
        ...options.headers,
      },
    });
  } catch {
    if ((!options.method || options.method === "GET") && !path.startsWith("/api/auth/")) {
      try {
        const cached = await readCachedResponse(path);
        if (cached) {
          window.dispatchEvent(new CustomEvent("fundwise:offline-cache", {
            detail: { savedAt: cached.savedAt },
          }));
          return cached.payload;
        }
      } catch {
        window.dispatchEvent(new CustomEvent("fundwise:offline-storage-error"));
      }
    }
    throw new NetworkError(
      "The backend is unreachable. Showing cached data when available.",
    );
  }

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("Your session has expired. Please sign in again.");
    }

    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      if (body.message) message = body.message;
    } catch {
      // Some successful or error responses have no JSON body.
    }
    throw new Error(message);
  }

  if (response.status === 204) return null;
  const payload = await response.json();
  if ((!options.method || options.method === "GET") && !path.startsWith("/api/auth/")) {
    try {
      await cacheResponse(path, payload);
    } catch {
      window.dispatchEvent(new CustomEvent("fundwise:offline-storage-error"));
    }
  }
  return payload;
}

async function queueableMutation(method, path, body = null) {
  try {
    const csrf = await csrfHeaders();
    return await request(path, {
      method,
      headers: {
        ...csrf,
        ...(body === null ? {} : { "Content-Type": "application/json" }),
      },
      ...(body === null ? {} : {
        body: JSON.stringify(body),
      }),
    });
  } catch (error) {
    if (!error.isNetworkError) throw error;
    const queued = await enqueueMutation(method, path, body);
    window.dispatchEvent(new CustomEvent("fundwise:outbox-changed", {
      detail: { mutation: queued },
    }));
    return { queuedOffline: true, requestId: queued.id };
  }
}

async function csrfHeaders() {
  await request("/api/auth/csrf");
  const tokenCookie = document.cookie
    .split("; ")
    .find((cookie) => cookie.startsWith("XSRF-TOKEN="));
  if (!tokenCookie) {
    throw new Error("The CSRF cookie was not set. Check the API host and CORS settings.");
  }
  return { "X-XSRF-TOKEN": decodeURIComponent(tokenCookie.split("=").slice(1).join("=")) };
}

export const api = {
  async currentUser() {
    const user = await request("/api/auth/me");
    configureOfflineUser(user.id);
    localStorage.setItem("fundwise-offline-user", JSON.stringify({
      id: user.id,
      username: user.username,
      firstName: user.firstName,
      roles: user.roles,
    }));
    return user;
  },
  offlineIdentity() {
    try {
      const identity = JSON.parse(localStorage.getItem("fundwise-offline-user") ?? "null");
      return identity && identity.id ? identity : null;
    } catch {
      return null;
    }
  },
  configureOfflineUser,
  async clearOfflineSession(userId) {
    await clearUserOfflineData(userId);
    localStorage.removeItem("fundwise-offline-user");
    configureOfflineUser(null);
  },
  pendingMutations: listPendingMutations,
  synchronize: () => synchronizePendingMutations(API_BASE_URL),

  async login(username, password) {
    const headers = await csrfHeaders();
    const user = await request("/api/auth/login", {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    await request("/api/auth/csrf");
    configureOfflineUser(user.id);
    localStorage.setItem("fundwise-offline-user", JSON.stringify({
      id: user.id,
      username: user.username,
      firstName: user.firstName,
      roles: user.roles,
    }));
    return user;
  },

  async logout() {
    const headers = await csrfHeaders();
    await request("/api/auth/logout", { method: "POST", headers });
  },

  fundHouses: () => request("/api/fund-houses"),
  schemes: (filters) => {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== "" && value !== null && value !== undefined) {
        query.set(key, String(value));
      }
    }
    return request(`/api/schemes?${query.toString()}`);
  },
  schemeDetails: (schemeId) => request(`/api/schemes/${schemeId}`),
  navHistory: (schemeId, fromDate) => {
    const query = fromDate ? `?fromDate=${encodeURIComponent(fromDate)}` : "";
    return request(`/api/schemes/${schemeId}/nav${query}`);
  },
  navDashboard: () => request("/api/nav/dashboard"),
  async refreshNav(scope = "ALL", schemeId) {
    const headers = await csrfHeaders();
    return request("/api/nav/refresh", {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify({ scope, ...(schemeId ? { schemeId } : {}) }),
    });
  },
  portfolio: () => request("/api/me/portfolio"),
  investments: () => request("/api/me/investments"),
  async addInvestment(investment) {
    return queueableMutation("POST", "/api/me/investments", {
      ...investment,
      clientRequestId: investment.clientRequestId ?? crypto.randomUUID(),
    });
  },
  sips: () => request("/api/me/sips"),
  async addSip(sip) {
    const headers = await csrfHeaders();
    return request("/api/me/sips", {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify(sip),
    });
  },
  async updateSipStatus(sipId, status) {
    const headers = await csrfHeaders();
    return request(`/api/me/sips/${sipId}/status`, {
      method: "PATCH",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    });
  },
  preferences: () => request("/api/me/preferences"),
  async updatePreferences(preferences) {
    const headers = await csrfHeaders();
    return request("/api/me/preferences", {
      method: "PUT",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify(preferences),
    });
  },
  watchlist: () => request("/api/me/watchlist"),
  async addWatchlist(schemeId) {
    return queueableMutation("POST", `/api/me/watchlist/${schemeId}`);
  },
  async removeWatchlist(schemeId) {
    return queueableMutation("DELETE", `/api/me/watchlist/${schemeId}`);
  },
};
