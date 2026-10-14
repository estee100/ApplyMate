"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  AlarmClock,
  ArrowRight,
  Briefcase,
  CalendarDays,
  FileUp,
  KanbanSquare,
  PartyPopper,
  Plus,
  Send,
  Sparkles,
  Trophy,
} from "lucide-react";
import { ButtonLink, Button } from "@/components/ui/button";
import { Card, CardHeader, EmptyState, Skeleton, StageBadge, Stat } from "@/components/ui/misc";
import { CompanyLogo } from "@/components/ui/visuals";
import { LogApplicationDialog } from "@/components/jobs/add-job-dialogs";
import { WeeklyGoalCard } from "@/components/tools";
import { api, getStoredUser } from "@/lib/api";
import type { AnalyticsSummary, Application, ApplicationStats, Profile } from "@/lib/types";
import { cn, formatDay, greeting, isOpenStage, parseDay, relativeTime, STAGE_LABEL, STAGE_STYLE, STAGES, today } from "@/lib/utils";

export default function DashboardPage() {
  const [stats, setStats] = useState<ApplicationStats | null>(null);
  const [analytics, setAnalytics] = useState<AnalyticsSummary | null>(null);
  const [apps, setApps] = useState<Application[] | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [logOpen, setLogOpen] = useState(false);
  const [name, setName] = useState("");

  useEffect(() => {
    const user = getStoredUser();
    setName(user?.name?.split(" ")[0] || "");
    api.stats().then(setStats).catch(() => setStats(null));
    api.analytics().then(setAnalytics).catch(() => setAnalytics(null));
    api.applications().then(setApps).catch(() => setApps([]));
    api.profile().then(setProfile).catch(() => setProfile(null));
  }, []);

  const followUps = (apps ?? []).filter((a) => a.needsFollowUp);
  const upcoming = (apps ?? [])
    .filter((a) => a.nextActionDate && isOpenStage(a.status) && a.nextActionDate >= today())
    .sort((a, b) => a.nextActionDate!.localeCompare(b.nextActionDate!))
    .slice(0, 4);
  const recent = (apps ?? []).slice(0, 5);
  const total = stats?.total ?? 0;

  return (
    <div className="space-y-8">
      {/* Hero */}
      <section data-tour="hero" className="relative overflow-hidden rounded-3xl border border-border bg-card p-6 shadow-soft sm:p-8">
        <div className="aurora pointer-events-none absolute inset-0" />
        <div className="relative flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-sm font-medium text-muted-foreground">{greeting()}{name ? `, ${name}` : ""}</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">
              {total === 0 ? (
                <>Let&apos;s land your next role.</>
              ) : stats && stats.needsFollowUp > 0 ? (
                <>
                  {stats.needsFollowUp} {stats.needsFollowUp === 1 ? "application needs" : "applications need"} a follow-up.
                </>
              ) : (
                <>
                  You have <span className="text-gradient">{stats?.active ?? 0} active</span> applications.
                </>
              )}
            </h1>
            <p className="mt-2 max-w-xl text-muted-foreground">
              Find jobs that fit, tailor your resume for each one, and keep every application moving.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <ButtonLink href="/jobs" variant="gradient">
              <Briefcase className="h-4 w-4" />
              Find jobs
            </ButtonLink>
            <Button variant="outline" onClick={() => setLogOpen(true)}>
              <Plus className="h-4 w-4" />
              Log an application
            </Button>
          </div>
        </div>
      </section>

      {/* Setup nudge */}
      {profile && !profile.resume && (
        <Link
          href="/profile"
          className="group flex items-center gap-4 rounded-2xl border border-primary/25 bg-primary/5 p-5 transition-colors hover:bg-primary/10"
        >
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <FileUp className="h-5 w-5" />
          </span>
          <div className="flex-1">
            <p className="font-semibold">Upload your resume to unlock match scores and recommendations</p>
            <p className="text-sm text-muted-foreground">PDF, Word or plain text. It takes a few seconds.</p>
          </div>
          <ArrowRight className="h-5 w-5 text-primary transition-transform group-hover:translate-x-1" />
        </Link>
      )}

      {/* Stats */}
      <section className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        {stats === null ? (
          [0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-[124px] rounded-2xl" />)
        ) : (
          <>
            <Stat label="Applications" value={stats.total} icon={Send} hint={`${stats.active} still open`} />
            <Stat
              label="Response rate"
              value={analytics?.responseRatePct != null ? `${analytics.responseRatePct}%` : "–"}
              icon={Sparkles}
              accent="bg-accent/10 text-accent"
              hint={analytics?.avgDaysToResponse != null ? `~${analytics.avgDaysToResponse} days to hear back` : "Updates as employers reply"}
            />
            <Stat
              label="Interviews"
              value={stats.byStatus.INTERVIEWING}
              icon={CalendarDays}
              accent="bg-warning/10 text-warning"
              hint={analytics?.interviewRatePct != null ? `${analytics.interviewRatePct}% interview rate` : undefined}
            />
            <Stat label="Offers" value={stats.offers} icon={Trophy} accent="bg-success/10 text-success" hint={stats.offers > 0 ? "Congratulations!" : undefined} />
          </>
        )}
      </section>

      <div className="grid gap-6 lg:grid-cols-3">
        <WeeklyGoalCard />
      {/* Pipeline bar */}
      {stats && stats.total > 0 && (
        <Card className="p-5 lg:col-span-2">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-semibold">Pipeline</h2>
            <Link href="/tracking" className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline">
              Open board <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
          <div className="flex h-3 overflow-hidden rounded-full bg-muted">
            {STAGES.map((s) =>
              stats.byStatus[s] > 0 ? (
                <div
                  key={s}
                  className={cn(STAGE_STYLE[s].bar, "transition-all")}
                  style={{ width: `${(stats.byStatus[s] / stats.total) * 100}%` }}
                  title={`${STAGE_LABEL[s]}: ${stats.byStatus[s]}`}
                />
              ) : null,
            )}
          </div>
          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-1.5">
            {STAGES.map((s) => (
              <span key={s} className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className={cn("h-2 w-2 rounded-full", STAGE_STYLE[s].dot)} />
                {STAGE_LABEL[s]} <span className="font-medium text-foreground tabular-nums">{stats.byStatus[s]}</span>
              </span>
            ))}
          </div>
        </Card>
      )}
      </div>

      <div className="grid gap-6 lg:grid-cols-5">
        {/* Recent */}
        <Card className="lg:col-span-3">
          <CardHeader
            title="Recent activity"
            icon={KanbanSquare}
            action={
              <Link href="/tracking" className="text-sm font-medium text-primary hover:underline">
                View all
              </Link>
            }
          />
          {apps === null ? (
            <div className="space-y-3 p-5">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : recent.length === 0 ? (
            <EmptyState
              className="m-5"
              icon={PartyPopper}
              title="Nothing tracked yet"
              description="Mark a job as applied, or log one you applied to elsewhere."
            />
          ) : (
            <ul className="divide-y divide-border">
              {recent.map((a) => (
                <li key={a.id}>
                  <Link href={`/applications/${a.id}`} className="flex items-center gap-3 px-5 py-3.5 transition-colors hover:bg-muted/40">
                    <CompanyLogo name={a.companyName} src={a.companyLogoUrl} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{a.jobTitle}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {a.companyName} · updated {relativeTime(a.statusUpdatedAt)}
                      </p>
                    </div>
                    <StageBadge status={a.status} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* Up next */}
        <Card className="lg:col-span-2">
          <CardHeader
            title="Up next"
            icon={AlarmClock}
            action={
              <Link href="/calendar" className="text-sm font-medium text-primary hover:underline">
                Calendar
              </Link>
            }
          />
          <div className="space-y-2 p-4">
            {followUps.slice(0, 3).map((a) => (
              <Link
                key={`f${a.id}`}
                href={`/applications/${a.id}`}
                className="flex items-start gap-3 rounded-xl bg-warning/10 p-3 ring-1 ring-inset ring-warning/20 transition-colors hover:bg-warning/15"
              >
                <AlarmClock className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">Follow up with {a.companyName}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {a.nextActionDate ? `${a.nextAction ?? "Next step"} was due ${formatDay(a.nextActionDate)}` : `No change since ${formatDay(a.statusUpdatedAt)}`}
                  </p>
                </div>
              </Link>
            ))}
            {upcoming.map((a) => (
              <Link key={`u${a.id}`} href={`/applications/${a.id}`} className="flex items-start gap-3 rounded-xl p-3 transition-colors hover:bg-muted/60">
                <span className="flex w-10 shrink-0 flex-col items-center rounded-lg bg-primary/10 py-1 text-primary">
                  <span className="text-[10px] font-semibold uppercase">
                    {parseDay(a.nextActionDate!).toLocaleDateString(undefined, { month: "short" })}
                  </span>
                  <span className="text-sm font-semibold leading-none">{parseDay(a.nextActionDate!).getDate()}</span>
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{a.nextAction ?? "Next step"}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {a.jobTitle} · {a.companyName}
                  </p>
                </div>
              </Link>
            ))}
            {apps !== null && followUps.length === 0 && upcoming.length === 0 && (
              <p className="px-2 py-8 text-center text-sm text-muted-foreground">
                You&apos;re all caught up. Set a next step on an application to see it here.
              </p>
            )}
          </div>
        </Card>
      </div>

      <LogApplicationDialog open={logOpen} onClose={() => setLogOpen(false)} />
    </div>
  );
}
