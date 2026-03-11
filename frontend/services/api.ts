const API_BASE =
  typeof window !== "undefined"
    ? process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api"
    : process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

export type ApplicationStatus =
  | "APPLIED"
  | "INTERVIEWING"
  | "OFFER"
  | "REJECTED"
  | "WITHDRAWN";

export interface JobApplication {
  id: number;
  jobTitle: string;
  companyName: string;
  location: string | null;
  jobPostingUrl: string | null;
  status: ApplicationStatus;
  dateApplied: string;
  notes: string | null;
}

export interface AuthResponse {
  token: string;
  email: string;
}

function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("applymate_token");
}

export function setToken(token: string) {
  localStorage.setItem("applymate_token", token);
}

export function clearToken() {
  localStorage.removeItem("applymate_token");
}

async function fetchApi<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  const token = getToken();
  if (token) {
    (headers as Record<string, string>)["Authorization"] = `Bearer ${token}`;
  }
  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (res.status === 401) {
    clearToken();
    if (typeof window !== "undefined") window.location.href = "/login";
    throw new Error("Unauthorized");
  }
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || res.statusText);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export async function register(
  email: string,
  password: string
): Promise<AuthResponse> {
  return fetchApi<AuthResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export async function login(
  email: string,
  password: string
): Promise<AuthResponse> {
  return fetchApi<AuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export async function listApplications(
  status?: ApplicationStatus | ""
): Promise<JobApplication[]> {
  const q = status ? `?status=${encodeURIComponent(status)}` : "";
  return fetchApi<JobApplication[]>(`/applications${q}`);
}

export async function getApplication(id: number): Promise<JobApplication> {
  return fetchApi<JobApplication>(`/applications/${id}`);
}

export async function createApplication(body: {
  jobTitle: string;
  companyName: string;
  location?: string | null;
  jobPostingUrl?: string | null;
  status: ApplicationStatus;
  dateApplied: string;
  notes?: string | null;
}): Promise<JobApplication> {
  return fetchApi<JobApplication>("/applications", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateApplication(
  id: number,
  body: {
    jobTitle: string;
    companyName: string;
    location?: string | null;
    jobPostingUrl?: string | null;
    status: ApplicationStatus;
    dateApplied: string;
    notes?: string | null;
  }
): Promise<JobApplication> {
  return fetchApi<JobApplication>(`/applications/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function deleteApplication(id: number): Promise<void> {
  return fetchApi<void>(`/applications/${id}`, { method: "DELETE" });
}
