"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Brain,
  CheckCircle2,
  Eye,
  FileText,
  KeyRound,
  PencilLine,
  RefreshCw,
  Save,
  Settings2,
  ShieldAlert,
  Sparkles,
  UploadCloud,
  UserRound,
  Wand2,
} from "lucide-react";
import { useToast } from "@/components/providers";
import { Button } from "@/components/ui/button";
import { ChipsInput } from "@/components/ui/chips";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Switch, Textarea } from "@/components/ui/form";
import { Alert, Card, CardHeader, PageHeader, Skeleton, Spinner } from "@/components/ui/misc";
import { ResumeEditor } from "@/components/resume/resume-editor";
import { announceAvatar, UserAvatar } from "@/components/ui/avatar";
import { IntegrationsCard } from "@/components/tools";
import { ResumePreview } from "@/components/resume/resume-preview";
import { api, ApiError, clearSession, saveSession } from "@/lib/api";
import type { Profile, ResumeContent, ResumeInfo, SkillProfile } from "@/lib/types";
import { cn, errorMessage, formatDay, relativeTime } from "@/lib/utils";

export default function ProfilePage() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = () =>
    api
      .profile()
      .then(setProfile)
      .catch((e) => setError(errorMessage(e)));

  useEffect(() => {
    reload();
  }, []);

  // Keep the sidebar avatar in step with the server, e.g. after signing in on a new device.
  useEffect(() => {
    if (profile) announceAvatar(profile.avatarUpdatedAt);
  }, [profile?.avatarUpdatedAt]); // eslint-disable-line react-hooks/exhaustive-deps

  if (error) return <Alert>{error}</Alert>;
  if (!profile) return <Spinner label="Loading profile" />;

  return (
    <div>
      <PageHeader title="Profile" description="Your resume powers match scores, recommendations and tailored documents." />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <ResumeCard profile={profile} onChanged={reload} />
          {profile.resume && <SkillProfileCard key={profile.skillProfile?.generatedAt ?? "none"} profile={profile} onChanged={reload} />}
          {profile.resume && <MasterResumeCard aiEnabled={profile.ai.enabled} />}
        </div>
        <div className="space-y-6">
          <AccountCard profile={profile} onSaved={setProfile} />
          <PreferencesCard profile={profile} onSaved={setProfile} />
          <IntegrationsCard calendarEnabled={profile.calendarFeedEnabled} onChanged={reload} />
          <SecurityCard />
        </div>
      </div>
    </div>
  );
}

