"use client";

import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input, Textarea } from "@/components/ui/form";
import type { CoverLetterContent, ResumeContent, ResumeEntry, ResumeSection } from "@/lib/types";

const emptyEntry = (): ResumeEntry => ({ title: "", meta: "", right: "", subtitle: "", subtitleRight: "", bullets: [""] });

function move<T>(list: T[], from: number, to: number) {
  if (to < 0 || to >= list.length) return list;
  const copy = [...list];
  const [item] = copy.splice(from, 1);
  copy.splice(to, 0, item);
  return copy;
}

/** Bullets edited one per line: easier than a field per bullet, and paste-friendly. */
function BulletsField({ value, onChange, placeholder }: { value: string[]; onChange: (v: string[]) => void; placeholder?: string }) {
  return (
    <Textarea
      rows={Math.max(2, value.length + 1)}
      value={value.join("\n")}
      onChange={(e) => onChange(e.target.value.split("\n"))}
      onBlur={(e) => onChange(e.target.value.split("\n").map((b) => b.trim()).filter(Boolean))}
      placeholder={placeholder ?? "One bullet per line"}
      className="text-[13px]"
    />
  );
}

function IconButton({ onClick, label, children, danger }: { onClick: () => void; label: string; children: React.ReactNode; danger?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={
        "rounded-lg p-1.5 text-muted-foreground transition-colors hover:bg-muted " + (danger ? "hover:text-danger" : "hover:text-foreground")
      }
    >
      {children}
    </button>
  );
}

