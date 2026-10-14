"use client";

import Link from "next/link";
import { FileText, History as HistoryIcon, Mail } from "lucide-react";
import { ButtonLink } from "@/components/ui/button";
import { Alert, Badge, EmptyState, PageHeader, Skeleton, StageBadge } from "@/components/ui/misc";
import { CompanyLogo } from "@/components/ui/visuals";
import { ApplyLink } from "@/components/jobs/apply-link";
import { api } from "@/lib/api";
import { useAsync } from "@/lib/hooks";
import { formatDay, relativeTime } from "@/lib/utils";

/** The log of what has actually been done on each job, newest first. */
export default function HistoryPage() {
  const { data, error, loading } = useAsync(() => api.history());

  return (
    <div>
      <PageHeader title="History" description="Every job you've applied to or prepared documents for." />
      {error ? (
        <Alert>{error}</Alert>
      ) : loading && !data ? (
        <div className="space-y-3">
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-24 rounded-2xl" />
          ))}
        </div>
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={HistoryIcon}
          title="No history yet"
          description="Jobs show up here once you track an application or create a resume or cover letter for them."
          action={<ButtonLink href="/jobs">Find jobs</ButtonLink>}
        />
      ) : (
        <ol className="relative space-y-3 border-l border-border pl-6 sm:ml-3">
          {data.map((entry) => (
            <li key={`${entry.jobId ?? "app"}-${entry.application?.id ?? ""}`} className="relative">
              <span className="absolute -left-[1.85rem] top-6 h-3 w-3 rounded-full border-2 border-background bg-primary" aria-hidden />
              <div className="card flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
                <CompanyLogo name={entry.company} src={entry.companyLogoUrl} />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    {entry.jobId ? (
                      <Link href={`/jobs/${entry.jobId}`} className="font-semibold hover:text-primary">
                        {entry.jobTitle}
                      </Link>
                    ) : (
                      <span className="font-semibold">{entry.jobTitle}</span>
                    )}
                    {entry.application && <StageBadge status={entry.application.status} />}
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {entry.company}
                    {entry.application ? ` · applied ${formatDay(entry.application.dateApplied)}` : " · not applied yet"} · active{" "}
                    {relativeTime(entry.lastActivity)}
                  </p>
                  {entry.documents.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {entry.documents.map((d) => (
                        <Link key={d.id} href={`/documents/${d.id}`}>
                          <Badge tone="primary" className="hover:bg-primary/15">
                            {d.kind === "RESUME" ? <FileText className="h-3 w-3" /> : <Mail className="h-3 w-3" />}
                            {d.kind === "RESUME" ? "Resume" : "Cover letter"}
                            {d.edited ? " · edited" : ""}
                          </Badge>
                        </Link>
                      ))}
                    </div>
                  )}
                </div>
                <div className="flex gap-2">
                  {entry.application && (
                    <ButtonLink href={`/applications/${entry.application.id}`} variant="outline" size="sm">
                      Timeline
                    </ButtonLink>
                  )}
                  <ApplyLink url={entry.url} publisher={entry.publisher} size="sm" />
                </div>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
