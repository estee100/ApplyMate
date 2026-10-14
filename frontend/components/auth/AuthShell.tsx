import Link from "next/link";

type AuthShellProps = {
  children: React.ReactNode;
  title: string;
  subtitle: string;
  footer: { text: string; linkText: string; href: string };
};

export function AuthShell({ children, title, subtitle, footer }: AuthShellProps) {
  return (
    <div className="min-h-screen flex">
      {/* Left: landing / brand panel — hidden on small screens, visible md+ */}
      <div className="hidden lg:flex lg:w-1/2 xl:w-[55%] relative overflow-hidden bg-slate-950">
        {/* Gradient orbs */}
        <div
          className="absolute -top-32 -left-32 w-96 h-96 rounded-full bg-violet-600/40 blur-3xl"
          aria-hidden
        />
        <div
          className="absolute top-1/2 -right-24 w-80 h-80 rounded-full bg-cyan-500/30 blur-3xl"
          aria-hidden
        />
        <div
          className="absolute bottom-0 left-1/3 w-72 h-72 rounded-full bg-indigo-600/25 blur-3xl"
          aria-hidden
        />
        {/* Grid overlay */}
        <div
          className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.03)_1px,transparent_1px)] bg-[size:64px_64px]"
          aria-hidden
        />
        <div className="relative z-10 flex flex-col justify-between p-12 xl:p-16 text-white">
          <div>
            <Link
              href="/"
              className="inline-flex items-center gap-2 text-lg font-semibold tracking-tight"
            >
              <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/10 backdrop-blur">
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
            </Link>
          </div>
          <div className="space-y-8">
            <div>
              <h2 className="text-4xl xl:text-5xl font-bold tracking-tight leading-tight">
                Your job search,
                <br />
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-cyan-300 to-violet-300">
                  organized.
                </span>
              </h2>
              <p className="mt-6 text-lg text-slate-400 max-w-md leading-relaxed">
                Track applications, statuses, and notes in one place. Less
                spreadsheet chaos, more focus on landing the role.
              </p>
            </div>
            <ul className="space-y-4 text-slate-300">
              {[
                "Kanban & table views for every stage",
                "Secure account — your data stays yours",
                "Built for developers who apply with intent",
              ].map((item) => (
                <li key={item} className="flex items-center gap-3">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-500/20 text-emerald-400">
                    <svg
                      className="h-3.5 w-3.5"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                      strokeWidth={2.5}
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        d="M5 13l4 4L19 7"
                      />
                    </svg>
                  </span>
                  {item}
                </li>
              ))}
            </ul>
          </div>
          <p className="text-sm text-slate-500">
            © {new Date().getFullYear()} ApplyMate
          </p>
        </div>
      </div>

      {/* Right: form area */}
      <div className="flex-1 flex flex-col justify-center px-4 py-12 sm:px-8 lg:px-12 bg-slate-50">
        {/* Mobile logo */}
        <div className="lg:hidden mb-8 text-center">
          <Link
            href="/"
            className="inline-flex items-center gap-2 text-xl font-bold text-slate-900"
          >
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-900 text-white">
              <svg
                className="h-5 w-5"
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
          </Link>
        </div>

        <div className="w-full max-w-md mx-auto">
          <div className="mb-8">
            <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight">
              {title}
            </h1>
            <p className="mt-2 text-slate-600">{subtitle}</p>
          </div>

          <div className="rounded-2xl bg-white p-8 shadow-xl shadow-slate-200/50 ring-1 ring-slate-200/80">
            {children}
          </div>

          <p className="mt-8 text-center text-sm text-slate-500">
            {footer.text}{" "}
            <Link
              href={footer.href}
              className="font-semibold text-slate-900 hover:text-violet-600 transition-colors"
            >
              {footer.linkText}
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
