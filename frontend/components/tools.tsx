"use client";

import { useEffect, useRef, useState } from "react";
import {
  Bookmark,
  CalendarSync,
  Copy,
  Download,
  Flame,
  Mail,
  PlugZap,
  RefreshCw,
  Send,
  Target,
  Upload,
} from "lucide-react";
import { useToast } from "@/components/providers";
import { Button } from "@/components/ui/button";
import { Input, Select, Textarea } from "@/components/ui/form";
import { Alert, Card, CardHeader, Skeleton } from "@/components/ui/misc";
import { api, ApiError } from "@/lib/api";
import type { EmailPurpose, ImportResult, Progress } from "@/lib/types";
import { cn, errorMessage, parseDay } from "@/lib/utils";

async function copy(text: string, toast: (m: string, t?: "success" | "error" | "info") => void, what = "Copied") {
  try {
    await navigator.clipboard.writeText(text);
    toast(what);
  } catch {
    toast("Couldn't copy. Select the text and copy it yourself.", "error");
  }
}

/** Applications this week against the weekly goal, the streak, and the last eight weeks. */
export function WeeklyGoalCard() {
  const [data, setData] = useState<Progress | null>(null);
  useEffect(() => {
    api.progress().then(setData).catch(() => setData(null));
  }, []);

  if (!data) return <Skeleton className="h-[188px] rounded-2xl" />;
  const pct = Math.min(100, Math.round((data.thisWeek / Math.max(1, data.goal)) * 100));
  const max = Math.max(data.goal, ...data.recentWeeks.map((w) => w.count), 1);

  return (
    <Card className="p-5">
      <div className="flex items-start justify-between">
        <div>
          <p className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
            <Target className="h-4 w-4 text-primary" /> Weekly goal
          </p>
          <p className="mt-2 text-3xl font-semibold tabular-nums tracking-tight">
            {data.thisWeek}
            <span className="text-lg text-muted-foreground"> / {data.goal}</span>
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            {data.thisWeek >= data.goal ? "Goal met this week. Nice work." : `${data.goal - data.thisWeek} more to hit this week's goal.`}
          </p>
        </div>
        <div
          className={cn(
            "flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold",
            data.streakWeeks > 0 ? "bg-warning/15 text-warning" : "bg-muted text-muted-foreground",
          )}
          title="Consecutive weeks meeting your goal"
        >
          <Flame className="h-4 w-4" /> {data.streakWeeks} week{data.streakWeeks === 1 ? "" : "s"}
        </div>
      </div>
      <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-muted">
        <div className="h-full rounded-full bg-gradient-to-r from-primary to-accent transition-all duration-700" style={{ width: `${pct}%` }} />
      </div>
      <div className="mt-4 flex h-12 items-end gap-1.5" aria-label="Applications in the last eight weeks">
        {data.recentWeeks.map((w, i) => (
          <div key={w.weekStart} className="group relative flex h-full flex-1 flex-col justify-end">
            <div
              className={cn(
                "w-full rounded-t-md transition-all",
                w.count >= data.goal ? "bg-primary" : "bg-primary/30",
                i === data.recentWeeks.length - 1 && "ring-2 ring-primary/30",
              )}
              style={{ height: `${Math.max(w.count > 0 ? 10 : 4, (w.count / max) * 100)}%` }}
              title={`Week of ${parseDay(w.weekStart).toLocaleDateString(undefined, { month: "short", day: "numeric" })}: ${w.count}`}
            />
          </div>
        ))}
      </div>
    </Card>
  );
}

const PURPOSES: [EmailPurpose, string][] = [
  ["FOLLOW_UP", "Follow up"],
  ["THANK_YOU", "Thank you after an interview"],
  ["WITHDRAW", "Withdraw"],
  ["ACCEPT_OFFER", "Accept the offer"],
  ["DECLINE_OFFER", "Decline the offer"],
];

