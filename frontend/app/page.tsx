import Link from "next/link";

export default function HomePage() {
  return (
    <div className="min-h-screen flex flex-col bg-slate-950 text-white overflow-hidden">
      {/* Background */}
      <div className="fixed inset-0 -z-10">
        <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:48px_48px]" />
        <div className="absolute top-0 left-1/4 w-[500px] h-[500px] rounded-full bg-violet-600/20 blur-[100px]" />
        <div className="absolute bottom-0 right-1/4 w-[400px] h-[400px] rounded-full bg-cyan-500/15 blur-[80px]" />
      </div>

      <header className="relative border-b border-white/5">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
          <span className="flex items-center gap-2 text-lg font-semibold tracking-tight">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/10">
              <svg
                className="h-5 w-5 text-cyan-300"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={2}
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                />
              </svg>
            </span>
            ApplyMate
          </span>
          <nav className="flex items-center gap-3">
            <Link
              href="/login"
              className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white transition-colors"
            >
              Sign in
            </Link>
            <Link
              href="/register"
              className="px-4 py-2 rounded-lg bg-white text-slate-900 text-sm font-semibold hover:bg-slate-100 transition-colors"
            >
              Get started
            </Link>
          </nav>
        </div>
      </header>

      <main className="relative flex-1 flex flex-col items-center justify-center px-4 py-20 sm:py-28">
        <div className="max-w-3xl text-center space-y-8">
          <p className="text-sm font-medium text-cyan-400/90 uppercase tracking-widest">
            Job application tracker
          </p>
          <h1 className="text-4xl sm:text-5xl md:text-6xl font-bold tracking-tight leading-[1.1]">
            Stop losing track of{" "}
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-cyan-300 to-violet-300">
              every application
            </span>
          </h1>
          <p className="text-lg sm:text-xl text-slate-400 max-w-2xl mx-auto leading-relaxed">
            One place for statuses, dates, and notes. Table and kanban views
            so you always know what&apos;s applied, interviewing, or offer.
          </p>
          <div className="flex flex-col sm:flex-row gap-4 justify-center pt-4">
            <Link
              href="/register"
              className="inline-flex justify-center px-8 py-3.5 rounded-xl bg-white text-slate-900 font-semibold hover:bg-slate-100 transition-colors shadow-xl shadow-black/20"
            >
              Create free account
            </Link>
            <Link
              href="/login"
              className="inline-flex justify-center px-8 py-3.5 rounded-xl border border-white/20 text-white font-semibold hover:bg-white/5 transition-colors"
            >
              I have an account
            </Link>
          </div>
        </div>

        <div className="mt-20 grid grid-cols-1 sm:grid-cols-3 gap-6 max-w-4xl w-full text-left">
          {[
            {
              title: "Pipeline clarity",
              desc: "See every app by status—applied through offer or withdrawn.",
            },
            {
              title: "Your data",
              desc: "Account-based storage. Log in from anywhere, pick up where you left off.",
            },
            {
              title: "Built to ship",
              desc: "Fast, focused UI so you spend time on interviews—not spreadsheets.",
            },
          ].map((item) => (
            <div
              key={item.title}
              className="rounded-2xl border border-white/10 bg-white/5 p-6 backdrop-blur-sm"
            >
              <h3 className="font-semibold text-white mb-2">{item.title}</h3>
              <p className="text-sm text-slate-400 leading-relaxed">{item.desc}</p>
            </div>
          ))}
        </div>
      </main>

      <footer className="relative border-t border-white/5 py-8 text-center text-sm text-slate-500">
        © {new Date().getFullYear()} ApplyMate
      </footer>
    </div>
  );
}
