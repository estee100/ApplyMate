"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Bookmark,
  BookmarkCheck,
  Building2,
  CalendarClock,
  Check,
  ChevronDown,
  CircleDollarSign,
  FileText,
  Mail,
  MapPin,
  RefreshCw,
  Sparkles,
  Wand2,
  X,
} from "lucide-react";
import { useToast } from "@/components/providers";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/form";
import { Alert, Badge, Skeleton, StageBadge } from "@/components/ui/misc";
import { CompanyLogo, MatchRing } from "@/components/ui/visuals";
import { ApplyLink } from "@/components/jobs/apply-link";
import { api, ApiError } from "@/lib/api";
import type { ApplicationStatus, JobCard, JobDetail, MatchResponse } from "@/lib/types";
import { cn, errorMessage, formatDay, JOB_TYPE_LABEL, matchBand, relativeTime, STAGE_LABEL, STAGES } from "@/lib/utils";

/** The job, its match, documents and application, shared by the dialog and the full page. */
export function JobDetailView({ jobId, onChange }: { jobId: number; onChange?: (card: JobCard) => void }) {
  const toast = useToast();
  const router = useRouter();
  const [detail, setDetail] = useState<JobDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [matching, setMatching] = useState(false);
  const [matchError, setMatchError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [instructions, setInstructions] = useState("");
  const [showSteer, setShowSteer] = useState(false);
  const [showDescription, setShowDescription] = useState(false);
  const autoRan = useRef(false);

  async function load() {
    try {
      const d = await api.job(jobId);
      setDetail(d);
      onChange?.(d.job);
      return d;
    } catch (e) {
      setError(errorMessage(e));
      return null;
    }
  }

  async function analyse(refresh = false) {
    setMatching(true);
    setMatchError(null);
    try {
      const match = await api.match(jobId, refresh);
      setDetail((d) => (d ? { ...d, match } : d));
      const card = await api.job(jobId);
      setDetail(card);
      onChange?.(card.job);
    } catch (e) {
      setMatchError(errorMessage(e));
    } finally {
      setMatching(false);
    }
  }

  useEffect(() => {
    autoRan.current = false;
    setDetail(null);
    load().then((d) => {
      // Opening a job analyses it once; after that the cached result is free.
      if (d && d.hasResume && d.analyzable && !d.match && !autoRan.current) {
        autoRan.current = true;
        analyse(false);
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobId]);

  async function run(label: string, action: () => Promise<unknown>, success?: string) {
    setBusy(label);
    try {
      await action();
      if (success) toast(success);
      await load();
    } catch (e) {
      toast(errorMessage(e), e instanceof ApiError && e.isRateLimited ? "info" : "error");
    } finally {
      setBusy(null);
    }
  }

  async function generate(kind: "master" | "tailor" | "cover") {
    setBusy(kind);
    try {
      const doc =
        kind === "master"
          ? await api.resumeFromMaster(jobId)
          : kind === "tailor"
            ? await api.tailorResume(jobId, instructions.trim() || undefined)
            : await api.coverLetter(jobId, instructions.trim() || undefined);
      router.push(`/documents/${doc.id}`);
    } catch (e) {
      toast(errorMessage(e), e instanceof ApiError && e.isRateLimited ? "info" : "error");
      setBusy(null);
    }
  }

  if (error) return <Alert>{error}</Alert>;
  if (!detail) return <DetailSkeleton />;

  const { job, match, application, documents } = detail;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start gap-4">
        <CompanyLogo name={job.company} src={job.companyLogoUrl} size="lg" />
        <div className="min-w-0 flex-1">
          <h2 className="text-xl font-semibold leading-tight tracking-tight">{job.title}</h2>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
            <span className="inline-flex items-center gap-1.5">
              <Building2 className="h-3.5 w-3.5" />
              {job.company}
            </span>
            {job.location && (
              <span className="inline-flex items-center gap-1.5">
                <MapPin className="h-3.5 w-3.5" />
                {job.location}
              </span>
            )}
            {job.salary && (
              <span className="inline-flex items-center gap-1.5">
                <CircleDollarSign className="h-3.5 w-3.5" />
                {job.salary}
              </span>
            )}
            {job.postedDate && (
              <span className="inline-flex items-center gap-1.5">
                <CalendarClock className="h-3.5 w-3.5" />
                Posted {relativeTime(job.postedDate)}
              </span>
            )}
          </div>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {job.jobType && <Badge>{JOB_TYPE_LABEL[job.jobType]}</Badge>}
            {job.source === "PASTED" ? <Badge>Added by you</Badge> : <Badge>via {job.publisher}</Badge>}
            {application && <StageBadge status={application.status} />}
          </div>
        </div>
      </div>

      {/* Primary actions */}
      <div className="flex flex-wrap gap-2">
        <ApplyLink url={job.url} publisher={job.publisher} />
        <Button
          variant="outline"
          loading={busy === "save"}
          onClick={() =>
            run("save", () => (job.saved ? api.unsaveJob(job.id) : api.saveJob(job.id)), job.saved ? "Removed from saved" : "Saved")
          }
        >
          {job.saved ? <BookmarkCheck className="h-4 w-4 text-primary" /> : <Bookmark className="h-4 w-4" />}
          {job.saved ? "Saved" : "Save"}
        </Button>
        {!application && (
          <Button variant="outline" loading={busy === "track"} onClick={() => run("track", () => api.trackJob(job.id), "Added to your tracking board")}>
            <Check className="h-4 w-4" />
            Mark as applied
          </Button>
        )}
      </div>

      {/* Match */}
      <section className="rounded-2xl border border-border bg-gradient-to-br from-primary/[0.04] to-accent/[0.04] p-5">
        <div className="flex items-center justify-between gap-3">
          <h3 className="flex items-center gap-2 font-semibold">
            <Sparkles className="h-4 w-4 text-primary" />
            How you match
          </h3>
          {match && (
            <Button variant="ghost" size="sm" onClick={() => analyse(true)} loading={matching}>
              <RefreshCw className="h-3.5 w-3.5" />
              Re-analyse
            </Button>
          )}
        </div>

        {!detail.hasResume ? (
          <p className="mt-3 text-sm text-muted-foreground">
            <Link href="/profile" className="font-medium text-primary hover:underline">
              Upload your resume
            </Link>{" "}
            to see how well you match this job.
          </p>
        ) : !detail.analyzable ? (
          <p className="mt-3 text-sm text-muted-foreground">This job has no description to analyse.</p>
        ) : matching && !match ? (
          <div className="mt-4 flex items-center gap-4">
            <Skeleton className="h-16 w-16 rounded-full" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-1/3" />
              <Skeleton className="h-3 w-2/3" />
            </div>
          </div>
        ) : match ? (
          <MatchBody match={match} />
        ) : (
          <div className="mt-3 space-y-3">
            {matchError && <Alert tone="warning">{matchError}</Alert>}
            <Button variant="gradient" onClick={() => analyse(false)} loading={matching}>
              <Sparkles className="h-4 w-4" />
              Analyse my match
            </Button>
          </div>
        )}
        {match && matchError && <Alert tone="warning" className="mt-3">{matchError}</Alert>}
      </section>

      {/* Documents */}
      <section>
        <h3 className="mb-3 font-semibold">Resume &amp; cover letter</h3>
        <div className="grid gap-2 sm:grid-cols-3">
          <ActionTile
            icon={FileText}
            title="Start from master"
            text="Exact copy, no AI"
            loading={busy === "master"}
            disabled={!detail.hasResume || !!busy}
            onClick={() => generate("master")}
          />
          <ActionTile
            icon={Wand2}
            title="Tailor with AI"
            text="Rewords for this role"
            loading={busy === "tailor"}
            disabled={!detail.hasResume || !detail.analyzable || !!busy}
            onClick={() => generate("tailor")}
          />
          <ActionTile
            icon={Mail}
            title="Cover letter"
            text="Written for this job"
            loading={busy === "cover"}
            disabled={!detail.hasResume || !detail.analyzable || !!busy}
            onClick={() => generate("cover")}
          />
        </div>
        {detail.hasResume && detail.analyzable && (
          <div className="mt-2">
            {showSteer ? (
              <Input
                autoFocus
                value={instructions}
                maxLength={500}
                onChange={(e) => setInstructions(e.target.value)}
                placeholder='Optional, e.g. "emphasise my backend work"'
              />
            ) : (
              <button onClick={() => setShowSteer(true)} className="text-xs font-medium text-primary hover:underline">
                + Add guidance for the AI
              </button>
            )}
          </div>
        )}
        {documents.length > 0 && (
          <ul className="mt-4 divide-y divide-border rounded-xl border border-border">
            {documents.map((d) => (
              <li key={d.id}>
                <Link href={`/documents/${d.id}`} className="flex items-center justify-between gap-3 px-4 py-3 text-sm hover:bg-muted/50">
                  <span className="flex items-center gap-2 font-medium">
                    {d.kind === "RESUME" ? <FileText className="h-4 w-4 text-primary" /> : <Mail className="h-4 w-4 text-accent" />}
                    {d.kind === "RESUME" ? "Resume" : "Cover letter"}
                    {d.modelUsed === "master-copy" && <Badge>From master</Badge>}
                    {d.edited && <Badge tone="primary">Edited</Badge>}
                  </span>
                  <span className="text-xs text-muted-foreground">{relativeTime(d.updatedAt)}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Application */}
      {application && (
        <ApplicationQuickPanel
          key={application.id + application.status}
          id={application.id}
          status={application.status}
          dateApplied={application.dateApplied}
          onChanged={load}
        />
      )}

      {/* Description */}
      {detail.description && (
        <section>
          <button
            onClick={() => setShowDescription((v) => !v)}
            className="flex w-full items-center justify-between rounded-xl py-1 text-left font-semibold"
            aria-expanded={showDescription}
          >
            Job description
            <ChevronDown className={cn("h-4 w-4 text-muted-foreground transition-transform", showDescription && "rotate-180")} />
          </button>
          <div className={cn("relative mt-2 overflow-hidden", !showDescription && "max-h-40")}>
            <p className="whitespace-pre-line text-sm leading-relaxed text-muted-foreground">{detail.description}</p>
            {!showDescription && <div className="absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-card to-transparent" />}
          </div>
          {job.source === "ADZUNA" && (
            <p className="mt-2 text-xs text-muted-foreground">Adzuna shares a shortened description. The full advert is on the Apply link.</p>
          )}
        </section>
      )}
    </div>
  );
}

function MatchBody({ match }: { match: MatchResponse }) {
  const band = matchBand(match.matchPercentage);
  return (
    <div className="mt-4 animate-fade-in">
      <div className="flex items-center gap-4">
        <MatchRing value={match.matchPercentage} size={72} stroke={6} />
        <div>
          <p className={cn("font-semibold", band.tone)}>{band.label}</p>
          <p className="text-sm text-muted-foreground">
            {match.requirementsMet.length} requirements met · {match.requirementsMissing.length} missing
          </p>
        </div>
      </div>
      {match.rationale && <p className="mt-4 text-sm leading-relaxed">{match.rationale}</p>}
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <RequirementList title="You have" items={match.requirementsMet} positive />
        <RequirementList title="Missing or not shown" items={match.requirementsMissing} />
      </div>
      <p className="mt-4 text-xs text-muted-foreground">
        An AI estimate of how well your resume covers this advert, not a prediction of whether you&apos;ll be hired. Analysed{" "}
        {relativeTime(match.generatedAt)}.
      </p>
    </div>
  );
}

function RequirementList({ title, items, positive }: { title: string; items: string[]; positive?: boolean }) {
  return (
    <div>
      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</p>
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">None</p>
      ) : (
        <ul className="space-y-1.5">
          {items.map((item) => (
            <li key={item} className="flex items-start gap-2 text-sm">
              {positive ? (
                <Check className="mt-0.5 h-4 w-4 shrink-0 text-success" />
              ) : (
                <X className="mt-0.5 h-4 w-4 shrink-0 text-danger" />
              )}
              {item}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ActionTile({
  icon: Icon,
  title,
  text,
  onClick,
  loading,
  disabled,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  text: string;
  onClick: () => void;
  loading?: boolean;
  disabled?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="group flex items-center gap-3 rounded-xl border border-border bg-card p-3 text-left transition-all hover:border-primary/40 hover:shadow-soft disabled:cursor-not-allowed disabled:opacity-50"
    >
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
        {loading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Icon className="h-4 w-4" />}
      </span>
      <span>
        <span className="block text-sm font-medium">{title}</span>
        <span className="block text-xs text-muted-foreground">{text}</span>
      </span>
    </button>
  );
}

/** Move an application between stages with a note on what happened. */
export function ApplicationQuickPanel({
  id,
  status,
  dateApplied,
  onChanged,
}: {
  id: number;
  status: ApplicationStatus;
  dateApplied: string;
  onChanged: () => void;
}) {
  const toast = useToast();
  const [next, setNext] = useState<ApplicationStatus>(status);
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  async function save() {
    setSaving(true);
    try {
      await api.updateApplication(id, { status: next, note: note.trim() || undefined });
      toast(`Moved to ${STAGE_LABEL[next]}`);
      setNote("");
      onChanged();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="rounded-2xl border border-border p-5">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold">Application</h3>
        <Link href={`/applications/${id}`} className="text-sm font-medium text-primary hover:underline">
          Open timeline
        </Link>
      </div>
      <p className="mt-1 text-sm text-muted-foreground">Applied {formatDay(dateApplied)}</p>
      <div className="mt-4 grid gap-3 sm:grid-cols-[200px_1fr_auto]">
        <Select value={next} onChange={(e) => setNext(e.target.value as ApplicationStatus)} aria-label="Stage">
          {STAGES.map((s) => (
            <option key={s} value={s}>
              {STAGE_LABEL[s]}
            </option>
          ))}
        </Select>
        <Input
          value={note}
          maxLength={1000}
          onChange={(e) => setNote(e.target.value)}
          placeholder="What happened? e.g. Invited to interview by email"
          disabled={next === status}
        />
        <Button onClick={save} loading={saving} disabled={next === status}>
          Update
        </Button>
      </div>
    </section>
  );
}

function DetailSkeleton() {
  return (
    <div className="space-y-6">
      <div className="flex gap-4">
        <Skeleton className="h-14 w-14 rounded-2xl" />
        <div className="flex-1 space-y-2">
          <Skeleton className="h-5 w-2/3" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      </div>
      <Skeleton className="h-10 w-64" />
      <Skeleton className="h-40 w-full rounded-2xl" />
      <Skeleton className="h-24 w-full rounded-2xl" />
    </div>
  );
}
