import Link from "next/link";

export default function HomePage() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center p-8 bg-gradient-to-br from-slate-50 to-slate-200">
      <div className="max-w-lg text-center space-y-6">
        <h1 className="text-4xl font-bold text-slate-900 tracking-tight">
          ApplyMate
        </h1>
        <p className="text-slate-600 text-lg">
          Track and manage your job applications in one place. Sign up to get
          started.
        </p>
        <div className="flex flex-wrap gap-4 justify-center">
          <Link
            href="/login"
            className="px-6 py-3 rounded-lg bg-primary text-white font-medium hover:bg-primary-dark transition"
          >
            Log in
          </Link>
          <Link
            href="/register"
            className="px-6 py-3 rounded-lg border border-slate-300 bg-white text-slate-800 font-medium hover:bg-slate-50 transition"
          >
            Register
          </Link>
        </div>
      </div>
    </main>
  );
}