/** AI-drafted emails to copy into your own mail app. Nothing is ever sent from here. */
export function EmailDraftCard({ applicationId, aiEnabled = true }: { applicationId: number; aiEnabled?: boolean }) {
  const toast = useToast();
  const [purpose, setPurpose] = useState<EmailPurpose>("FOLLOW_UP");
  const [extra, setExtra] = useState("");
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);

  async function draft() {
    setBusy(true);
    try {
      const d = await api.emailDraft(applicationId, purpose, extra.trim() || undefined);
      setSubject(d.subject);
      setBody(d.body);
    } catch (e) {
      toast(errorMessage(e), e instanceof ApiError && e.isRateLimited ? "info" : "error");
    } finally {
      setBusy(false);
    }
  }

  const mailto = `mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;

  return (
    <Card>
      <CardHeader title="Draft an email" icon={Mail} description="A starting point you edit and send yourself. ApplyMate never sends it." />
      <div className="space-y-3 p-5">
        <div className="grid gap-2 sm:grid-cols-[220px_1fr_auto]">
          <Select value={purpose} onChange={(e) => setPurpose(e.target.value as EmailPurpose)} aria-label="Purpose">
            {PURPOSES.map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </Select>
          <Input value={extra} maxLength={300} onChange={(e) => setExtra(e.target.value)} placeholder="Anything to mention? (optional)" />
          <Button onClick={draft} loading={busy} disabled={!aiEnabled}>
            {!busy && <Send className="h-4 w-4" />} Draft
          </Button>
        </div>
        {(subject || body) && (
          <div className="animate-fade-in space-y-2">
            <Input value={subject} onChange={(e) => setSubject(e.target.value)} aria-label="Subject" />
            <Textarea rows={8} value={body} onChange={(e) => setBody(e.target.value)} aria-label="Body" />
            <div className="flex flex-wrap gap-2">
              <Button size="sm" variant="outline" onClick={() => copy(`Subject: ${subject}\n\n${body}`, toast)}>
                <Copy className="h-3.5 w-3.5" /> Copy
              </Button>
              <a href={mailto} className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-border px-3 text-xs font-medium hover:bg-muted/60">
                <Mail className="h-3.5 w-3.5" /> Open in mail app
              </a>
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}

/** Calendar subscription, CSV import/export and the "Add to ApplyMate" bookmarklet. */
export function IntegrationsCard({ calendarEnabled, onChanged }: { calendarEnabled: boolean; onChanged: () => void }) {
  const toast = useToast();
  const [feedUrl, setFeedUrl] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [importResult, setImportResult] = useState<ImportResult | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const [origin, setOrigin] = useState("");
  useEffect(() => setOrigin(window.location.origin), []);

  async function run(label: string, action: () => Promise<void>) {
    setBusy(label);
    try {
      await action();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(null);
    }
  }

  // A bookmarklet only navigates to the app with the page URL: no script runs on the job site.
  const bookmarklet = `javascript:location.href='${origin}/jobs?add='+encodeURIComponent(location.href)`;

  return (
    <Card>
      <CardHeader title="Integrations" icon={PlugZap} />
      <div className="divide-y divide-border">
        <section className="space-y-3 p-5">
          <p className="flex items-center gap-2 text-sm font-medium">
            <CalendarSync className="h-4 w-4 text-primary" /> Calendar subscription
          </p>
          <p className="text-xs text-muted-foreground">
            Your next steps in Google Calendar, Outlook or Apple Calendar, updated automatically. Keep the link private: anyone with it can see the events.
          </p>
          {feedUrl ? (
            <div className="space-y-2">
              <div className="flex gap-2">
                <Input readOnly value={feedUrl} onFocus={(e) => e.target.select()} className="font-mono text-xs" aria-label="Calendar feed URL" />
                <Button size="sm" variant="outline" className="h-[42px]" onClick={() => copy(feedUrl, toast, "Link copied")}>
                  <Copy className="h-4 w-4" />
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">Shown once. If you lose it, make a new one (the old link stops working).</p>
            </div>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              variant={calendarEnabled ? "outline" : "primary"}
              loading={busy === "feed"}
              onClick={() =>
                run("feed", async () => {
                  setFeedUrl((await api.rotateCalendarFeed()).url);
                  onChanged();
                })
              }
            >
              {calendarEnabled ? <RefreshCw className="h-3.5 w-3.5" /> : null}
              {calendarEnabled ? "Make a new link" : "Create calendar link"}
            </Button>
            {calendarEnabled && (
              <Button
                size="sm"
                variant="ghost"
                onClick={() =>
                  run("revoke", async () => {
                    await api.revokeCalendarFeed();
                    setFeedUrl(null);
                    onChanged();
                    toast("Calendar link turned off");
                  })
                }
              >
                Turn off
              </Button>
            )}
          </div>
        </section>

        <section className="space-y-3 p-5">
          <p className="text-sm font-medium">Spreadsheet import and export</p>
          <p className="text-xs text-muted-foreground">
            Moving from a spreadsheet? Import a CSV with Company and Position columns (Status, Date applied, Link and Notes are optional).
          </p>
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              variant="outline"
              loading={busy === "export"}
              onClick={() =>
                run("export", async () => {
                  const blob = await api.exportApplications();
                  const url = URL.createObjectURL(blob);
                  const a = document.createElement("a");
                  a.href = url;
                  a.download = `applymate-applications-${new Date().toISOString().slice(0, 10)}.csv`;
                  a.click();
                  URL.revokeObjectURL(url);
                })
              }
            >
              <Download className="h-3.5 w-3.5" /> Export CSV
            </Button>
            <Button size="sm" variant="outline" loading={busy === "import"} onClick={() => fileInput.current?.click()}>
              <Upload className="h-3.5 w-3.5" /> Import CSV
            </Button>
            <input
              ref={fileInput}
              type="file"
              accept=".csv,text/csv"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                e.target.value = "";
                if (f)
                  run("import", async () => {
                    const r = await api.importApplications(f);
                    setImportResult(r);
                    toast(`Imported ${r.imported} application${r.imported === 1 ? "" : "s"}`);
                  });
              }}
            />
          </div>
          {importResult && importResult.skipped.length > 0 && (
            <Alert tone="warning">
              <p className="font-medium">
                {importResult.skipped.length} row{importResult.skipped.length === 1 ? " was" : "s were"} skipped:
              </p>
              <ul className="mt-1 list-disc space-y-0.5 pl-5 text-xs">
                {importResult.skipped.slice(0, 10).map((s) => (
                  <li key={s.line}>
                    Line {s.line}: {s.reason}
                  </li>
                ))}
              </ul>
            </Alert>
          )}
        </section>

        <section className="space-y-3 p-5">
          <p className="flex items-center gap-2 text-sm font-medium">
            <Bookmark className="h-4 w-4 text-primary" /> Add to ApplyMate from any job page
          </p>
          <p className="text-xs text-muted-foreground">Drag this button to your bookmarks bar. Click it on any job posting to add the job here.</p>
          {origin && (
            <a
              href={bookmarklet}
              onClick={(e) => {
                e.preventDefault();
                toast("Drag it to your bookmarks bar instead of clicking it here.", "info");
              }}
              className="inline-flex cursor-grab items-center gap-2 rounded-xl bg-gradient-to-r from-primary to-accent px-4 py-2 text-sm font-medium text-white shadow-glow"
            >
              <Bookmark className="h-4 w-4" /> Add to ApplyMate
            </a>
          )}
        </section>
      </div>
    </Card>
  );
}
