"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Navbar } from "@/components/Navbar";
import { getToken } from "@/services/api";
import { ApplicationForm } from "@/components/ApplicationForm";

export default function NewApplicationPage() {
  const router = useRouter();

  useEffect(() => {
    if (!getToken()) router.replace("/login");
  }, [router]);

  if (typeof window !== "undefined" && !getToken()) return null;

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar />
      <div className="max-w-2xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-bold text-slate-900 mb-6">
          New job application
        </h1>
        <ApplicationForm />
      </div>
    </div>
  );
}
