"use client";

import { useEffect, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { Navbar } from "@/components/Navbar";
import { getApplication, getToken } from "@/services/api";
import { ApplicationForm } from "@/components/ApplicationForm";
import type { JobApplication } from "@/services/api";

export default function EditApplicationPage() {
  const router = useRouter();
  const params = useParams();
  const id = Number(params.id);
  const [app, setApp] = useState<JobApplication | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    if (!id) return;
    getApplication(id)
      .then(setApp)
      .catch(() => setError("Application not found"));
  }, [id, router]);

  if (typeof window !== "undefined" && !getToken()) return null;
  if (error) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Navbar />
        <div className="max-w-2xl mx-auto px-4 py-8">
          <p className="text-red-600">{error}</p>
        </div>
      </div>
    );
  }
  if (!app) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Navbar />
        <div className="max-w-2xl mx-auto px-4 py-8">
          <p className="text-slate-500">Loading…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar />
      <div className="max-w-2xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-bold text-slate-900 mb-6">
          Edit application
        </h1>
        <ApplicationForm initial={app} />
      </div>
    </div>
  );
}
