const DATABASE_NAME = "fundwise-offline";
const DATABASE_VERSION = 1;
const CACHE_STORE = "responses";
const OUTBOX_STORE = "outbox";
let activeUserId = null;
let cacheDays = 30;

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(CACHE_STORE)) {
        database.createObjectStore(CACHE_STORE, { keyPath: "key" });
      }
      if (!database.objectStoreNames.contains(OUTBOX_STORE)) {
        const outbox = database.createObjectStore(OUTBOX_STORE, { keyPath: "id" });
        outbox.createIndex("userId", "userId");
        outbox.createIndex("createdAt", "createdAt");
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("Offline storage could not be opened."));
  });
}

async function storeTransaction(storeName, mode, action) {
  const database = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(storeName, mode);
    const request = action(transaction.objectStore(storeName));
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("Offline storage operation failed."));
    transaction.oncomplete = () => database.close();
    transaction.onerror = () => reject(transaction.error ?? new Error("Offline storage transaction failed."));
  });
}

export function configureOfflineUser(userId, days = cacheDays) {
  activeUserId = userId == null ? null : String(userId);
  cacheDays = days;
}

export async function cacheResponse(path, payload) {
  if (!activeUserId) return;
  await storeTransaction(CACHE_STORE, "readwrite", (store) => store.put({
    key: `${activeUserId}:${path}`,
    userId: activeUserId,
    path,
    payload,
    savedAt: Date.now(),
  }));
}

export async function readCachedResponse(path) {
  if (!activeUserId) return null;
  const record = await storeTransaction(CACHE_STORE, "readonly", (store) =>
    store.get(`${activeUserId}:${path}`));
  if (!record) return null;
  const expiresAt = record.savedAt + cacheDays * 24 * 60 * 60 * 1000;
  if (Date.now() > expiresAt) {
    await storeTransaction(CACHE_STORE, "readwrite", (store) => store.delete(record.key));
    return null;
  }
  return { payload: record.payload, savedAt: record.savedAt };
}

export async function enqueueMutation(method, path, body = null) {
  if (!activeUserId) throw new Error("Sign in before saving an offline change.");
  const item = {
    id: crypto.randomUUID(),
    userId: activeUserId,
    method,
    path,
    body,
    createdAt: Date.now(),
    failed: false,
    error: null,
  };
  await storeTransaction(OUTBOX_STORE, "readwrite", (store) => store.add(item));
  return item;
}

export async function listPendingMutations() {
  if (!activeUserId) return [];
  const records = await storeTransaction(OUTBOX_STORE, "readonly", (store) =>
    store.index("userId").getAll(activeUserId));
  return records.sort((left, right) => left.createdAt - right.createdAt);
}

export async function discardPendingMutations() {
  const pending = await listPendingMutations();
  await Promise.all(pending.map((item) =>
    storeTransaction(OUTBOX_STORE, "readwrite", (store) => store.delete(item.id))));
}

async function markMutationFailed(item, error) {
  await storeTransaction(OUTBOX_STORE, "readwrite", (store) => store.put({
    ...item,
    failed: true,
    error,
  }));
}

function csrfToken() {
  const cookie = document.cookie.split("; ").find((part) => part.startsWith("XSRF-TOKEN="));
  return cookie ? decodeURIComponent(cookie.slice("XSRF-TOKEN=".length)) : null;
}

export async function synchronizePendingMutations(apiBaseUrl) {
  if (!activeUserId || !navigator.onLine) return { synchronized: 0, pending: 0, blocked: false };
  let synchronized = 0;
  let blocked = false;
  const pending = await listPendingMutations();
  for (const item of pending) {
    if (item.failed) continue;
    try {
      const csrfResponse = await fetch(`${apiBaseUrl}/api/auth/csrf`, {
        credentials: "include",
        headers: { Accept: "application/json" },
      });
      if (!csrfResponse.ok) {
        blocked = csrfResponse.status === 401;
        if (blocked) break;
        await markMutationFailed(item, `Could not retrieve CSRF token (${csrfResponse.status}).`);
        continue;
      }
      const token = csrfToken();
      if (!token) {
        blocked = true;
        break;
      }
      const response = await fetch(`${apiBaseUrl}${item.path}`, {
        method: item.method,
        credentials: "include",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": token,
        },
        ...(item.body == null ? {} : { body: JSON.stringify(item.body) }),
      });
      if (response.status === 401 || response.status === 403) {
        blocked = true;
        break;
      }
      if (!response.ok) {
        const responseBody = await response.json().catch(() => ({}));
        await markMutationFailed(item, responseBody.message ?? `Sync failed (${response.status}).`);
        continue;
      }
      await storeTransaction(OUTBOX_STORE, "readwrite", (store) => store.delete(item.id));
      synchronized += 1;
    } catch {
      break;
    }
  }
  const remaining = await listPendingMutations();
  return { synchronized, pending: remaining.length, blocked };
}

export async function clearUserOfflineData(userId) {
  const id = String(userId);
  const database = await openDatabase();
  await new Promise((resolve, reject) => {
    const transaction = database.transaction([CACHE_STORE, OUTBOX_STORE], "readwrite");
    const cacheStore = transaction.objectStore(CACHE_STORE);
    const outboxStore = transaction.objectStore(OUTBOX_STORE);
    const cacheRequest = cacheStore.openCursor();
    cacheRequest.onsuccess = () => {
      const cursor = cacheRequest.result;
      if (cursor) {
        if (cursor.value.userId === id) cursor.delete();
        cursor.continue();
      }
    };
    const outboxRequest = outboxStore.index("userId").openCursor(IDBKeyRange.only(id));
    outboxRequest.onsuccess = () => {
      const cursor = outboxRequest.result;
      if (cursor) {
        cursor.delete();
        cursor.continue();
      }
    };
    transaction.oncomplete = () => { database.close(); resolve(); };
    transaction.onerror = () => reject(transaction.error ?? new Error("Offline data could not be cleared."));
  });
}
