import type * as T from "./types";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
const TOKEN_KEY = "applymate_token";
const REFRESH_KEY = "applymate_refresh";
const USER_KEY = "applymate_user";

/** An error from the API, with the server's user-facing message and HTTP status. */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
  }

  /** Quota or rate limit: worth telling the user to wait rather than that something broke. */
  get isRateLimited() {
    return this.status === 429;
  }

  /** Someone saved a newer version first (another tab or device). */
  get isConflict() {
    return this.status === 409;
  }
}

// --- Session ------------------------------------------------------------------
// The JWT lives in localStorage. That keeps the API stateless and CORS simple, at the
// cost that any XSS could read it; React escapes all rendered text and the app never
// injects HTML, which is what keeps that risk contained.

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function getStoredUser(): { email: string; name: string | null; avatarVersion?: string | null } | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveSession(auth: T.AuthResponse) {
  localStorage.setItem(TOKEN_KEY, auth.token);
  localStorage.setItem(REFRESH_KEY, auth.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify({ email: auth.email, name: auth.name }));
}

export function clearSession() {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
  } catch {
    // storage unavailable: nothing to clear
  }
}

function redirectToLogin() {
  if (typeof window === "undefined") return;
  const here = window.location.pathname + window.location.search;
  if (!window.location.pathname.startsWith("/login")) {
    window.location.href = `/login?next=${encodeURIComponent(here)}`;
  }
}

// --- Session refresh ----------------------------------------------------------
// Access tokens last 15 minutes. On a 401 the client trades its refresh token for a
// new pair and retries once. Refresh tokens are single-use and rotated, so two tabs
// refreshing at once would collide: the Web Locks API serialises refreshes across
// tabs, and a tab that waited checks whether another tab already refreshed.

let inFlight: Promise<boolean> | null = null;

async function doRefresh(staleAccess: string | null): Promise<boolean> {
  if (getToken() !== staleAccess) return true; // another tab already refreshed
  let refreshToken: string | null = null;
  try {
    refreshToken = localStorage.getItem(REFRESH_KEY);
  } catch {
    return false;
  }
  if (!refreshToken) return false;
  try {
    const res = await fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return getToken() !== staleAccess;
    saveSession((await res.json()) as T.AuthResponse);
    return true;
  } catch {
    return false;
  }
}

function refreshSession(staleAccess: string | null): Promise<boolean> {
  if (!inFlight) {
    const locks = typeof navigator !== "undefined" ? navigator.locks : undefined;
    // `await` flattens the nested promise the Web Locks typings describe.
    const run: Promise<boolean> = locks
      ? (async () => await locks.request("applymate-refresh", () => doRefresh(staleAccess)))()
      : doRefresh(staleAccess);
    inFlight = run.finally(() => {
      inFlight = null;
    });
  }
  return inFlight;
}

// --- Transport ----------------------------------------------------------------

async function request<R>(
  path: string,
  options: RequestInit & { auth?: boolean; retried?: boolean; blob?: boolean } = {},
): Promise<R> {
  const { auth = true, retried = false, blob = false, ...init } = options;
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  const token = auth ? getToken() : null;
  if (token) headers.set("Authorization", `Bearer ${token}`);

  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError("Can't reach the server. Check your connection and try again.", 0);
  }

  if (res.status === 401 && auth && !retried && (await refreshSession(token))) {
    return request<R>(path, { ...options, retried: true });
  }
  if (res.status === 401 && auth) {
    clearSession();
    redirectToLogin();
    throw new ApiError("Your session has ended. Please sign in again.", 401);
  }
  if (!res.ok) {
    let message = res.statusText || "Something went wrong.";
    try {
      const data = await res.json();
      if (typeof data?.error === "string") message = data.error;
    } catch {
      // not JSON: keep the status text
    }
    throw new ApiError(message, res.status);
  }
  if (res.status === 204) return undefined as R;
  if (blob) return (await res.blob()) as R;
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as R;
}

