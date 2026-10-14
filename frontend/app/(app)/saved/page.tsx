"use client";

import { Bookmark } from "lucide-react";
import { ButtonLink } from "@/components/ui/button";
import { Alert, EmptyState, PageHeader, Skeleton } from "@/components/ui/misc";
import { JobCard, JobGrid } from "@/components/jobs/job-card";
import { api } from "@/lib/api";
import { useAsync } from "@/lib/hooks";

export default function SavedPage() {
  const { data, setData, error, loading } = useAsync(() => api.savedJobs());

  return (
    <div>
      <PageHeader title="Saved" description="Your shortlist. Open a job to check your match or tailor your resume." />
      {error ? (
        <Alert>{error}</Alert>
      ) : loading && !data ? (
        <JobGrid>
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-52 rounded-2xl" />
          ))}
        </JobGrid>
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={Bookmark}
          title="No saved jobs yet"
          description="Open any job and press Save to keep it here."
          action={<ButtonLink href="/jobs">Browse jobs</ButtonLink>}
        />
      ) : (
        <JobGrid>
          {data.map((j) => (
            <JobCard
              key={j.id}
              job={j}
              // Updated in place: removing an unsaved card would also close its open dialog.
              // It drops off the list on the next visit.
              onChange={(card) => setData((list) => (list ?? []).map((x) => (x.id === card.id ? card : x)))}
            />
          ))}
        </JobGrid>
      )}
    </div>
  );
}
