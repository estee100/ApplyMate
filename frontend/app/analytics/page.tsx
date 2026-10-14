"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Navbar } from "@/components/Navbar";
import {
  getAnalyticsSummary,
  getToken,
  type AnalyticsSummary,
  type ApplicationStatus,
} from "@/services/api";

const STATUS_LABELS: Record<string, string> = {
  APPLIED: "Applied",
  INTERVIEWING: "Interviewing",
  OFFER: "Offer",
  REJECTED: "Rejected",
  WITHDRAWN: "Withdrawn",
};

const STATUS_ORDER: ApplicationStatus[] = [
  "APPLIED",
  "INTERVIEWING",
  "OFFER",
  "REJECTED",
  "WITHDRAWN",
];

const STATUS_COLORS: Record<string, string> = {
  APPLIED: "bg-blue-500",
  INTERVIEWING: "bg-amber-500",
  OFFER: "bg-emerald-500",
  REJECTED: "bg-red-500",
  WITHDRAWN: "bg-slate-400",
};

export default function AnalyticsPage() {
  const router = useRouter();
  const [data, setData] = useState<AnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    (async () => {
      try {
        const summary = await getAnalyticsSummary();
        setData(summary);
      } catch (e) {
        const msg =
          e instanceof Error && e.message && !e.message.startsWith("{")
            ? e.message
            : "Could not load analytics.";
        setError(msg);
      } finally {
        setLoading(false);
      }
    })();
  }, [router]);

  if (!getToken() && typeof window !== "undefined") return null;

  const maxStatusCount = data
    ? Math.max(
        1,
        ...STATUS_ORDER.map((s) => data.byStatus[s] ?? 0)
      )
    : 1;

  const maxMonthCount = data?.applicationsByMonth.length
    ? Math.max(
        1,
        ...data.applicationsByMonth.map((m) => m.count)
      )
    : 1;

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar />
      <div className="max-w-6xl mx-auto px-4 py-8">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">Analytics</h1>
            <p className="text-slate-600 text-sm mt-1">
              Overview of your application pipeline
            </p>
          </div>
          <Link
            href="/dashboard"
            className="inline-flex justify-center px-4 py-2 rounded-lg border border-slate-300 bg-white text-slate-700 font-medium hover:bg-slate-50 text-sm"
          >
            ← Back to dashboard
          </Link>
        </div>

        {loading && (
          <p className="text-slate-500">Loading analytics…</p>
        )}

        {error && (
          <div className="rounded-xl border border-red-100 bg-red-50 text-red-700 px-4 py-3 text-sm">
            {error}
          </div>
        )}

        {data && !loading && (
          <div className="space-y-8">
            {/* Total */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              <div className="rounded-2xl bg-white border border-slate-200 shadow-sm p-6">
                <p className="text-sm font-medium text-slate-500">
                  Total applications
                </p>
                <p className="text-3xl font-bold text-slate-900 mt-1">
                  {data.totalApplications}
                </p>
              </div>
              <div className="rounded-2xl bg-white border border-slate-200 shadow-sm p-6 sm:col-span-2">
                <p className="text-sm font-medium text-slate-500 mb-3">
                  Pipeline mix
                </p>
                <div className="flex h-8 rounded-lg overflow-hidden">
                  {STATUS_ORDER.map((status) => {
                    const count = data.byStatus[status] ?? 0;
                    if (data.totalApplications === 0) return null;
                    const pct =
                      (count / data.totalApplications) * 100;
                    if (pct <= 0) return null;
                    return (
                      <div
                        key={status}
                        className={`${STATUS_COLORS[status]} min-w-0 transition-all`}
                        style={{ width: `${pct}%` }}
                        title={`${STATUS_LABELS[status]}: ${count}`}
                      />
                    );
                  })}
                </div>
                <div className="flex flex-wrap gap-x-4 gap-y-1 mt-3 text-xs text-slate-600">
                  {STATUS_ORDER.map((status) => {
                    const count = data.byStatus[status] ?? 0;
                    if (count === 0) return null;
                    return (
                      <span key={status} className="flex items-center gap-1">
                        <span
                          className={`w-2 h-2 rounded-full ${STATUS_COLORS[status]}`}
                        />
                        {STATUS_LABELS[status]} ({count})
                      </span>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* By status bars */}
            <div className="rounded-2xl bg-white border border-slate-200 shadow-sm p-6">
              <h2 className="text-lg font-semibold text-slate-900 mb-4">
                By status
              </h2>
              <div className="space-y-4">
                {STATUS_ORDER.map((status) => {
                  const count = data.byStatus[status] ?? 0;
                  const pct = (count / maxStatusCount) * 100;
                  return (
                    <div key={status}>
                      <div className="flex justify-between text-sm mb-1">
                        <span className="font-medium text-slate-700">
                          {STATUS_LABELS[status]}
                        </span>
                        <span className="text-slate-500">{count}</span>
                      </div>
                      <div className="h-3 rounded-full bg-slate-100 overflow-hidden">
                        <div
                          className={`h-full rounded-full ${STATUS_COLORS[status]} transition-all`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Applications over time */}
            <div className="rounded-2xl bg-white border border-slate-200 shadow-sm p-6">
              <h2 className="text-lg font-semibold text-slate-900 mb-4">
                Applications by month
              </h2>
              {data.applicationsByMonth.length === 0 ? (
                <p className="text-slate-500 text-sm">
                  No data yet. Add applications with applied dates to see
                  trends.
                </p>
              ) : (
                <div className="flex items-end gap-2 min-h-[180px] pt-4 border-b border-slate-200">
                  {data.applicationsByMonth.map(({ month, count }) => {
                    const pct = (count / maxMonthCount) * 100;
                    const barHeightPx = Math.round((pct / 100) * 140);
                    const heightPx = Math.max(barHeightPx, count > 0 ? 12 : 4);
                    return (
                      <div
                        key={month}
                        className="flex-1 flex flex-col items-center gap-2 min-w-0 h-[160px] justify-end"
                      >
                        <div
                          className="w-full max-w-[48px] mx-auto rounded-t-lg bg-violet-500/90 hover:bg-violet-600 transition-colors"
                          style={{ height: heightPx }}
                          title={`${month}: ${count}`}
                        />
                        <span className="text-[10px] text-slate-500 truncate w-full text-center">
                          {month.slice(5)}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