const get = <R,>(path: string) => request<R>(path);
const send = <R,>(method: string, path: string, body?: unknown) =>
  request<R>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) });

function qs(params: Record<string, string | number | boolean | null | undefined>) {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== "" && v !== false) search.set(k, String(v));
  }
  const s = search.toString();
  return s ? `?${s}` : "";
}

// --- Endpoints ------------------------------------------------------------------

export const api = {
  // Auth
  login: (email: string, password: string) =>
    request<T.AuthResponse>("/auth/login", { method: "POST", auth: false, body: JSON.stringify({ email, password }) }),
  register: (email: string, password: string, name?: string) =>
    request<T.AuthResponse>("/auth/register", {
      method: "POST",
      auth: false,
      body: JSON.stringify({ email, password, name }),
    }),
  forgotPassword: (email: string) =>
    request<{ message: string }>("/auth/forgot-password", { method: "POST", auth: false, body: JSON.stringify({ email }) }),
  resetPassword: (token: string, password: string) =>
    request<{ message: string }>("/auth/reset-password", {
      method: "POST",
      auth: false,
      body: JSON.stringify({ token, password }),
    }),
  /** Revokes this device's session on the server, then forgets it locally. */
  logout: async () => {
    let refreshToken: string | null = null;
    try {
      refreshToken = localStorage.getItem(REFRESH_KEY);
    } catch {
      // storage unavailable
    }
    clearSession();
    if (refreshToken) {
      await fetch(`${API_BASE}/auth/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      }).catch(() => undefined);
    }
  },
  changePassword: (currentPassword: string, newPassword: string) =>
    send<T.AuthResponse>("POST", "/auth/change-password", { currentPassword, newPassword }),

  // Profile and resume
  profile: () => get<T.Profile>("/profile"),
  updateProfile: (body: Partial<Pick<T.Profile, "name" | "preferredLocation" | "includeRemote" | "emailReminders">>) =>
    send<T.Profile>("PATCH", "/profile", body),
  deleteAccount: (password: string) => send<void>("DELETE", "/profile", { password }),
  /** The profile picture as an object URL (it needs auth, so it can't be a plain <img src>). */
  avatar: async (version: string) =>
    URL.createObjectURL(await request<Blob>(`/profile/avatar${qs({ v: version })}`, { blob: true })),
  uploadAvatar: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return request<{ avatarUpdatedAt: string }>("/profile/avatar", { method: "POST", body: form });
  },
  deleteAvatar: () => send<void>("DELETE", "/profile/avatar"),
  resumes: () => get<T.ResumeInfo[]>("/resumes"),
  activateResume: (id: number) => send<T.ResumeInfo>("POST", `/resumes/${id}/activate`),
  uploadResume: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return request<T.UploadResponse>("/resumes", { method: "POST", body: form });
  },
  updateSkillProfile: (body: Partial<T.SkillProfile>) => send<T.SkillProfile>("PUT", "/resumes/active/skill-profile", body),
  refreshSkillProfile: () => send<T.SkillProfile>("POST", "/resumes/active/skill-profile/refresh"),
  masterResume: () => get<T.MasterResume>("/resumes/active/master"),
  saveMasterResume: (content: T.ResumeContent, version?: number) =>
    send<T.MasterResume>("PUT", `/resumes/active/master${qs({ version })}`, content),
  fillMasterResume: () => send<T.MasterResume>("POST", "/resumes/active/master/fill"),

  // Jobs
  feed: (p: { q?: string; location?: string; jobType?: T.JobType | ""; remoteOnly?: boolean; postedWithinDays?: number | ""; page?: number }) =>
    get<T.FeedResponse>(`/jobs/feed${qs(p)}`),
  recommended: () => get<T.Recommendations>("/jobs/recommended"),
  myJobs: () => get<T.JobCard[]>("/jobs/mine"),
  savedJobs: () => get<T.JobCard[]>("/jobs/saved"),
  job: (id: number) => get<T.JobDetail>(`/jobs/${id}`),
  pasteLink: (url: string) => send<T.JobDetail>("POST", "/jobs/paste-link", { url }),
  pasteText: (body: { title: string; company: string; location?: string; url?: string; description: string }) =>
    send<T.JobDetail>("POST", "/jobs/paste-text", body),
  removeMyJob: (id: number) => send<void>("DELETE", `/jobs/${id}/mine`),
  saveJob: (id: number) => send<void>("PUT", `/jobs/${id}/saved`),
  unsaveJob: (id: number) => send<void>("DELETE", `/jobs/${id}/saved`),
  match: (id: number, refresh = false) => send<T.MatchResponse>("POST", `/jobs/${id}/match${qs({ refresh })}`),

  // Documents
  resumeFromMaster: (jobId: number) => send<T.GeneratedDocument>("POST", `/jobs/${jobId}/documents/from-master`),
  tailorResume: (jobId: number, instructions?: string) =>
    send<T.GeneratedDocument>("POST", `/jobs/${jobId}/documents/resume`, { instructions }),
  coverLetter: (jobId: number, instructions?: string) =>
    send<T.GeneratedDocument>("POST", `/jobs/${jobId}/documents/cover-letter`, { instructions }),
  document: (id: number) => get<T.GeneratedDocument>(`/documents/${id}`),
  saveDocument: (id: number, content: T.ResumeContent | T.CoverLetterContent, version?: number) =>
    send<T.GeneratedDocument>("PUT", `/documents/${id}`, { content, version }),
  resetDocument: (id: number) => send<T.GeneratedDocument>("POST", `/documents/${id}/reset`),
  deleteDocument: (id: number) => send<void>("DELETE", `/documents/${id}`),

  // Applications
  applications: (status?: T.ApplicationStatus) => get<T.Application[]>(`/applications${qs({ status })}`),
  application: (id: number) => get<T.Application>(`/applications/${id}`),
  stats: () => get<T.ApplicationStats>("/applications/stats"),
  trackJob: (jobId: number) => send<T.Application>("POST", "/applications", { jobId }),
  logApplication: (body: {
    companyName: string;
    jobTitle: string;
    location?: string;
    jobPostingUrl?: string;
    status?: T.ApplicationStatus;
    dateApplied?: string;
    notes?: string;
  }) => send<T.Application>("POST", "/applications", body),
  updateApplication: (
    id: number,
    body: Partial<{
      status: T.ApplicationStatus;
      note: string;
      jobTitle: string;
      companyName: string;
      location: string;
      jobPostingUrl: string;
      dateApplied: string;
      notes: string;
      nextAction: string;
      nextActionDate: string;
      clearNextAction: boolean;
      version: number;
    }>,
  ) => send<T.Application>("PATCH", `/applications/${id}`, body),
  deleteApplication: (id: number) => send<void>("DELETE", `/applications/${id}`),
  events: (id: number) => get<T.ApplicationEvent[]>(`/applications/${id}/events`),
  communications: (id: number) => get<T.Communication[]>(`/applications/${id}/communications`),
  addCommunication: (id: number, body: Omit<T.Communication, "id" | "applicationId">) =>
    send<T.Communication>("POST", `/applications/${id}/communications`, body),
  deleteCommunication: (id: number) => send<void>("DELETE", `/communications/${id}`),
  interviewPrep: (id: number) => get<T.InterviewPrep | null>(`/applications/${id}/interview-prep`),
  generateInterviewPrep: (id: number, refresh = false) =>
    send<T.InterviewPrep>("POST", `/applications/${id}/interview-prep${qs({ refresh })}`),

  // Views
  search: (q: string) => get<T.SearchResults>(`/search${qs({ q })}`),
  history: () => get<T.HistoryEntry[]>("/history"),
  calendar: (from: string, to: string) => get<T.CalendarEvent[]>(`/calendar${qs({ from, to })}`),
  analytics: () => get<T.AnalyticsSummary>("/analytics/summary"),
};
