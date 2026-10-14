"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { register, setToken } from "@/services/api";
import { AuthShell } from "@/components/auth/AuthShell";

export default function RegisterPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    setLoading(true);
    try {
      const res = await register(email, password);
      setToken(res.token);
      router.push("/dashboard");
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Registration failed. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell
      title="Create your account"
      subtitle="Start tracking applications in minutes."
      footer={{
        text: "Already registered?",
        linkText: "Sign in instead",
        href: "/login",
      }}
    >
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && (
          <div
            role="alert"
            className="text-sm text-red-700 bg-red-50 border border-red-100 px-4 py-3 rounded-xl"
          >
            {error}
          </div>
        )}
        <div className="space-y-2">
          <label
            htmlFor="register-email"
            className="block text-sm font-medium text-slate-700"
          >
            Email
          </label>
          <input
            id="register-email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-xl border border-slate-200 bg-slate-50/50 px-4 py-3 text-slate-900 placeholder:text-slate-400 transition-colors hover:border-slate-300 focus:bg-white"
            placeholder="you@example.com"
          />
        </div>
        <div className="space-y-2">
          <label
            htmlFor="register-password"
            className="block text-sm font-medium text-slate-700"
          >
            Password
          </label>
          <input
            id="register-password"
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded-xl border border-slate-200 bg-slate-50/50 px-4 py-3 text-slate-900 placeholder:text-slate-400 transition-colors hover:border-slate-300 focus:bg-white"
            placeholder="At least 8 characters"
          />
          <p className="text-xs text-slate-500">
            Use 8+ characters for a secure account.
          </p>
        </div>
        <button
          type="submit"
          disabled={loading}
          className="w-full py-3 rounded-xl bg-slate-900 text-white font-semibold hover:bg-slate-800 disabled:opacity-50 disabled:pointer-events-none transition-colors shadow-lg shadow-slate-900/20"
        >
          {loading ? (
            <span className="inline-flex items-center gap-2">
              <svg
                className="animate-spin h-4 w-4"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle
                  className="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  strokeWidth="4"
                />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                />
              </svg>
              Creating account…
            </span>
          ) : (
            "Create account"
          )}
        </button>
      </form>
    </AuthShell>
  );
}
