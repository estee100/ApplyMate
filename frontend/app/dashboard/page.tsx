"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Navbar } from "@/components/Navbar";
import {
  listApplications,
  deleteApplication,
  type ApplicationStatus,
  type JobApplication,
  getToken,
} from "@/services/api";

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

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    load();
  }, [filter, router]);

  async function load() {
    setLoading(true);
    try {
      const data = await listApplications(filter || undefined);
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
                  ) : (
                    applications.map((app) => (
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
          <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-4">
            {(
              [
                "APPLIED",
                "INTERVIEWING",
                "OFFER",
                "REJECTED",
                "WITHDRAWN",
              ] as ApplicationStatus[]
            ).map((status) => (
              <div
                key={status}
                className="bg-slate-100 rounded-xl p-3 min-h-[200px]"
              >
                <h3 className="font-semibold text-slate-800 text-sm mb-3">
                  {STATUS_LABELS[status]}
                </h3>
                <ul className="space-y-2">
                  {byStatus(status).map((app) => (
                    <li
                      key={app.id}
                      className="bg-white rounded-lg p-3 shadow-sm border border-slate-200 text-sm"
                    >
                      <p className="font-medium text-slate-900">
                        {app.jobTitle}
                      </p>
                      <p className="text-slate-600">{app.companyName}</p>
                      <Link
                        href={`/applications/${app.id}/edit`}
                        className="text-primary text-xs mt-1 inline-block hover:underline"
                      >
                        Edit
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