function ResumeCard({ profile, onChanged }: { profile: Profile; onChanged: () => void }) {
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [warning, setWarning] = useState<string | null>(null);

  async function upload(file: File) {
    if (file.size > 5 * 1024 * 1024) {
      toast("Resumes must be 5 MB or smaller.", "error");
      return;
    }
    setUploading(true);
    setWarning(null);
    try {
      const res = await api.uploadResume(file);
      setWarning(res.warning);
      toast(res.skillProfile ? "Resume uploaded and analysed" : "Resume uploaded");
      onChanged();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setUploading(false);
    }
  }

  const { ai } = profile;
  const usedPct = ai.dailyLimit ? Math.min(100, (ai.usedToday / ai.dailyLimit) * 100) : 0;

  return (
    <Card>
      <CardHeader title="Resume" icon={FileText} description="PDF, Word (.docx) or plain text, up to 5 MB. We keep the text, not the file." />
      <div className="space-y-4 p-5">
        {profile.resume && (
          <div className="flex items-center gap-3 rounded-xl border border-border bg-muted/30 p-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-success/10 text-success">
              <CheckCircle2 className="h-5 w-5" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{profile.resume.fileName}</p>
              <p className="text-xs text-muted-foreground">
                Uploaded {relativeTime(profile.resume.uploadedAt)} · {profile.resume.characters.toLocaleString()} characters read
              </p>
            </div>
          </div>
        )}
        <button
          type="button"
          onClick={() => input.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            const file = e.dataTransfer.files[0];
            if (file) upload(file);
          }}
          disabled={uploading}
          className={cn(
            "flex w-full flex-col items-center justify-center rounded-2xl border-2 border-dashed border-border px-6 py-10 text-center transition-colors hover:border-primary/50 hover:bg-primary/5",
            dragOver && "border-primary bg-primary/5",
          )}
        >
          {uploading ? (
            <>
              <RefreshCw className="h-6 w-6 animate-spin text-primary" />
              <p className="mt-3 text-sm font-medium">Reading your resume…</p>
              <p className="text-xs text-muted-foreground">Extracting skills can take a few seconds.</p>
            </>
          ) : (
            <>
              <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-primary/15 to-accent/15 text-primary">
                <UploadCloud className="h-6 w-6" />
              </span>
              <p className="mt-3 text-sm font-medium">{profile.resume ? "Upload a new version" : "Drop your resume here, or click to choose"}</p>
              <p className="text-xs text-muted-foreground">Your master resume carries over to the new version.</p>
            </>
          )}
        </button>
        <input
          ref={input}
          type="file"
          accept=".pdf,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) upload(f);
            e.target.value = "";
          }}
        />
        {warning && <Alert tone="warning">{warning}</Alert>}
        <ResumeVersions key={profile.resume?.id ?? "none"} onChanged={onChanged} />

        <div className="rounded-xl bg-muted/40 p-3">
          <div className="flex items-center justify-between text-xs">
            <span className="flex items-center gap-1.5 font-medium">
              <Sparkles className="h-3.5 w-3.5 text-primary" /> AI requests today
            </span>
            <span className="tabular-nums text-muted-foreground">{ai.enabled ? `${ai.usedToday} of ${ai.dailyLimit}` : "AI is turned off on this server"}</span>
          </div>
          {ai.enabled && (
            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted">
              <div className={cn("h-full rounded-full", usedPct > 80 ? "bg-warning" : "bg-primary")} style={{ width: `${usedPct}%` }} />
            </div>
          )}
        </div>
      </div>
    </Card>
  );
}

function SkillProfileCard({ profile, onChanged }: { profile: Profile; onChanged: () => void }) {
  const toast = useToast();
  const sp = profile.skillProfile;
  const [form, setForm] = useState<Pick<SkillProfile, "skills" | "jobTitles" | "locations" | "seniority" | "summary">>({
    skills: sp?.skills ?? [],
    jobTitles: sp?.jobTitles ?? [],
    locations: sp?.locations ?? [],
    seniority: sp?.seniority ?? "",
    summary: sp?.summary ?? "",
  });
  const [saving, setSaving] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  async function save() {
    setSaving(true);
    try {
      await api.updateSkillProfile(form);
      toast("Skill profile saved");
      onChanged();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setSaving(false);
    }
  }

  async function refresh() {
    setRefreshing(true);
    try {
      await api.refreshSkillProfile();
      toast("Re-read your resume");
      onChanged();
    } catch (e) {
      toast(errorMessage(e), e instanceof ApiError && e.isRateLimited ? "info" : "error");
    } finally {
      setRefreshing(false);
    }
  }

  return (
    <Card>
      <CardHeader
        title="Skill profile"
        icon={Brain}
        description="What we read from your resume. Edit anything: you know your career better than the AI."
        action={
          <Button variant="ghost" size="sm" onClick={refresh} loading={refreshing} disabled={!profile.ai.enabled}>
            {!refreshing && <RefreshCw className="h-3.5 w-3.5" />} Re-read
          </Button>
        }
      />
      <div className="space-y-4 p-5">
        {!sp && <Alert tone="info">No skill profile yet. Re-read your resume with AI, or fill these in yourself and save.</Alert>}
        <div>
          <label className="label">Skills</label>
          <ChipsInput label="Skills" value={form.skills} onChange={(skills) => setForm({ ...form, skills })} placeholder="Java, SQL, React…" />
        </div>
        <div>
          <label className="label">Job titles to search for</label>
          <ChipsInput
            label="Job titles"
            max={10}
            value={form.jobTitles}
            onChange={(jobTitles) => setForm({ ...form, jobTitles })}
            placeholder="Software Developer…"
          />
          <p className="mt-1.5 text-xs text-muted-foreground">The first two drive your recommendations.</p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label">Locations</label>
            <ChipsInput label="Locations" max={10} value={form.locations} onChange={(locations) => setForm({ ...form, locations })} placeholder="Edmonton…" />
          </div>
          <Field label="Seniority">
            {(p) => <Input {...p} value={form.seniority ?? ""} onChange={(e) => setForm({ ...form, seniority: e.target.value })} placeholder="junior" />}
          </Field>
        </div>
        <Field label="Summary">
          {(p) => <Textarea {...p} rows={3} value={form.summary ?? ""} onChange={(e) => setForm({ ...form, summary: e.target.value })} />}
        </Field>
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground">{sp && `Generated ${relativeTime(sp.generatedAt)}${sp.modelUsed && sp.modelUsed !== "manual" ? ` by ${sp.modelUsed}` : ""}`}</p>
          <Button onClick={save} loading={saving}>
            <Save className="h-4 w-4" /> Save profile
          </Button>
        </div>
      </div>
    </Card>
  );
}

