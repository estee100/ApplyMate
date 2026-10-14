"use client";

import { useState } from "react";
import { BookmarkCheck, MapPin, Maximize2 } from "lucide-react";
import Link from "next/link";
import { Dialog } from "@/components/ui/dialog";
import { Badge, StageBadge } from "@/components/ui/misc";
import { CompanyLogo, MatchRing } from "@/components/ui/visuals";
import { ApplyLink } from "@/components/jobs/apply-link";
import { JobDetailView } from "@/components/jobs/job-detail";
import type { JobCard as Card } from "@/lib/types";
import { cn, JOB_TYPE_LABEL, relativeTime } from "@/lib/utils";

/**
 * A job in a grid. Clicking anywhere opens the detail dialog; the card owns it, so
 * every page that lists jobs gets the same behaviour.
 */
export function JobCard({
  job,
  onChange,
  footer,
  className,
}: {
  job: Card;
  onChange?: (card: Card) => void;
  footer?: React.ReactNode;
  className?: string;
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <article
        role="button"
        tabIndex={0}
        onClick={() => setOpen(true)}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            setOpen(true);
          }
        }}
        aria-label={`${job.title} at ${job.company}`}
        className={cn(
          "card group flex cursor-pointer flex-col p-5 text-left transition-all duration-200 hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-lift",
          className,
        )}
      >
        <div className="flex items-start gap-3">
          <CompanyLogo name={job.company} src={job.companyLogoUrl} />
          <div className="min-w-0 flex-1">
            <h3 className="line-clamp-2 font-semibold leading-snug tracking-tight group-hover:text-primary">{job.title}</h3>
            <p className="mt-0.5 truncate text-sm text-muted-foreground">{job.company}</p>
          </div>
          {job.match ? (
            <MatchRing value={job.match.percentage} size={46} stroke={4} />
          ) : (
            job.saved && <BookmarkCheck className="h-5 w-5 shrink-0 text-primary" aria-label="Saved" />
          )}
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-1.5">
          {job.location && (
            <span className="inline-flex max-w-full items-center gap-1 truncate text-xs text-muted-foreground">
              <MapPin className="h-3 w-3 shrink-0" />
              <span className="truncate">{job.location}</span>
            </span>
          )}
        </div>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {job.applicationStatus && <StageBadge status={job.applicationStatus} />}
          {job.jobType && <Badge>{JOB_TYPE_LABEL[job.jobType]}</Badge>}
          {job.salary && <Badge>{job.salary}</Badge>}
          {job.match && (
            <Badge tone="success">
              {job.match.metCount} have · {job.match.missingCount} missing
            </Badge>
          )}
          {job.saved && job.match && <Badge tone="primary">Saved</Badge>}
        </div>

        {footer}

        <div className="mt-auto flex items-center justify-between gap-2 pt-5">
          <span className="text-xs text-muted-foreground">{job.postedDate ? relativeTime(job.postedDate) : ""}</span>
          <ApplyLink url={job.url} publisher={job.publisher} size="sm" />
        </div>
      </article>

      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Job details"
        size="lg"
        description={
          <Link href={`/jobs/${job.id}`} className="inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline">
            <Maximize2 className="h-3 w-3" /> Open full page
          </Link>
        }
      >
        {open && <JobDetailView jobId={job.id} onChange={onChange} />}
      </Dialog>
    </>
  );
}

export function JobGrid({ children }: { children: React.ReactNode }) {
  return <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{children}</div>;
}
