const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

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
    throw new Error(
      "Cannot reach the backend. Start Oracle and the Spring Boot API, then try again.",
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
  return response.json();
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
  currentUser: () => request("/api/auth/me"),

  async login(username, password) {
    const headers = await csrfHeaders();
    const user = await request("/api/auth/login", {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    await request("/api/auth/csrf");
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
  watchlist: () => request("/api/me/watchlist"),
  async addWatchlist(schemeId) {
    const headers = await csrfHeaders();
    return request(`/api/me/watchlist/${schemeId}`, { method: "POST", headers });
  },
  async removeWatchlist(schemeId) {
    const headers = await csrfHeaders();
    return request(`/api/me/watchlist/${schemeId}`, { method: "DELETE", headers });
  },
};
