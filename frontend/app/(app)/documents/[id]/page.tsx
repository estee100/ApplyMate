"use client";

import Link from "next/link";
import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Download, Eye, PencilLine, RotateCcw, Save, ShieldCheck, Trash2 } from "lucide-react";
import { useToast } from "@/components/providers";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Alert, Badge, Spinner } from "@/components/ui/misc";
import { CoverLetterEditor, ResumeEditor } from "@/components/resume/resume-editor";
import { CoverLetterPreview, ResumePreview } from "@/components/resume/resume-preview";
import { api, ApiError } from "@/lib/api";
import type { CoverLetterContent, GeneratedDocument, ResumeContent } from "@/lib/types";
import { cn, errorMessage, relativeTime } from "@/lib/utils";

export default function DocumentPage({ params }: { params: Promise<{ id: string }> }) {
  const id = Number(use(params).id);
  const router = useRouter();
  const toast = useToast();
  const [doc, setDoc] = useState<GeneratedDocument | null>(null);
  const [draft, setDraft] = useState<ResumeContent | CoverLetterContent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [tab, setTab] = useState<"edit" | "preview">("edit");
  const [confirm, setConfirm] = useState<"reset" | "delete" | null>(null);

  useEffect(() => {
    api
      .document(id)
      .then((d) => {
        setDoc(d);
        setDraft(d.content);
      })
      .catch((e) => setError(errorMessage(e)));
  }, [id]);

  const dirty = !!doc && !!draft && JSON.stringify(draft) !== JSON.stringify(doc.content);

  // Don't let a stray tab close lose an edit.
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  async function save() {
    if (!draft) return;
    setSaving(true);
    try {
      const saved = await api.saveDocument(id, draft, doc?.version);
      setDoc(saved);
      setDraft(saved.content);
      toast("Saved");
    } catch (e) {
      // On a conflict the draft is kept so nothing typed is lost; reloading shows the newer copy.
      toast(e instanceof ApiError && e.isConflict ? `${errorMessage(e)} Your edits are still here.` : errorMessage(e), "error");
    } finally {
      setSaving(false);
    }
  }

  async function reset() {
    setConfirm(null);
    try {
      const d = await api.resetDocument(id);
      setDoc(d);
      setDraft(d.content);
      toast("Restored the original");
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  }

  async function remove() {
    try {
      await api.deleteDocument(id);
      toast("Deleted");
      router.replace(doc ? `/jobs/${doc.jobId}` : "/history");
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  }

  function exportPdf() {
    if (!doc) return;
    // The browser's "Save as PDF" uses the page title as the file name.
    const previous = document.title;
    const who = doc.authorName || (draft as ResumeContent).fullName || "Resume";
    document.title = `${who} - ${doc.company} ${doc.kind === "RESUME" ? "Resume" : "Cover Letter"}`;
    setTab("preview");
    setTimeout(() => {
      window.print();
      document.title = previous;
    }, 50);
  }

  if (error) return <Alert>{error}</Alert>;
  if (!doc || !draft) return <Spinner label="Loading document" />;

  const isResume = doc.kind === "RESUME";

  return (
    <div>
      <div className="no-print mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <Link href={`/jobs/${doc.jobId}`} className="mb-2 inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> {doc.jobTitle}
          </Link>
          <h1 className="flex flex-wrap items-center gap-2 text-2xl font-semibold tracking-tight">
            {isResume ? "Resume" : "Cover letter"} for {doc.company}
            {doc.modelUsed === "master-copy" ? <Badge>From master</Badge> : <Badge tone="primary">AI draft</Badge>}
            {doc.edited && <Badge tone="success">Edited</Badge>}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">Updated {relativeTime(doc.updatedAt)}. Edits here never change your master resume.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="ghost" size="icon" onClick={() => setConfirm("delete")} aria-label="Delete document">
            <Trash2 className="h-4 w-4" />
          </Button>
          {doc.edited && (
            <Button variant="outline" onClick={() => setConfirm("reset")}>
              <RotateCcw className="h-4 w-4" /> Original
            </Button>
          )}
          <Button variant="outline" onClick={save} loading={saving} disabled={!dirty}>
            <Save className="h-4 w-4" /> {dirty ? "Save" : "Saved"}
          </Button>
          <Button variant="gradient" onClick={exportPdf}>
            <Download className="h-4 w-4" /> Download PDF
          </Button>
        </div>
      </div>

      {doc.modelUsed !== "master-copy" && (
        <Alert tone="info" className="no-print mb-6 flex items-start gap-2">
          <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <span>Read this draft before you send it. The AI only rewords what&apos;s in your resume, but you know your experience best.</span>
        </Alert>
      )}

      {/* Phone: one pane at a time */}
      <div className="no-print mb-4 grid grid-cols-2 gap-1 rounded-xl bg-muted p-1 lg:hidden">
        {(
          [
            ["edit", "Edit", PencilLine],
            ["preview", "Preview", Eye],
          ] as const
        ).map(([value, label, Icon]) => (
          <button
            key={value}
            onClick={() => setTab(value)}
            className={cn(
              "flex items-center justify-center gap-2 rounded-lg py-2 text-sm font-medium",
              tab === value ? "bg-card shadow-sm" : "text-muted-foreground",
            )}
          >
            <Icon className="h-4 w-4" /> {label}
          </button>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className={cn("no-print card p-5", tab !== "edit" && "hidden lg:block")}>
          {isResume ? (
            <ResumeEditor value={draft as ResumeContent} onChange={setDraft} />
          ) : (
            <CoverLetterEditor value={draft as CoverLetterContent} onChange={setDraft} />
          )}
        </div>
        <div className={cn("lg:sticky lg:top-6 lg:self-start", tab !== "preview" && "hidden lg:block", "print:!block")}>
          {isResume ? (
            <ResumePreview content={draft as ResumeContent} />
          ) : (
            <CoverLetterPreview content={draft as CoverLetterContent} author={doc.authorName} company={doc.company} />
          )}
        </div>
      </div>

      <Dialog
        open={confirm !== null}
        onClose={() => setConfirm(null)}
        title={confirm === "reset" ? "Restore the original?" : "Delete this document?"}
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirm(null)}>
              Cancel
            </Button>
            <Button variant="danger" onClick={confirm === "reset" ? reset : remove}>
              {confirm === "reset" ? "Restore" : "Delete"}
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">
          {confirm === "reset"
            ? "Your edits are discarded and the document goes back to how it was first created."
            : "This removes the document. Your master resume is not affected."}
        </p>
      </Dialog>
    </div>
  );
}
