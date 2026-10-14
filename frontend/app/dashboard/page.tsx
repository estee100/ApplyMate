"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Navbar } from "@/components/Navbar";
import {
  listApplications,
  deleteApplication,
  updateApplication,
  type ApplicationStatus,
  type JobApplication,
  getToken,
} from "@/services/api";

const KANBAN_STATUSES: ApplicationStatus[] = [
  "APPLIED",
  "INTERVIEWING",
  "OFFER",
  "REJECTED",
  "WITHDRAWN",
];

function appToUpdateBody(app: JobApplication, status: ApplicationStatus) {
  return {
    jobTitle: app.jobTitle,
    companyName: app.companyName,
    location: app.location,
    jobPostingUrl: app.jobPostingUrl,
    status,
    dateApplied: app.dateApplied,
    notes: app.notes,
  };
}

const STATUSES: (ApplicationStatus | "")[] = [
  "",
  "APPLIED",
  "INTERVIEWING",
  "OFFER",
  "REJECTED",
  "WITHDRAWN",
];

const STATUS_LABELS: Record<string, string> = {
  "": "All",
  APPLIED: "Applied",
  INTERVIEWING: "Interviewing",
  OFFER: "Offer",
  REJECTED: "Rejected",
  WITHDRAWN: "Withdrawn",
};

function statusBadgeClass(status: ApplicationStatus) {
  const map: Record<ApplicationStatus, string> = {
    APPLIED: "bg-blue-100 text-blue-800",
    INTERVIEWING: "bg-amber-100 text-amber-800",
    OFFER: "bg-green-100 text-green-800",
    REJECTED: "bg-red-100 text-red-800",
    WITHDRAWN: "bg-slate-100 text-slate-700",
  };
  return map[status] || "bg-slate-100 text-slate-700";
}