function MasterResumeCard({ aiEnabled }: { aiEnabled: boolean }) {
  const toast = useToast();
  const [content, setContent] = useState<ResumeContent | null>(null);
  const [saved, setSaved] = useState<ResumeContent | null>(null);
  const [isSaved, setIsSaved] = useState(false);
  const [version, setVersion] = useState<number | undefined>(undefined);
  const [view, setView] = useState<"edit" | "preview">("edit");
  const [saving, setSaving] = useState(false);
  const [filling, setFilling] = useState(false);

  useEffect(() => {
    api.masterResume().then((m) => {
      setContent(m.content);
      setSaved(m.content);
      setIsSaved(m.saved);
      setVersion(m.version);
    });
  }, []);

  const dirty = JSON.stringify(content) !== JSON.stringify(saved);

  async function save() {
    if (!content) return;
    setSaving(true);
    try {
      const m = await api.saveMasterResume(content, version);
      setContent(m.content);
      setSaved(m.content);
      setIsSaved(true);
      setVersion(m.version);
      toast("Master resume saved");
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setSaving(false);
    }
  }

  async function fill() {
    setFilling(true);
    try {
      const m = await api.fillMasterResume();
      setContent(m.content);
      toast("Filled from your CV. Check it, then save.", "info");
    } catch (e) {
      toast(errorMessage(e), e instanceof ApiError && e.isRateLimited ? "info" : "error");
    } finally {
      setFilling(false);
    }
  }

  return (
    <Card>
      <CardHeader
        title="Master resume"
        icon={PencilLine}
        description="Your base resume. Every resume made for a job starts from this, and editing those never changes it."
      />
      <div className="p-5">
        {!content ? (
          <Skeleton className="h-64 w-full" />
        ) : (
          <>
            <div className="mb-5 flex flex-wrap items-center justify-between gap-2">
              <div className="grid grid-cols-2 gap-1 rounded-xl bg-muted p-1">
                {(
                  [
                    ["edit", "Edit", PencilLine],
                    ["preview", "Preview", Eye],
                  ] as const
                ).map(([value, label, Icon]) => (
                  <button
                    key={value}
                    onClick={() => setView(value)}
                    className={cn(
                      "flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium",
                      view === value ? "bg-card shadow-sm" : "text-muted-foreground",
                    )}
                  >
                    <Icon className="h-3.5 w-3.5" /> {label}
                  </button>
                ))}
              </div>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={fill} loading={filling} disabled={!aiEnabled}>
                  {!filling && <Wand2 className="h-3.5 w-3.5" />} Fill from uploaded CV
                </Button>
                <Button size="sm" onClick={save} loading={saving} disabled={!dirty}>
                  <Save className="h-3.5 w-3.5" /> {dirty ? "Save master" : isSaved ? "Saved" : "Save master"}
                </Button>
              </div>
            </div>
            {!isSaved && !dirty && (
              <Alert tone="info" className="mb-5">
                No master resume yet. Fill it from your CV with one AI request, or type it in. Nothing is saved until you press Save.
              </Alert>
            )}
            {view === "edit" ? <ResumeEditor value={content} onChange={setContent} /> : <ResumePreview content={content} />}
          </>
        )}
      </div>
    </Card>
  );
}

