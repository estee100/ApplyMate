"use client";

import { BarChart3, CalendarCheck, Clock, MessageCircleReply, Trophy } from "lucide-react";
import { ButtonLink } from "@/components/ui/button";
import { Alert, Card, CardHeader, EmptyState, PageHeader, Skeleton, Stat } from "@/components/ui/misc";
import { api } from "@/lib/api";
import { useAsync } from "@/lib/hooks";
import { cn, STAGE_LABEL, STAGE_STYLE, STAGES } from "@/lib/utils";

const pct = (v: number | null) => (v == null ? "–" : `${v}%`);

export default function AnalyticsPage() {
  const { data, error, loading } = useAsync(() => api.analytics());

  if (error) return <Alert>{error}</Alert>;

  const maxMonth = Math.max(1, ...(data?.applicationsByMonth.map((m) => m.count) ?? [1]));
  const maxStage = Math.max(1, ...(data ? STAGES.map((s) => data.byStatus[s] ?? 0) : [1]));

  return (
    <div>
      <PageHeader title="Analytics" description="How your search is going. Rates count every stage an application reached, not just where it is now." />

      {loading && !data ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-[124px] rounded-2xl" />
          ))}
        </div>
      ) : data && data.totalApplications === 0 ? (
        <EmptyState
          icon={BarChart3}
          title="No data yet"
          description="Track a few applications and your response, interview and offer rates appear here."
          action={<ButtonLink href="/jobs">Find jobs</ButtonLink>}
        />
      ) : data ? (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Response rate" value={pct(data.responseRatePct)} icon={MessageCircleReply} hint="Employers who replied at all" />
            <Stat
              label="Interview rate"
              value={pct(data.interviewRatePct)}
              icon={CalendarCheck}
              accent="bg-warning/10 text-warning"
              hint="Reached an interview"
            />
            <Stat label="Offer rate" value={pct(data.offerRatePct)} icon={Trophy} accent="bg-success/10 text-success" hint="Ended in an offer" />
            <Stat
              label="Time to reply"
              value={data.avgDaysToResponse == null ? "–" : `${data.avgDaysToResponse}d`}
              icon={Clock}
              accent="bg-accent/10 text-accent"
              hint="Average, applied to first reply"
            />
          </div>

          <div className="grid gap-6 lg:grid-cols-5">
            <Card className="lg:col-span-3">
              <CardHeader title="Applications per month" description="The last 12 months." />
              <div className="flex h-64 items-end gap-1.5 px-5 pb-5 pt-8 sm:gap-2.5">
                {data.applicationsByMonth.map(({ month, count }) => {
                  const [y, m] = month.split("-").map(Number);
                  const name = new Date(y, m - 1, 1).toLocaleDateString(undefined, { month: "short" });
                  return (
                    <div key={month} className="group flex h-full flex-1 flex-col items-center justify-end gap-2">
                      <span className="text-[11px] font-medium tabular-nums text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100">
                        {count}
                      </span>
                      <div
                        className="w-full max-w-[36px] rounded-t-lg bg-gradient-to-t from-primary to-accent transition-all duration-500 group-hover:brightness-110"
                        style={{ height: `${Math.max(count > 0 ? 6 : 2, (count / maxMonth) * 100)}%`, opacity: count > 0 ? 1 : 0.25 }}
                        title={`${name}: ${count}`}
                      />
                      <span className="text-[10px] text-muted-foreground">{name}</span>
                    </div>
                  );
                })}
              </div>
            </Card>

            <Card className="lg:col-span-2">
              <CardHeader title="Where they are now" description={`${data.totalApplications} applications`} />
              <div className="space-y-4 p-5">
                {STAGES.map((s) => {
                  const count = data.byStatus[s] ?? 0;
                  return (
                    <div key={s}>
                      <div className="mb-1.5 flex justify-between text-sm">
                        <span className="flex items-center gap-2 font-medium">
                          <span className={cn("h-2 w-2 rounded-full", STAGE_STYLE[s].dot)} />
                          {STAGE_LABEL[s]}
                        </span>
                        <span className="tabular-nums text-muted-foreground">{count}</span>
                      </div>
                      <div className="h-2 overflow-hidden rounded-full bg-muted">
                        <div className={cn("h-full rounded-full transition-all duration-700", STAGE_STYLE[s].bar)} style={{ width: `${(count / maxStage) * 100}%` }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            </Card>
          </div>
        </div>
      ) : null}
    </div>
  );
}
