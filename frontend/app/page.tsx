"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowRight, BarChart3, CalendarDays, FileText, KanbanSquare, Search, ShieldCheck, Sparkles } from "lucide-react";
import { ButtonLink } from "@/components/ui/button";
import { Logo } from "@/components/ui/visuals";
import { getToken } from "@/lib/api";

const FEATURES = [
  {
    icon: Search,
    title: "One feed, every source",
    text: "Search local and remote jobs in one place, or paste a link from LinkedIn, Indeed or any careers page.",
  },
  {
    icon: Sparkles,
    title: "Honest match scores",
    text: "See which requirements you meet and which you're missing, backed by evidence from your resume.",
  },
  {
    icon: FileText,
    title: "Tailored in minutes",
    text: "Reword your master resume for each role without ever changing your dates, employers or titles.",
  },
  {
    icon: KanbanSquare,
    title: "A board for your pipeline",
    text: "Drag applications from applied to offer, log every call and email, and never miss a follow-up.",
  },
  {
    icon: CalendarDays,
    title: "Next steps on a calendar",
    text: "Interviews, deadlines and follow-ups in one view, with optional reminder emails.",
  },
  {
    icon: BarChart3,
    title: "Know what's working",
    text: "Response, interview and offer rates, and how long employers take to reply.",
  },
];

export default function LandingPage() {
  const [signedIn, setSignedIn] = useState(false);
  useEffect(() => setSignedIn(!!getToken()), []);

  return (
    <div className="relative min-h-screen overflow-hidden">
      <div className="aurora pointer-events-none absolute inset-x-0 top-0 h-[720px]" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[720px] bg-[linear-gradient(hsl(var(--foreground)/0.04)_1px,transparent_1px),linear-gradient(90deg,hsl(var(--foreground)/0.04)_1px,transparent_1px)] bg-[size:64px_64px] [mask-image:radial-gradient(ellipse_at_top,black_20%,transparent_70%)]" />

      <header className="relative mx-auto flex max-w-6xl items-center justify-between px-4 py-5 sm:px-6">
        <Logo />
        <div className="flex items-center gap-2">
          {signedIn ? (
            <ButtonLink href="/dashboard" size="sm">
              Open ApplyMate <ArrowRight className="h-3.5 w-3.5" />
            </ButtonLink>
          ) : (
            <>
              <ButtonLink href="/login" variant="ghost" size="sm">
                Sign in
              </ButtonLink>
              <ButtonLink href="/register" size="sm">
                Get started
              </ButtonLink>
            </>
          )}
        </div>
      </header>

      <main className="relative">
        <section className="mx-auto max-w-4xl px-4 pb-20 pt-16 text-center sm:px-6 sm:pt-24">
          <span className="inline-flex animate-fade-in items-center gap-2 rounded-full border border-border bg-card/70 px-3 py-1 text-xs font-medium text-muted-foreground shadow-soft backdrop-blur">
            <Sparkles className="h-3.5 w-3.5 text-primary" />
            AI that works from your real experience
          </span>
          <h1 className="mt-6 animate-slide-up text-4xl font-semibold leading-[1.05] tracking-tight sm:text-6xl">
            Your job search,
            <br />
            <span className="text-gradient">finally organized.</span>
          </h1>
          <p className="mx-auto mt-6 max-w-2xl animate-slide-up text-base text-muted-foreground sm:text-lg">
            Find roles that fit, see exactly how you match, tailor your resume for each one, and track every application until you hear back.
          </p>
          <div className="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <ButtonLink href={signedIn ? "/dashboard" : "/register"} variant="gradient" size="lg">
              {signedIn ? "Go to your dashboard" : "Start for free"} <ArrowRight className="h-4 w-4" />
            </ButtonLink>
            {!signedIn && (
              <ButtonLink href="/login" variant="outline" size="lg">
                I have an account
              </ButtonLink>
            )}
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-4 pb-24 sm:px-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(({ icon: Icon, title, text }) => (
              <div key={title} className="card group p-6 transition-all hover:-translate-y-0.5 hover:shadow-lift">
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-primary/15 to-accent/15 text-primary ring-1 ring-inset ring-primary/15">
                  <Icon className="h-5 w-5" />
                </span>
                <h3 className="mt-5 font-semibold tracking-tight">{title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{text}</p>
              </div>
            ))}
          </div>

          <div className="mt-6 flex items-start gap-3 rounded-2xl border border-border bg-muted/40 p-5 text-sm text-muted-foreground">
            <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-success" />
            <p>
              Every AI draft is yours to review before it goes anywhere. Nothing is ever sent or submitted for you, and a match score is an estimate, not a
              prediction of whether you&apos;ll be hired.
            </p>
          </div>
        </section>
      </main>

      <footer className="relative border-t border-border py-8 text-center text-xs text-muted-foreground">
        © {new Date().getFullYear()} ApplyMate ·{" "}
        <Link href="/login" className="hover:text-foreground">
          Sign in
        </Link>
      </footer>
    </div>
  );
}
