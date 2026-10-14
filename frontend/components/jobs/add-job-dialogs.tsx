"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ClipboardPaste, Link2 } from "lucide-react";
import { useToast } from "@/components/providers";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { Alert } from "@/components/ui/misc";
import { api } from "@/lib/api";
import type { ApplicationStatus, JobDetail } from "@/lib/types";
import { cn, errorMessage, STAGE_LABEL, STAGES, today } from "@/lib/utils";

/** Paste a link (fetched server-side) or the advert text (for sites that block fetching). */
export function PasteJobDialog({ open, onClose, onAdded }: { open: boolean; onClose: () => void; onAdded: (d: JobDetail) => void }) {
  const toast = useToast();
  const [mode, setMode] = useState<"link" | "text">("link");
  const [url, setUrl] = useState("");
  const [form, setForm] = useState({ title: "", company: "", location: "", url: "", description: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function reset() {
    setUrl("");
    setForm({ title: "", company: "", location: "", url: "", description: "" });
    setError("");
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const detail =
        mode === "link"
          ? await api.pasteLink(url.trim())
          : await api.pasteText({
              title: form.title.trim(),
              company: form.company.trim(),
              location: form.location.trim() || undefined,
              url: form.url.trim() || undefined,
              description: form.description.trim(),
            });
      toast(`Added ${detail.job.title}`);
      onAdded(detail);
      reset();
      onClose();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Add a job"
      description="From LinkedIn, Indeed, a company careers page, anywhere."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="paste-job" variant="gradient" loading={loading}>
            Add job
          </Button>
        </>
      }
    >
      <div className="mb-5 grid grid-cols-2 gap-1 rounded-xl bg-muted p-1">
        {(
          [
            ["link", "Paste a link", Link2],
            ["text", "Paste the text", ClipboardPaste],
          ] as const
        ).map(([value, label, Icon]) => (
          <button
            key={value}
            type="button"
            onClick={() => {
              setMode(value);
              setError("");
            }}
            className={cn(
              "flex items-center justify-center gap-2 rounded-lg py-2 text-sm font-medium transition-all",
              mode === value ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground",
            )}
          >
            <Icon className="h-4 w-4" />
            {label}
          </button>
        ))}
      </div>

      <form id="paste-job" onSubmit={submit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        {mode === "link" ? (
          <Field label="Job posting URL" hint="We read the page once. If a site blocks us, paste the text instead.">
            {(p) => <Input {...p} type="url" required autoFocus value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://..." />}
          </Field>
        ) : (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Job title">{(p) => <Input {...p} required value={form.title} onChange={set("title")} />}</Field>
              <Field label="Company">{(p) => <Input {...p} required value={form.company} onChange={set("company")} />}</Field>
              <Field label="Location">{(p) => <Input {...p} value={form.location} onChange={set("location")} />}</Field>
              <Field label="Link (optional)">{(p) => <Input {...p} type="url" value={form.url} onChange={set("url")} placeholder="https://..." />}</Field>
            </div>
            <Field label="Job description" hint="Copy the whole advert. Adding text costs no AI quota.">
              {(p) => <Textarea {...p} required rows={8} minLength={50} value={form.description} onChange={set("description")} />}
            </Field>
          </>
        )}
      </form>
    </Dialog>
  );
}

/** Record an application sent somewhere the app never saw. Only company and position are required. */
export function LogApplicationDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const toast = useToast();
  const router = useRouter();
  const [form, setForm] = useState({
    companyName: "",
    jobTitle: "",
    location: "",
    jobPostingUrl: "",
    status: "APPLIED" as ApplicationStatus,
    dateApplied: today(),
    notes: "",
  });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const app = await api.logApplication({
        companyName: form.companyName.trim(),
        jobTitle: form.jobTitle.trim(),
        location: form.location.trim() || undefined,
        jobPostingUrl: form.jobPostingUrl.trim() || undefined,
        status: form.status,
        dateApplied: form.dateApplied,
        notes: form.notes.trim() || undefined,
      });
      toast("Application logged");
      onClose();
      router.push(`/applications/${app.id}`);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Log an application"
      description="For a job you applied to outside ApplyMate."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="log-application" variant="gradient" loading={loading}>
            Save
          </Button>
        </>
      }
    >
      <form id="log-application" onSubmit={submit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Company">{(p) => <Input {...p} required autoFocus value={form.companyName} onChange={set("companyName")} />}</Field>
          <Field label="Position">{(p) => <Input {...p} required value={form.jobTitle} onChange={set("jobTitle")} />}</Field>
          <Field label="Location">{(p) => <Input {...p} value={form.location} onChange={set("location")} />}</Field>
          <Field label="Date applied">{(p) => <Input {...p} type="date" value={form.dateApplied} onChange={set("dateApplied")} />}</Field>
          <Field label="Stage">
            {(p) => (
              <Select {...p} value={form.status} onChange={set("status")}>
                {STAGES.map((s) => (
                  <option key={s} value={s}>
                    {STAGE_LABEL[s]}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="Link (optional)">
            {(p) => <Input {...p} type="url" value={form.jobPostingUrl} onChange={set("jobPostingUrl")} placeholder="https://..." />}
          </Field>
        </div>
        <Field label="Notes">{(p) => <Textarea {...p} rows={3} value={form.notes} onChange={set("notes")} />}</Field>
      </form>
    </Dialog>
  );
}
