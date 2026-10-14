"use client";

import Link from "next/link";
import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  AlarmClock,
  ArrowDownLeft,
  ArrowLeft,
  ArrowUpRight,
  Brain,
  CalendarPlus,
  ExternalLink,
  History,
  Lightbulb,
  MessageSquare,
  NotebookPen,
  Plus,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { useToast } from "@/components/providers";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { Alert, Badge, Card, CardHeader, Skeleton, Spinner, StageBadge } from "@/components/ui/misc";
import { CompanyLogo } from "@/components/ui/visuals";
import { ApplyLink } from "@/components/jobs/apply-link";
import { ApplicationQuickPanel } from "@/components/jobs/job-detail";
import { EmailDraftCard } from "@/components/tools";
import { api, ApiError } from "@/lib/api";
import type { Application, ApplicationEvent, Communication, CommunicationDirection, CommunicationKind, InterviewPrep } from "@/lib/types";
import { COMMUNICATION_LABEL, cn, errorMessage, formatDay, relativeTime, STAGE_LABEL, STAGE_STYLE } from "@/lib/utils";

export default function ApplicationPage({ params }: { params: Promise<{ id: string }> }) {
  const id = Number(use(params).id);
  const router = useRouter();
  const toast = useToast();
  const [app, setApp] = useState<Application | null>(null);
  const [events, setEvents] = useState<ApplicationEvent[]>([]);
  const [comms, setComms] = useState<Communication[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  async function load() {
    try {
      const [a, e, c] = await Promise.all([api.application(id), api.events(id), api.communications(id)]);
      setApp(a);
      setEvents(e);
      setComms(c);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function remove() {
    try {
      await api.deleteApplication(id);
      toast("Stopped tracking this application");
      router.replace("/tracking");
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  }

  if (error) return <Alert>{error}</Alert>;
  if (!app) return <Spinner label="Loading application" />;

  return (
    <div className="mx-auto max-w-5xl">
      <Link href="/tracking" className="mb-6 inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> Back to board
      </Link>

      {/* Header */}
      <div className="card relative mb-6 overflow-hidden p-6">
        <div className={cn("absolute inset-x-0 top-0 h-1", STAGE_STYLE[app.status].bar)} />
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start">
          <CompanyLogo name={app.companyName} src={app.companyLogoUrl} size="lg" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <StageBadge status={app.status} />
              {app.manual && <Badge>Logged by hand</Badge>}
              {app.needsFollowUp && (
                <Badge tone="warning">
                  <AlarmClock className="h-3 w-3" /> Needs follow-up
                </Badge>
              )}
            </div>
            <h1 className="mt-2 text-2xl font-semibold tracking-tight">{app.jobTitle}</h1>
            <p className="text-muted-foreground">
              {app.companyName}
              {app.location ? ` · ${app.location}` : ""} · applied {formatDay(app.dateApplied)}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <ApplyLink url={app.jobPostingUrl} publisher={null} />
            {app.jobId && (
              <Link
                href={`/jobs/${app.jobId}`}
                className="inline-flex h-10 items-center gap-1.5 rounded-xl border border-border px-4 text-sm font-medium hover:bg-muted/60"
              >
                <ExternalLink className="h-4 w-4" /> Job page
              </Link>
            )}
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <ApplicationQuickPanel key={app.status + app.version} id={app.id} status={app.status} version={app.version} dateApplied={app.dateApplied} onChanged={load} />
          {app.status === "INTERVIEWING" && <InterviewPrepCard app={app} />}
          <CommunicationsCard appId={app.id} comms={comms} onChanged={load} />
          <EmailDraftCard applicationId={app.id} />
        </div>
        <div className="space-y-6">
          <NextStepCard app={app} onSaved={setApp} />
          <NotesCard app={app} onSaved={setApp} />
          <TimelineCard events={events} />
          <Button variant="ghost" className="w-full text-danger hover:bg-danger/10 hover:text-danger" onClick={() => setConfirmDelete(true)}>
            <Trash2 className="h-4 w-4" /> Stop tracking
          </Button>
        </div>
      </div>

      <Dialog
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="Stop tracking this application?"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button variant="danger" onClick={remove}>
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">Its timeline, notes and logged communications are deleted too. This can&apos;t be undone.</p>
      </Dialog>
    </div>
  );
}

function NextStepCard({ app, onSaved }: { app: Application; onSaved: (a: Application) => void }) {
  const toast = useToast();
  const [action, setAction] = useState(app.nextAction ?? "");
  const [date, setDate] = useState(app.nextActionDate ?? "");
  const [saving, setSaving] = useState(false);

  async function save(clear = false) {
    setSaving(true);
    try {
      const updated = await api.updateApplication(
        app.id,
        clear
          ? { clearNextAction: true, version: app.version }
          : { nextAction: action.trim() || undefined, nextActionDate: date || undefined, version: app.version },
      );
      onSaved(updated);
      if (clear) {
        setAction("");
        setDate("");
      }
      toast(clear ? "Next step cleared" : "Next step saved");
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card>
      <CardHeader title="Next step" icon={CalendarPlus} description="Shows on your calendar and dashboard." />
      <div className="space-y-3 p-5">
        <Input value={action} onChange={(e) => setAction(e.target.value)} placeholder="e.g. Technical interview" maxLength={255} aria-label="Next step" />
        <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} aria-label="Next step date" />
        <div className="flex gap-2">
          <Button size="sm" onClick={() => save(false)} loading={saving} disabled={!action.trim() && !date}>
            Save
          </Button>
          {(app.nextAction || app.nextActionDate) && (
            <Button size="sm" variant="ghost" onClick={() => save(true)} disabled={saving}>
              Clear
            </Button>
          )}
        </div>
      </div>
    </Card>
  );
}

function NotesCard({ app, onSaved }: { app: Application; onSaved: (a: Application) => void }) {
  const toast = useToast();
  const [notes, setNotes] = useState(app.notes ?? "");
  const [saving, setSaving] = useState(false);
  const dirty = notes !== (app.notes ?? "");

  async function save() {
    setSaving(true);
    try {
      onSaved(await api.updateApplication(app.id, { notes, version: app.version }));
      toast("Notes saved");
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card>
      <CardHeader title="Notes" icon={NotebookPen} />
      <div className="space-y-3 p-5">
        <Textarea rows={5} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Recruiter name, salary range, anything worth remembering" />
        {dirty && (
          <Button size="sm" onClick={save} loading={saving}>
            Save notes
          </Button>
        )}
      </div>
    </Card>
  );
}

function TimelineCard({ events }: { events: ApplicationEvent[] }) {
  return (
    <Card>
      <CardHeader title="Timeline" icon={History} />
      <ol className="relative space-y-5 p-5 pl-8">
        <span className="absolute bottom-6 left-[1.42rem] top-6 w-px bg-border" aria-hidden />
        {[...events].reverse().map((e) => (
          <li key={e.id} className="relative">
            <span className={cn("absolute -left-[0.95rem] top-1 h-2.5 w-2.5 rounded-full ring-4 ring-card", STAGE_STYLE[e.toStatus].dot)} />
            <p className="text-sm font-medium">{e.fromStatus ? `Moved to ${STAGE_LABEL[e.toStatus]}` : `Tracked as ${STAGE_LABEL[e.toStatus]}`}</p>
            {e.note && <p className="mt-0.5 text-sm text-muted-foreground">{e.note}</p>}
            <p className="mt-0.5 text-xs text-muted-foreground">
              {formatDay(e.changedAt, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" })}
            </p>
          </li>
        ))}
      </ol>
    </Card>
  );
}

const KINDS: CommunicationKind[] = ["EMAIL", "CALL", "MEETING", "MESSAGE", "OTHER"];

function CommunicationsCard({ appId, comms, onChanged }: { appId: number; comms: Communication[]; onChanged: () => void }) {
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    kind: "EMAIL" as CommunicationKind,
    direction: "RECEIVED" as CommunicationDirection,
    occurredAt: "",
    contactName: "",
    subject: "",
    summary: "",
  });
  const [saving, setSaving] = useState(false);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      await api.addCommunication(appId, {
        kind: form.kind,
        direction: form.direction,
        occurredAt: form.occurredAt ? new Date(form.occurredAt).toISOString() : new Date().toISOString(),
        contactName: form.contactName.trim() || null,
        subject: form.subject.trim() || null,
        summary: form.summary.trim() || null,
      });
      toast("Logged");
      setOpen(false);
      setForm({ ...form, occurredAt: "", contactName: "", subject: "", summary: "" });
      onChanged();
    } catch (err) {
      toast(errorMessage(err), "error");
    } finally {
      setSaving(false);
    }
  }

  async function remove(id: number) {
    try {
      await api.deleteCommunication(id);
      onChanged();
    } catch (err) {
      toast(errorMessage(err), "error");
    }
  }

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  return (
    <Card>
      <CardHeader
        title="Communications"
        icon={MessageSquare}
        description="Emails, calls and meetings with the employer."
        action={
          <Button size="sm" variant="outline" onClick={() => setOpen(true)}>
            <Plus className="h-3.5 w-3.5" /> Log
          </Button>
        }
      />
      {comms.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-muted-foreground">Nothing logged yet.</p>
      ) : (
        <ul className="divide-y divide-border">
          {comms.map((c) => (
            <li key={c.id} className="group flex gap-3 px-5 py-4">
              <span
                className={cn(
                  "mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg",
                  c.direction === "RECEIVED" ? "bg-success/10 text-success" : "bg-primary/10 text-primary",
                )}
              >
                {c.direction === "RECEIVED" ? <ArrowDownLeft className="h-4 w-4" /> : <ArrowUpRight className="h-4 w-4" />}
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">
                  {c.subject || COMMUNICATION_LABEL[c.kind]}
                  <span className="ml-2 text-xs font-normal text-muted-foreground">
                    {COMMUNICATION_LABEL[c.kind]} {c.direction === "RECEIVED" ? "from" : "to"} {c.contactName || "employer"} ·{" "}
                    {relativeTime(c.occurredAt)}
                  </span>
                </p>
                {c.summary && <p className="mt-1 whitespace-pre-line text-sm text-muted-foreground">{c.summary}</p>}
              </div>
              <button
                onClick={() => remove(c.id)}
                className="self-start rounded-lg p-1.5 text-muted-foreground opacity-0 transition hover:bg-danger/10 hover:text-danger focus:opacity-100 group-hover:opacity-100"
                aria-label="Delete"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </li>
          ))}
        </ul>
      )}

      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Log a communication"
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" form="log-comm" loading={saving}>
              Save
            </Button>
          </>
        }
      >
        <form id="log-comm" onSubmit={add} className="grid gap-4 sm:grid-cols-2">
          <Field label="Type">
            {(p) => (
              <Select {...p} value={form.kind} onChange={set("kind")}>
                {KINDS.map((k) => (
                  <option key={k} value={k}>
                    {COMMUNICATION_LABEL[k]}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="Direction">
            {(p) => (
              <Select {...p} value={form.direction} onChange={set("direction")}>
                <option value="RECEIVED">From them</option>
                <option value="SENT">From me</option>
              </Select>
            )}
          </Field>
          <Field label="When">{(p) => <Input {...p} type="datetime-local" value={form.occurredAt} onChange={set("occurredAt")} />}</Field>
          <Field label="Contact">{(p) => <Input {...p} value={form.contactName} onChange={set("contactName")} placeholder="Name" />}</Field>
          <Field label="Subject" className="sm:col-span-2">
            {(p) => <Input {...p} value={form.subject} onChange={set("subject")} />}
          </Field>
          <Field label="Summary" className="sm:col-span-2">
            {(p) => <Textarea {...p} rows={4} value={form.summary} onChange={set("summary")} />}
          </Field>
        </form>
      </Dialog>
    </Card>
  );
}

function InterviewPrepCard({ app }: { app: Application }) {
  const toast = useToast();
  const [prep, setPrep] = useState<InterviewPrep | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState(false);

  useEffect(() => {
    api.interviewPrep(app.id).then(setPrep).catch(() => setPrep(null));
  }, [app.id]);

  async function generate(refresh: boolean) {
    setConfirm(false);
    setBusy(true);
    try {
      setPrep(await api.generateInterviewPrep(app.id, refresh));
    } catch (e) {
      toast(errorMessage(e), e instanceof ApiError && e.isRateLimited ? "info" : "error");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="overflow-hidden">
      <CardHeader
        title="Interview prep"
        icon={Brain}
        description="A plan grounded in this advert and your resume."
        action={
          prep && (
            <Button size="sm" variant="ghost" onClick={() => setConfirm(true)} disabled={busy}>
              <RefreshCw className="h-3.5 w-3.5" /> Rewrite
            </Button>
          )
        }
      />
      <div className="p-5">
        {prep === undefined ? (
          <Skeleton className="h-24 w-full" />
        ) : app.manual || !app.jobId ? (
          <p className="text-sm text-muted-foreground">Interview prep needs the job advert. Add the job to ApplyMate and track it from there.</p>
        ) : busy ? (
          <div className="space-y-3">
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-20 w-full" />
          </div>
        ) : !prep ? (
          <div className="flex flex-col items-start gap-3">
            <p className="text-sm text-muted-foreground">Get the likely questions, what a strong answer covers, and the gaps to be ready to explain.</p>
            <Button variant="gradient" onClick={() => generate(false)}>
              <Lightbulb className="h-4 w-4" /> Plan my interview
            </Button>
          </div>
        ) : (
          <div className="space-y-6 text-sm">
            <p className="leading-relaxed">{prep.content.summary}</p>
            <div>
              <h4 className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Focus areas</h4>
              <div className="grid gap-3 sm:grid-cols-2">
                {prep.content.focusAreas.map((f) => (
                  <div key={f.topic} className="rounded-xl border border-border p-4">
                    <p className="font-medium">{f.topic}</p>
                    <p className="mt-1 text-muted-foreground">{f.why}</p>
                    <ul className="mt-2 list-disc space-y-1 pl-4 text-muted-foreground">
                      {f.actions.map((a) => (
                        <li key={a}>{a}</li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            </div>
            <div>
              <h4 className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Likely questions</h4>
              <div className="space-y-2">
                {prep.content.likelyQuestions.map((q) => (
                  <details key={q.question} className="group rounded-xl border border-border px-4 py-3 open:bg-muted/30">
                    <summary className="cursor-pointer list-none font-medium marker:hidden">{q.question}</summary>
                    <p className="mt-2 text-muted-foreground">{q.howToAnswer}</p>
                  </details>
                ))}
              </div>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Ask them</h4>
                <ul className="list-disc space-y-1 pl-4">
                  {prep.content.questionsToAsk.map((q) => (
                    <li key={q}>{q}</li>
                  ))}
                </ul>
              </div>
              <div>
                <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Be ready to explain</h4>
                <ul className="list-disc space-y-1 pl-4">
                  {prep.content.watchOuts.map((w) => (
                    <li key={w}>{w}</li>
                  ))}
                </ul>
              </div>
            </div>
            <p className="text-xs text-muted-foreground">Written {relativeTime(prep.generatedAt)}. Preparation advice, not a prediction of the outcome.</p>
          </div>
        )}
      </div>

      <Dialog
        open={confirm}
        onClose={() => setConfirm(false)}
        title="Write the plan again?"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirm(false)}>
              Keep current
            </Button>
            <Button onClick={() => generate(true)}>Rewrite</Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">This uses one of today&apos;s AI requests and replaces the current plan.</p>
      </Dialog>
    </Card>
  );
}
