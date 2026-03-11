"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { clearToken } from "@/services/api";

export function Navbar() {
  const router = useRouter();

  function logout() {
    clearToken();
    router.push("/login");
  }

  return (
    <header className="bg-white border-b border-slate-200 shadow-sm">
      <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
        <Link href="/dashboard" className="text-xl font-bold text-primary">
          ApplyMate
        </Link>
        <nav className="flex items-center gap-4">
          <Link
            href="/dashboard"
            className="text-slate-600 hover:text-slate-900 text-sm font-medium"
          >
            Dashboard
          </Link>
          <Link
            href="/applications/new"
            className="text-slate-600 hover:text-slate-900 text-sm font-medium"
          >
            New application
          </Link>
          <button
            type="button"
            onClick={logout}
            className="text-sm text-slate-500 hover:text-slate-800"
          >
            Log out
          </button>
        </nav>
      </div>
    </header>
  );
}