function AccountCard({ profile, onSaved }: { profile: Profile; onSaved: (p: Profile) => void }) {
  const toast = useToast();
  const [name, setName] = useState(profile.name ?? "");
  const [saving, setSaving] = useState(false);

  async function save() {
    setSaving(true);
    try {
      const p = await api.updateProfile({ name });
      onSaved(p);
      // Keep the sidebar name in step with the profile.
      const raw = localStorage.getItem("applymate_user");
      if (raw) localStorage.setItem("applymate_user", JSON.stringify({ ...JSON.parse(raw), name: p.name }));
      toast("Saved");
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card>
      <CardHeader title="Account" icon={UserRound} />
      <div className="space-y-4 p-5">
        <AvatarPicker name={profile.name || profile.email} version={profile.avatarUpdatedAt} onChanged={(v) => onSaved({ ...profile, avatarUpdatedAt: v })} />
        <Field label="Name">{(p) => <Input {...p} value={name} onChange={(e) => setName(e.target.value)} />}</Field>
        <Field label="Email">{(p) => <Input {...p} value={profile.email} disabled />}</Field>
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground">Member since {formatDay(profile.memberSince, { month: "long", year: "numeric" })}</p>
          <Button size="sm" onClick={save} loading={saving} disabled={name === (profile.name ?? "")}>
            Save
          </Button>
        </div>
      </div>
    </Card>
  );
}

function PreferencesCard({ profile, onSaved }: { profile: Profile; onSaved: (p: Profile) => void }) {
  const toast = useToast();
  const [location, setLocation] = useState(profile.preferredLocation ?? "");

  async function update(body: Parameters<typeof api.updateProfile>[0], message = "Preferences saved") {
    try {
      onSaved(await api.updateProfile(body));
      toast(message);
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  }

  return (
    <Card>
      <CardHeader title="Job preferences" icon={Settings2} />
      <div className="space-y-5 p-5">
        <Field label="Preferred location" hint="Blank uses the location on your resume.">
          {(p) => (
            <div className="flex gap-2">
              <Input {...p} value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Edmonton, AB" />
              <Button
                variant="outline"
                onClick={() => update({ preferredLocation: location })}
                disabled={location === (profile.preferredLocation ?? "")}
              >
                Save
              </Button>
            </div>
          )}
        </Field>
        <Field label="Weekly application goal" hint="Drives the streak on your dashboard.">
          {(p) => (
            <Input
              {...p}
              type="number"
              min={1}
              max={50}
              defaultValue={profile.weeklyGoal}
              onBlur={(e) => {
                const v = Number(e.target.value);
                if (v >= 1 && v <= 50 && v !== profile.weeklyGoal) update({ weeklyGoal: v }, "Weekly goal saved");
              }}
            />
          )}
        </Field>
        <Switch
          label="Include remote jobs"
          description="Show remote listings in your recommendations."
          checked={profile.includeRemote}
          onChange={(v) => update({ includeRemote: v })}
        />
        <Switch
          label="Email reminders"
          description="A heads-up the day before a next step you've set."
          checked={profile.emailReminders}
          onChange={(v) => update({ emailReminders: v }, v ? "Reminders on" : "Reminders off")}
        />
      </div>
    </Card>
  );
}

function SecurityCard() {
  const toast = useToast();
  const router = useRouter();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [saving, setSaving] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");

  async function change(e: React.FormEvent) {
    e.preventDefault();
    if (next.length < 8) return toast("Use at least 8 characters.", "error");
    setSaving(true);
    try {
      saveSession(await api.changePassword(current, next));
      setCurrent("");
      setNext("");
      toast("Password changed. Other devices have been signed out.");
    } catch (err) {
      toast(errorMessage(err), "error");
    } finally {
      setSaving(false);
    }
  }

  async function deleteAccount() {
    setDeleting(true);
    setDeleteError("");
    try {
      await api.deleteAccount(deletePassword);
      clearSession();
      router.replace("/");
    } catch (err) {
      setDeleteError(errorMessage(err));
      setDeleting(false);
    }
  }

  return (
    <Card>
      <CardHeader title="Security" icon={KeyRound} />
      <form onSubmit={change} className="space-y-4 p-5">
        <Field label="Current password">
          {(p) => <Input {...p} type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />}
        </Field>
        <Field label="New password">
          {(p) => <Input {...p} type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />}
        </Field>
        <Button type="submit" size="sm" loading={saving} disabled={!current || !next}>
          Change password
        </Button>
      </form>
      <div className="border-t border-border p-5">
        <p className="flex items-center gap-2 text-sm font-medium text-danger">
          <ShieldAlert className="h-4 w-4" /> Delete account
        </p>
        <p className="mt-1 text-xs text-muted-foreground">Permanently removes your resume, documents, applications and everything else.</p>
        <Button variant="outline" size="sm" className="mt-3 text-danger hover:bg-danger/10" onClick={() => setDeleteOpen(true)}>
          Delete my account
        </Button>
      </div>

      <Dialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        title="Delete your account?"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleteOpen(false)}>
              Cancel
            </Button>
            <Button variant="danger" onClick={deleteAccount} loading={deleting} disabled={!deletePassword}>
              Delete forever
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">This can&apos;t be undone. Enter your password to confirm.</p>
          {deleteError && <Alert>{deleteError}</Alert>}
          <Input type="password" autoComplete="current-password" value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} aria-label="Password" />
        </div>
      </Dialog>
    </Card>
  );
}

