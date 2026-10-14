import Link from "next/link";
import { Check, FileText, KanbanSquare, Sparkles } from "lucide-react";
import { Logo } from "@/components/ui/visuals";

const POINTS = [
  { icon: Sparkles, text: "See how well you match a job before you apply" },
  { icon: FileText, text: "Tailor your resume and cover letter in minutes" },
  { icon: KanbanSquare, text: "Track every application from applied to offer" },
];

export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: React.ReactNode;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.1fr_1fr]">
      {/* Brand panel */}
      <div className="relative hidden overflow-hidden bg-zinc-950 p-12 text-white lg:flex lg:flex-col">
        <div className="absolute inset-0 opacity-90 [background:radial-gradient(60%_50%_at_20%_10%,hsl(252_90%_60%/0.45),transparent_60%),radial-gradient(50%_45%_at_85%_30%,hsl(199_89%_50%/0.35),transparent_60%),radial-gradient(40%_40%_at_40%_95%,hsl(320_80%_55%/0.25),transparent_60%)]" />
        <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.04)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.04)_1px,transparent_1px)] bg-[size:56px_56px] [mask-image:radial-gradient(ellipse_at_center,black_30%,transparent_75%)]" />

        <Link href="/" className="relative">
          <Logo className="text-white [&_.text-gradient]:from-violet-300 [&_.text-gradient]:to-cyan-300" />
        </Link>

        <div className="relative mt-auto max-w-md">
          <h2 className="text-4xl font-semibold leading-tight tracking-tight xl:text-5xl">
            Apply with intent,{" "}
            <span className="bg-gradient-to-r from-violet-300 to-cyan-300 bg-clip-text text-transparent">not in bulk.</span>
          </h2>
          <ul className="mt-10 space-y-4">
            {POINTS.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-zinc-300">
                <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-white/10 ring-1 ring-inset ring-white/15">
                  <Icon className="h-4 w-4 text-cyan-300" />
                </span>
                {text}
              </li>
            ))}
          </ul>

          {/* A glimpse of the product */}
          <div className="mt-12 rounded-2xl border border-white/10 bg-white/5 p-5 backdrop-blur-md">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium">Backend Developer</p>
                <p className="text-xs text-zinc-400">Acme · Edmonton, AB</p>
              </div>
              <span className="rounded-full bg-emerald-400/15 px-2.5 py-1 text-xs font-semibold text-emerald-300 ring-1 ring-inset ring-emerald-400/30">
                82% match
              </span>
            </div>
            <div className="mt-4 flex flex-wrap gap-1.5">
              {["Java", "Spring Boot", "PostgreSQL", "REST APIs"].map((s) => (
                <span key={s} className="inline-flex items-center gap-1 rounded-md bg-white/10 px-2 py-0.5 text-[11px] text-zinc-200">
                  <Check className="h-3 w-3 text-emerald-300" />
                  {s}
                </span>
              ))}
            </div>
          </div>
        </div>
        <p className="relative mt-10 text-xs text-zinc-500">© {new Date().getFullYear()} ApplyMate</p>
      </div>

      {/* Form */}
      <div className="aurora flex flex-col justify-center px-4 py-12 sm:px-10">
        <div className="mx-auto w-full max-w-sm animate-slide-up">
          <Link href="/" className="mb-10 inline-block lg:hidden">
            <Logo />
          </Link>
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{title}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{subtitle}</p>
          <div className="mt-8">{children}</div>
          {footer && <div className="mt-8 text-center text-sm text-muted-foreground">{footer}</div>}
        </div>
      </div>
    </div>
  );
}
