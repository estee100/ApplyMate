"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  createApplication,
  updateApplication,
  type ApplicationStatus,
  type JobApplication,
} from "@/services/api";

const STATUSES: ApplicationStatus[] = [
  "APPLIED",
  "INTERVIEWING",
  "OFFER",
  "REJECTED",
  "WITHDRAWN",
];

interface Props {
  initial?: JobApplication | null;
}

export function ApplicationForm({ initial }: Props) {
  const router = useRouter();
  const isEdit = !!initial;
  const [jobTitle, setJobTitle] = useState(initial?.jobTitle ?? "");
  const [companyName, setCompanyName] = useState(initial?.companyName ?? "");
  const [location, setLocation] = useState(initial?.location ?? "");
  const [jobPostingUrl, setJobPostingUrl] = useState(
    initial?.jobPostingUrl ?? ""
  );
  const [status, setStatus] = useState<ApplicationStatus>(
    initial?.status ?? "APPLIED"
  );
  const [dateApplied, setDateApplied] = useState(
    initial?.dateApplied ?? new Date().toISOString().slice(0, 10)
  );
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const body = {
        jobTitle,
        companyName,
        location: location || null,
        jobPostingUrl: jobPostingUrl || null,
        status,
        dateApplied,
        notes: notes || null,
      };
      if (isEdit && initial) {
        await updateApplication(initial.id, body);
      } else {
        await createApplication(body);
      }
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 space-y-4"
    >
      {error && (
        <div className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">
          {error}
        </div>
      )}
      <div>
        <label className="block text-sm font-medium text-slate-700 mb-1">
          Job title *
        </label>
        <input
          required
          value={jobTitle}
          onChange={(e) => setJobTitle(e.target.value)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:ring-2 focus:ring-primary outline-none"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-slate-700 mb-1">
          Company name *
        </label>
        <input
          required
          value={companyName}
          onChange={(e) => setCompanyName(e.target.value)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:ring-2 focus:ring-primary outline-none"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-slate-700 mb-1">
          Location
        </label>
        <input
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:ring-2 focus:ring-primary outline-none"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-slate-700 mb-1">
          Job posting URL
        </label>
        <input
          type="url"
          value={jobPostingUrl}
          onChange={(e) => setJobPostingUrl(e.target.value)}
          placeholder="https://..."
          className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:ring-2 focus:ring-primary outline-none"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-slate-700 mb-1">
          Status *
        </label>
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value as ApplicationStatus)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:ring-2 focus:ring-primary outline-none"
        >
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-sm font-medium text-slate-700 mb-1">
          Date applied *
        </label>
        <input
          type="date"
          required
          value={dateApplied}
          onChange={(e) => setDateApplied(e.target.value)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:ring-2 focus:ring-primary outline-none"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-slate-700 mb-1">
          Notes
        </label>
        <textarea
          rows={4}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:ring-2 focus:ring-primary outline-none"
        />
      </div>
      <div className="flex gap-3 pt-2">
        <button
          type="submit"
          disabled={loading}
          className="px-5 py-2.5 rounded-lg bg-primary text-white font-medium hover:bg-primary-dark disabled:opacity-50"
        >
          {loading ? "Saving…" : isEdit ? "Update" : "Create"}
        </button>
        <button
          type="button"
          onClick={() => router.back()}
          className="px-5 py-2.5 rounded-lg border border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}
