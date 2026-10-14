"use client";

import { useEffect, useState } from "react";
import { Check, ScanSearch, X } from "lucide-react";
import { Card, CardHeader, Skeleton } from "@/components/ui/misc";
import { MatchRing } from "@/components/ui/visuals";
import { api } from "@/lib/api";
import { documentLines, lineChanges } from "@/lib/diff";
import type { CoverLetterContent, KeywordCoverage, ResumeContent } from "@/lib/types";
import { cn } from "@/lib/utils";

/** ATS keyword coverage for the saved document; refreshes whenever a save bumps the version. */
export function KeywordPanel({ documentId, version }: { documentId: number; version: number }) {
  const [data, setData] = useState<KeywordCoverage | null>(null);

  useEffect(() => {
    let live = true;
    api
      .documentKeywords(documentId)
      .then((d) => live && setData(d))
      .catch(() => live && setData(null));
    return () => {
      live = false;
    };
  }, [documentId, version]);

  return (
    <Card>
      <CardHeader
        title="ATS keyword check"
        icon={ScanSearch}
        description="Terms from the job advert, and whether your saved document uses them. No AI, no quota."
      />
      <div className="p-5">
        {!data ? (
          <Skeleton className="h-24 w-full" />
        ) : data.found.length + data.missing.length === 0 ? (
          <p className="text-sm text-muted-foreground">The advert has no recognisable keywords to check against.</p>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center gap-4">
              <MatchRing value={data.coveragePct} size={60} stroke={5} />
              <p className="text-sm text-muted-foreground">
                Uses <span className="font-semibold text-foreground">{data.found.length}</span> of {data.found.length + data.missing.length} keywords.
                {data.missing.length > 0 && " Add the missing ones only where they're true for you."}
              </p>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {data.missing.map((k) => (
                <span key={k} className="inline-flex items-center gap-1 rounded-lg bg-danger/10 px-2 py-0.5 text-xs font-medium text-danger">
                  <X className="h-3 w-3" /> {k}
                </span>
              ))}
              {data.found.map((k) => (
                <span key={k} className="inline-flex items-center gap-1 rounded-lg bg-success/10 px-2 py-0.5 text-xs font-medium text-success">
                  <Check className="h-3 w-3" /> {k}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}

/** What the AI (or the master copy) produced versus the current edit, line by line. */
export function ChangesView({ original, current }: { original: ResumeContent | CoverLetterContent; current: ResumeContent | CoverLetterContent }) {
  const changes = lineChanges(documentLines(original), documentLines(current));
  const edited = changes.some((c) => c.type !== "same");

  if (!edited) {
    return <p className="card p-8 text-center text-sm text-muted-foreground">No changes yet. This is exactly what was generated.</p>;
  }

  return (
    <div className="card overflow-hidden font-mono text-[12.5px] leading-relaxed">
      <div className="flex gap-4 border-b border-border bg-muted/30 px-4 py-2 font-sans text-xs text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm bg-danger/40" /> Original
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm bg-success/40" /> Your edit
        </span>
      </div>
      <div className="divide-y divide-border/50">
        {changes.map((c, i) => {
          if (c.type === "change") {
            return (
              <div key={i}>
                <p className="bg-danger/5 px-4 py-1">
                  <span className="mr-2 select-none text-danger">−</span>
                  {c.before.map((w, j) => (
                    <span key={j} className={cn(w.type === "remove" && "rounded bg-danger/20 text-danger line-through decoration-danger/50")}>
                      {w.value}
                    </span>
                  ))}
                </p>
                <p className="bg-success/5 px-4 py-1">
                  <span className="mr-2 select-none text-success">+</span>
                  {c.after.map((w, j) => (
                    <span key={j} className={cn(w.type === "add" && "rounded bg-success/20 text-success")}>
                      {w.value}
                    </span>
                  ))}
                </p>
              </div>
            );
          }
          return (
            <p
              key={i}
              className={cn(
                "px-4 py-1",
                c.type === "add" && "bg-success/10 text-success",
                c.type === "remove" && "bg-danger/10 text-danger line-through decoration-danger/50",
                c.type === "same" && "text-muted-foreground",
              )}
            >
              <span className="mr-2 select-none">{c.type === "add" ? "+" : c.type === "remove" ? "−" : " "}</span>
              {c.text}
            </p>
          );
        })}
      </div>
    </div>
  );
}