/** Structured resume editor: fields and lists, not a freeform textarea. */
export function ResumeEditor({ value, onChange }: { value: ResumeContent; onChange: (v: ResumeContent) => void }) {
  const set = <K extends keyof ResumeContent>(key: K, v: ResumeContent[K]) => onChange({ ...value, [key]: v });
  const setSection = (i: number, s: ResumeSection) => set("sections", value.sections.map((x, j) => (j === i ? s : x)));

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <label className="label">Full name</label>
          <Input value={value.fullName} onChange={(e) => set("fullName", e.target.value)} />
        </div>
        <div>
          <label className="label">Headline (optional)</label>
          <Input value={value.headline} onChange={(e) => set("headline", e.target.value)} placeholder="Backend developer, Java and Spring" />
        </div>
        <div className="sm:col-span-2">
          <label className="label">Contact line</label>
          <Input value={value.contactLine} onChange={(e) => set("contactLine", e.target.value)} placeholder="780-555-0100 | you@example.com | linkedin.com/in/you | github.com/you" />
        </div>
        <div className="sm:col-span-2">
          <label className="label">Summary (optional)</label>
          <Textarea rows={2} value={value.summary} onChange={(e) => set("summary", e.target.value)} />
        </div>
      </div>

      {value.sections.map((section, si) => (
        <div key={si} className="rounded-2xl border border-border bg-muted/20 p-4">
          <div className="mb-3 flex items-center gap-2">
            <Input
              value={section.heading}
              onChange={(e) => setSection(si, { ...section, heading: e.target.value })}
              className="font-semibold"
              aria-label="Section heading"
              placeholder="Section heading"
            />
            <IconButton label="Move section up" onClick={() => set("sections", move(value.sections, si, si - 1))}>
              <ArrowUp className="h-4 w-4" />
            </IconButton>
            <IconButton label="Move section down" onClick={() => set("sections", move(value.sections, si, si + 1))}>
              <ArrowDown className="h-4 w-4" />
            </IconButton>
            <IconButton label="Delete section" danger onClick={() => set("sections", value.sections.filter((_, j) => j !== si))}>
              <Trash2 className="h-4 w-4" />
            </IconButton>
          </div>

          <div className="space-y-3">
            {section.entries.map((entry, ei) => {
              const setEntry = (e: ResumeEntry) => setSection(si, { ...section, entries: section.entries.map((x, j) => (j === ei ? e : x)) });
              return (
                <div key={ei} className="rounded-xl border border-border bg-card p-3">
                  <div className="grid gap-2 sm:grid-cols-2">
                    <Input value={entry.title} onChange={(e) => setEntry({ ...entry, title: e.target.value })} placeholder="Title (position, school or project)" />
                    <Input value={entry.right} onChange={(e) => setEntry({ ...entry, right: e.target.value })} placeholder="Right (dates, or location for education)" />
                    <Input value={entry.subtitle} onChange={(e) => setEntry({ ...entry, subtitle: e.target.value })} placeholder="Subtitle (employer or degree)" />
                    <Input
                      value={entry.subtitleRight}
                      onChange={(e) => setEntry({ ...entry, subtitleRight: e.target.value })}
                      placeholder="Subtitle right (location, or dates for education)"
                    />
                    <Input
                      value={entry.meta}
                      onChange={(e) => setEntry({ ...entry, meta: e.target.value })}
                      placeholder="Technologies (projects only)"
                      className="sm:col-span-2"
                    />
                  </div>
                  <div className="mt-2">
                    <BulletsField value={entry.bullets} onChange={(bullets) => setEntry({ ...entry, bullets })} />
                  </div>
                  <div className="mt-1 flex justify-end gap-1">
                    <IconButton label="Move entry up" onClick={() => setSection(si, { ...section, entries: move(section.entries, ei, ei - 1) })}>
                      <ArrowUp className="h-3.5 w-3.5" />
                    </IconButton>
                    <IconButton label="Move entry down" onClick={() => setSection(si, { ...section, entries: move(section.entries, ei, ei + 1) })}>
                      <ArrowDown className="h-3.5 w-3.5" />
                    </IconButton>
                    <IconButton label="Delete entry" danger onClick={() => setSection(si, { ...section, entries: section.entries.filter((_, j) => j !== ei) })}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </IconButton>
                  </div>
                </div>
              );
            })}
            {section.entries.length === 0 && (
              <BulletsField
                value={section.bullets}
                onChange={(bullets) => setSection(si, { ...section, bullets })}
                placeholder="Bullets for this section, one per line (or add an entry)"
              />
            )}
            <Button variant="ghost" size="sm" onClick={() => setSection(si, { ...section, entries: [...section.entries, emptyEntry()] })}>
              <Plus className="h-3.5 w-3.5" /> Add entry
            </Button>
          </div>
        </div>
      ))}

      <Button variant="outline" size="sm" onClick={() => set("sections", [...value.sections, { heading: "New section", entries: [emptyEntry()], bullets: [] }])}>
        <Plus className="h-3.5 w-3.5" /> Add section
      </Button>

      <div>
        <label className="label">Skills</label>
        <BulletsField value={value.skills} onChange={(skills) => set("skills", skills)} placeholder={"Languages: Java, Python, SQL\nFrameworks: Spring Boot, React"} />
      </div>
    </div>
  );
}

export function CoverLetterEditor({ value, onChange }: { value: CoverLetterContent; onChange: (v: CoverLetterContent) => void }) {
  return (
    <div className="space-y-4">
      <div>
        <label className="label">Greeting</label>
        <Input value={value.greeting} onChange={(e) => onChange({ ...value, greeting: e.target.value })} />
      </div>
      {value.paragraphs.map((p, i) => (
        <div key={i}>
          <div className="mb-1.5 flex items-center justify-between">
            <label className="label mb-0">Paragraph {i + 1}</label>
            <IconButton label="Delete paragraph" danger onClick={() => onChange({ ...value, paragraphs: value.paragraphs.filter((_, j) => j !== i) })}>
              <Trash2 className="h-3.5 w-3.5" />
            </IconButton>
          </div>
          <Textarea rows={5} value={p} onChange={(e) => onChange({ ...value, paragraphs: value.paragraphs.map((x, j) => (j === i ? e.target.value : x)) })} />
        </div>
      ))}
      <Button variant="ghost" size="sm" onClick={() => onChange({ ...value, paragraphs: [...value.paragraphs, ""] })}>
        <Plus className="h-3.5 w-3.5" /> Add paragraph
      </Button>
      <div>
        <label className="label">Closing</label>
        <Input value={value.closing} onChange={(e) => onChange({ ...value, closing: e.target.value })} />
      </div>
    </div>
  );
}
