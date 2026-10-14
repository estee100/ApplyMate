"use client";

import Link from "next/link";
import { use } from "react";
import { ArrowLeft } from "lucide-react";
import { Card } from "@/components/ui/misc";
import { JobDetailView } from "@/components/jobs/job-detail";

export default function JobPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <div className="mx-auto max-w-3xl">
      <Link href="/jobs" className="mb-6 inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> Back to jobs
      </Link>
      <Card className="p-6 sm:p-8">
        <JobDetailView jobId={Number(id)} />
      </Card>
    </div>
  );
}