function AvatarPicker({ name, version, onChanged }: { name: string; version: string | null; onChanged: (v: string | null) => void }) {
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  async function upload(file: File) {
    setBusy(true);
    try {
      const { avatarUpdatedAt } = await api.uploadAvatar(file);
      onChanged(avatarUpdatedAt);
      announceAvatar(avatarUpdatedAt);
      toast("Profile picture updated");
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    try {
      await api.deleteAvatar();
      onChanged(null);
      announceAvatar(null);
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  }

  return (
    <div className="flex items-center gap-4">
      <UserAvatar name={name} version={version} className="h-16 w-16 text-xl" />
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant="outline" loading={busy} onClick={() => input.current?.click()}>
          {version ? "Change photo" : "Add a photo"}
        </Button>
        {version && (
          <Button size="sm" variant="ghost" onClick={remove}>
            Remove
          </Button>
        )}
      </div>
      <input
        ref={input}
        type="file"
        accept="image/png,image/jpeg,image/gif,image/bmp"
        className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) upload(f);
          e.target.value = "";
        }}
      />
    </div>
  );
}

function ResumeVersions({ onChanged }: { onChanged: () => void }) {
  const toast = useToast();
  const [list, setList] = useState<ResumeInfo[] | null>(null);
  const [busy, setBusy] = useState<number | null>(null);

  useEffect(() => {
    api.resumes().then(setList).catch(() => setList([]));
  }, []);

  if (!list || list.length < 2) return null;

  async function activate(id: number) {
    setBusy(id);
    try {
      await api.activateResume(id);
      toast("Switched resume. Match scores now use this version.");
      onChanged();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(null);
    }
  }

  return (
    <div>
      <p className="label">Earlier versions</p>
      <ul className="divide-y divide-border rounded-xl border border-border">
        {list
          .filter((r) => !r.active)
          .map((r) => (
            <li key={r.id} className="flex items-center justify-between gap-3 px-3 py-2.5 text-sm">
              <span className="min-w-0">
                <span className="block truncate font-medium">{r.fileName}</span>
                <span className="text-xs text-muted-foreground">Uploaded {relativeTime(r.uploadedAt)}</span>
              </span>
              <Button size="sm" variant="ghost" loading={busy === r.id} onClick={() => activate(r.id)}>
                Use this version
              </Button>
            </li>
          ))}
      </ul>
    </div>
  );
}