export default function DashboardPage() {
  const router = useRouter();
  const [applications, setApplications] = useState<JobApplication[]>([]);
  const [filter, setFilter] = useState<ApplicationStatus | "">("");
  const [loading, setLoading] = useState(true);
  const [view, setView] = useState<"table" | "kanban">("table");
  const [draggingId, setDraggingId] = useState<number | null>(null);
  const [dropTarget, setDropTarget] = useState<ApplicationStatus | null>(null);
  const [tableSearch, setTableSearch] = useState("");

  const filteredTableApplications = useMemo(() => {
    const q = tableSearch.trim().toLowerCase();
    if (!q) return applications;
    return applications.filter((app) => {
      const haystack = [
        app.jobTitle,
        app.companyName,
        app.location,
        app.notes,
        STATUS_LABELS[app.status],
        app.dateApplied,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return haystack.includes(q);
    });
  }, [applications, tableSearch]);

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    load();
  }, [filter, view, router]);

  // Safety net: if drag ends without hitting a column onDrop, still clear visual state
  useEffect(() => {
    if (view !== "kanban") return;
    const clear = () => {
      setDraggingId(null);
      setDropTarget(null);
    };
    window.addEventListener("dragend", clear);
    return () => window.removeEventListener("dragend", clear);
  }, [view]);

  async function load() {
    setLoading(true);
    try {
      // Kanban needs all columns populated — always fetch full list when in kanban view
      const data = await listApplications(
        view === "kanban" ? undefined : filter || undefined
      );
      setApplications(data);
    } catch {
      setApplications([]);
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(id: number) {
    if (!confirm("Delete this application?")) return;
    try {
      await deleteApplication(id);
      setApplications((prev) => prev.filter((a) => a.id !== id));
    } catch {
      alert("Failed to delete");
    }
  }

  const byStatus = (status: ApplicationStatus) =>
    applications.filter((a) => a.status === status);

  async function handleDropOnColumn(
    targetStatus: ApplicationStatus,
    e: React.DragEvent
  ) {
    e.preventDefault();
    setDropTarget(null);
    // onDragEnd does not always fire on the source node after a successful drop —
    // clear drag state here so cards never stay translucent.
    setDraggingId(null);
    const idStr = e.dataTransfer.getData("applicationId");
    const id = parseInt(idStr, 10);
    if (Number.isNaN(id)) return;
    const app = applications.find((a) => a.id === id);
    if (!app || app.status === targetStatus) return;
    const prev = applications;
    setApplications((list) =>
      list.map((a) =>
        a.id === id ? { ...a, status: targetStatus } : a
      )
    );
    try {
      await updateApplication(id, appToUpdateBody(app, targetStatus));
    } catch {
      setApplications(prev);
      alert("Could not update status. Try again.");
    }
  }

  if (!getToken() && typeof window !== "undefined") return null;

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar />
      <div className="max-w-6xl mx-auto px-4 py-8">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
          <h1 className="text-2xl font-bold text-slate-900">
            Your applications
          </h1>
          <Link
            href="/applications/new"
            className="inline-flex justify-center px-4 py-2 rounded-lg bg-primary text-white font-medium hover:bg-primary-dark"
          >
            + New application
          </Link>
        </div>

        <div className="flex flex-wrap items-center gap-4 mb-6">
          <label className="text-sm font-medium text-slate-600">Filter:</label>
          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value as ApplicationStatus | "")}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm bg-white"
          >
            {STATUSES.map((s) => (
              <option key={s || "all"} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
          <div className="flex rounded-lg border border-slate-300 overflow-hidden">
            <button
              type="button"
              onClick={() => setView("table")}
              className={`px-3 py-2 text-sm ${
                view === "table"
                  ? "bg-primary text-white"
                  : "bg-white text-slate-600"
              }`}
            >
              Table
            </button>
            <button
              type="button"
              onClick={() => setView("kanban")}
              className={`px-3 py-2 text-sm ${
                view === "kanban"
                  ? "bg-primary text-white"
                  : "bg-white text-slate-600"
              }`}
            >
              Kanban
            </button>
          </div>
          {view === "table" && (
            <div className="w-full sm:w-auto sm:min-w-[240px] sm:flex-1 sm:max-w-md">
              <label htmlFor="table-search" className="sr-only">
                Search applications
              </label>
              <div className="relative">
                <span
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none"
                  aria-hidden
                >
                  <svg
                    className="w-4 h-4"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth={2}
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                    />
                  </svg>
                </span>
                <input
                  id="table-search"
                  type="search"
                  value={tableSearch}
                  onChange={(e) => setTableSearch(e.target.value)}
                  placeholder="Search job, company, location…"
                  className="w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 py-2 text-sm placeholder:text-slate-400"
                  autoComplete="off"
                />
              </div>
            </div>
          )}
        </div>

        {loading ? (
          <p className="text-slate-500">Loading…</p>
        ) : view === "table" ? (
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="px-4 py-3 font-semibold text-slate-700">
                      Job
                    </th>
                    <th className="px-4 py-3 font-semibold text-slate-700">
                      Company
                    </th>
                    <th className="px-4 py-3 font-semibold text-slate-700">
                      Location
                    </th>
                    <th className="px-4 py-3 font-semibold text-slate-700">
                      Status
                    </th>
                    <th className="px-4 py-3 font-semibold text-slate-700">
                      Applied
                    </th>
                    <th className="px-4 py-3 font-semibold text-slate-700">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {applications.length === 0 ? (
                    <tr>
                      <td
                        colSpan={6}
                        className="px-4 py-8 text-center text-slate-500"
                      >
                        No applications yet.{" "}
                        <Link
                          href="/applications/new"
                          className="text-primary font-medium hover:underline"
                        >
                          Create one
                        </Link>
                      </td>
                    </tr>
                  ) : filteredTableApplications.length === 0 ? (
                    <tr>
                      <td
                        colSpan={6}
                        className="px-4 py-8 text-center text-slate-500"
                      >
                        No applications match &ldquo;{tableSearch.trim()}&rdquo;.{" "}
                        <button
                          type="button"
                          className="text-primary font-medium hover:underline"
                          onClick={() => setTableSearch("")}
                        >
                          Clear search
                        </button>
                      </td>
                    </tr>
                  ) : (
                    filteredTableApplications.map((app) => (
                      <tr
                        key={app.id}
                        className="border-b border-slate-100 hover:bg-slate-50"
                      >
                        <td className="px-4 py-3">
                          {app.jobPostingUrl ? (
                            <a
                              href={app.jobPostingUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-primary hover:underline font-medium"
                            >
                              {app.jobTitle}
                            </a>
                          ) : (
                            <span className="font-medium text-slate-900">
                              {app.jobTitle}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-slate-700">
                          {app.companyName}
                        </td>
                        <td className="px-4 py-3 text-slate-600">
                          {app.location || "—"}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${statusBadgeClass(
                              app.status
                            )}`}
                          >
                            {STATUS_LABELS[app.status]}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-slate-600">
                          {app.dateApplied}
                        </td>
                        <td className="px-4 py-3">
                          <Link
                            href={`/applications/${app.id}/edit`}
                            className="text-primary text-sm font-medium mr-3 hover:underline"
                          >
                            Edit
                          </Link>
                          <button
                            type="button"
                            onClick={() => handleDelete(app.id)}
                            className="text-red-600 text-sm font-medium hover:underline"
                          >
                            Delete
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          <>
            {filter !== "" && (
              <p className="text-sm text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2 mb-4">
                Filter is ignored in Kanban — showing all applications. Switch
                to Table to filter.
              </p>
            )}
            <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-4">
              {KANBAN_STATUSES.map((status) => (
                <div
                  key={status}
                  className={`rounded-xl p-3 min-h-[240px] transition-colors ${
                    dropTarget === status
                      ? "bg-violet-100 ring-2 ring-violet-400 ring-inset"
                      : "bg-slate-100"
                  }`}
                  onDragOver={(e) => {
                    e.preventDefault();
                    e.dataTransfer.dropEffect = "move";
                    setDropTarget(status);
                  }}
                  onDrop={(e) => handleDropOnColumn(status, e)}
                >
                  <h3 className="font-semibold text-slate-800 text-sm mb-3">
                    {STATUS_LABELS[status]}
                    <span className="ml-1 font-normal text-slate-500">
                      ({byStatus(status).length})
                    </span>
                  </h3>
                  <ul className="space-y-2 min-h-[120px]">
                    {byStatus(status).map((app) => (
                      <li
                        key={app.id}
                        draggable
                        onDragStart={(e) => {
                          e.dataTransfer.setData(
                            "applicationId",
                            String(app.id)
                          );
                          e.dataTransfer.effectAllowed = "move";
                          setDraggingId(app.id);
                        }}
                        onDragEnd={() => {
                          setDraggingId(null);
                          setDropTarget(null);
                        }}
                        className={`bg-white rounded-lg p-3 shadow-sm border text-sm cursor-grab active:cursor-grabbing select-none transition-shadow ${
                          draggingId === app.id
                            ? "border-violet-400 ring-2 ring-violet-400/50 shadow-md"
                            : "border-slate-200 hover:border-slate-300"
                        }`}
                      >
                        <p className="font-medium text-slate-900 pr-6">
                          {app.jobTitle}
                        </p>
                        <p className="text-slate-600">{app.companyName}</p>
                        <div className="mt-2 flex items-center gap-2">
                          <span
                            className="text-slate-400"
                            aria-hidden
                            title="Drag to move"
                          >
                            <svg
                              className="w-4 h-4"
                              fill="currentColor"
                              viewBox="0 0 24 24"
                            >
                              <path d="M8 6a2 2 0 1 1-4 0 2 2 0 0 1 4 0zm0 6a2 2 0 1 1-4 0 2 2 0 0 1 4 0zm0 6a2 2 0 1 1-4 0 2 2 0 0 1 4 0zm6-12a2 2 0 1 1-4 0 2 2 0 0 1 4 0zm0 6a2 2 0 1 1-4 0 2 2 0 0 1 4 0zm0 6a2 2 0 1 1-4 0 2 2 0 0 1 4 0z" />
                            </svg>
                          </span>
                          <Link
                            href={`/applications/${app.id}/edit`}
                            className="text-primary text-xs font-medium hover:underline"
                            onClick={(e) => e.stopPropagation()}
                            onDragStart={(e) => e.preventDefault()}
                          >
                            Edit
                          </Link>
                        </div>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
            <p className="text-xs text-slate-500 mt-4">
              Drag a card onto another column to change its status.
            </p>
          </>
        )}
      </div>
    </div>
  );
}
