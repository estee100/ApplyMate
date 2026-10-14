import type { ApplicationStatus, CommunicationKind, JobType } from "./types";

/** Join class names, skipping falsy ones. */
export function cn(...classes: (string | false | null | undefined)[]) {
  return classes.filter(Boolean).join(" ");
}

// --- Pipeline stages: one source of labels and colours for every page ----------

export const STAGES: ApplicationStatus[] = [
  "APPLIED",
  "ONLINE_ASSESSMENT",
  "INTERVIEWING",
  "OFFER",
  "REJECTED",
  "WITHDRAWN",
];

export const STAGE_LABEL: Record<ApplicationStatus, string> = {
  APPLIED: "Applied",
  ONLINE_ASSESSMENT: "Assessment",
  INTERVIEWING: "Interviewing",
  OFFER: "Offer",
  REJECTED: "Rejected",
  WITHDRAWN: "Withdrawn",
};

/** Tailwind classes per stage: dot, soft badge, and a solid bar colour for charts. */
export const STAGE_STYLE: Record<ApplicationStatus, { dot: string; badge: string; bar: string }> = {
  APPLIED: { dot: "bg-sky-500", badge: "bg-sky-500/10 text-sky-700 dark:text-sky-300 ring-sky-500/20", bar: "bg-sky-500" },
  ONLINE_ASSESSMENT: {
    dot: "bg-violet-500",
    badge: "bg-violet-500/10 text-violet-700 dark:text-violet-300 ring-violet-500/20",
    bar: "bg-violet-500",
  },
  INTERVIEWING: {
    dot: "bg-amber-500",
    badge: "bg-amber-500/10 text-amber-700 dark:text-amber-300 ring-amber-500/20",
    bar: "bg-amber-500",
  },
  OFFER: {
    dot: "bg-emerald-500",
    badge: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 ring-emerald-500/20",
    bar: "bg-emerald-500",
  },
  REJECTED: { dot: "bg-rose-500", badge: "bg-rose-500/10 text-rose-700 dark:text-rose-300 ring-rose-500/20", bar: "bg-rose-500" },
  WITHDRAWN: { dot: "bg-zinc-400", badge: "bg-zinc-500/10 text-zinc-600 dark:text-zinc-400 ring-zinc-500/20", bar: "bg-zinc-400" },
};

export const isOpenStage = (s: ApplicationStatus) => s === "APPLIED" || s === "ONLINE_ASSESSMENT" || s === "INTERVIEWING";

export const JOB_TYPE_LABEL: Record<JobType, string> = {
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  CONTRACT: "Contract",
  INTERNSHIP: "Internship",
};

export const COMMUNICATION_LABEL: Record<CommunicationKind, string> = {
  EMAIL: "Email",
  CALL: "Call",
  MEETING: "Meeting",
  MESSAGE: "Message",
  OTHER: "Other",
};

/** How to read a match score. Deliberately not a probability of being hired. */
export function matchBand(pct: number) {
  if (pct >= 80) return { label: "Strong match", tone: "text-success", ring: "stroke-success" };
  if (pct >= 60) return { label: "Good match", tone: "text-primary", ring: "stroke-primary" };
  if (pct >= 40) return { label: "Partial match", tone: "text-warning", ring: "stroke-warning" };
  return { label: "Stretch", tone: "text-danger", ring: "stroke-danger" };
}

// --- Dates ------------------------------------------------------------------------

/** "2026-10-13" as a local date, without the UTC shift `new Date(str)` applies. */
export function parseDay(day: string) {
  const [y, m, d] = day.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function toDay(date: Date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export const today = () => toDay(new Date());

export function formatDay(day: string | null | undefined, opts: Intl.DateTimeFormatOptions = { month: "short", day: "numeric" }) {
  if (!day) return "";
  const date = day.length === 10 ? parseDay(day) : new Date(day);
  const sameYear = date.getFullYear() === new Date().getFullYear();
  return date.toLocaleDateString(undefined, sameYear ? opts : { ...opts, year: "numeric" });
}

export function relativeTime(value: string | null | undefined) {
  if (!value) return "";
  // Date-only values (posted dates, applied dates) are whole days: compare calendar
  // days, and never say "in 6 minutes" because a source dated it in UTC.
  if (value.length === 10) {
    const days = Math.round((parseDay(today()).getTime() - parseDay(value).getTime()) / 86400000);
    if (days <= 0) return "today";
    if (days === 1) return "yesterday";
    if (days < 30) return `${days} days ago`;
    const months = Math.round(days / 30);
    return months < 12 ? `${months} month${months === 1 ? "" : "s"} ago` : `${Math.round(days / 365)} year${days < 730 ? "" : "s"} ago`;
  }
  const date = new Date(value);
  const seconds = Math.round((date.getTime() - Date.now()) / 1000);
  const abs = Math.abs(seconds);
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: "auto" });
  if (abs < 60) return "just now";
  if (abs < 3600) return rtf.format(Math.round(seconds / 60), "minute");
  if (abs < 86400) return rtf.format(Math.round(seconds / 3600), "hour");
  if (abs < 86400 * 30) return rtf.format(Math.round(seconds / 86400), "day");
  if (abs < 86400 * 365) return rtf.format(Math.round(seconds / (86400 * 30)), "month");
  return rtf.format(Math.round(seconds / (86400 * 365)), "year");
}

export function greeting() {
  const h = new Date().getHours();
  if (h < 12) return "Good morning";
  if (h < 18) return "Good afternoon";
  return "Good evening";
}

/** Only http(s) links are ever rendered as an href; the backend enforces this too. */
export function safeHref(url: string | null | undefined) {
  if (!url) return null;
  return /^https?:\/\//i.test(url) ? url : null;
}

export function errorMessage(e: unknown, fallback = "Something went wrong.") {
  return e instanceof Error && e.message ? e.message : fallback;
}

/** Only same-site paths: "//evil.com" or "https://evil.com" would make ?next= an open redirect. */
export function safeNext(next: string | null) {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : "/dashboard";
}
