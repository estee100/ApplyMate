"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AlarmClock, GripVertical, KanbanSquare, MoreHorizontal, Plus, Search } from "lucide-react";
import { useToast } from "@/components/providers";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/form";
import { Alert, EmptyState, PageHeader, Skeleton } from "@/components/ui/misc";
import { CompanyLogo } from "@/components/ui/visuals";
import { LogApplicationDialog } from "@/components/jobs/add-job-dialogs";
import { api } from "@/lib/api";
import { useAsync } from "@/lib/hooks";
import type { Application, ApplicationStatus } from "@/lib/types";
import { cn, errorMessage, formatDay, relativeTime, STAGE_LABEL, STAGE_STYLE, STAGES } from "@/lib/utils";

export default function TrackingPage() {
  const toast = useToast();
  const { data: apps, setData: setApps, error, loading } = useAsync(() => api.applications());
  const [query, setQuery] = useState("");
  const [dragging, setDragging] = useState<number | null>(null);
  const [over, setOver] = useState<ApplicationStatus | null>(null);
  const [logOpen, setLogOpen] = useState(false);

  const columns = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = (apps ?? []).filter((a) => !q || `${a.jobTitle} ${a.companyName}`.toLowerCase().includes(q));
    return STAGES.map((stage) => ({ stage, items: list.filter((a) => a.status === stage) }));
  }, [apps, query]);

  /** Optimistic: the card moves at once and snaps back if the server refuses. */
  async function move(app: Application, to: ApplicationStatus) {
    if (app.status === to) return;
    const before = apps;
    setApps((list) => (list ?? []).map((a) => (a.id === app.id ? { ...a, status: to, statusUpdatedAt: new Date().toISOString() } : a)));
    try {
      const updated = await api.updateApplication(app.id, { status: to });
      setApps((list) => (list ?? []).map((a) => (a.id === app.id ? updated : a)));
      toast(`${app.companyName} moved to ${STAGE_LABEL[to]}`);
    } catch (e) {
      setApps(before);
      toast(errorMessage(e), "error");
    }
  }

  return (
    <div>
      <PageHeader
        title="Tracking"
        description="Drag a card to move it between stages. Every move is kept in the application's timeline."
        actions={
          <Button variant="gradient" onClick={() => setLogOpen(true)}>
            <Plus className="h-4 w-4" /> Log an application
          </Button>
        }
      />

      <div className="relative mb-6 max-w-sm">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Filter by company or role" className="pl-10" aria-label="Filter applications" />
      </div>

      {error ? (
        <Alert>{error}</Alert>
      ) : loading && !apps ? (
        <div className="flex gap-4 overflow-hidden">
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-96 w-72 shrink-0 rounded-2xl" />
          ))}
        </div>
      ) : apps && apps.length === 0 ? (
        <EmptyState
          icon={KanbanSquare}
          title="Your board is empty"
          description="Mark a job as applied from its page, or log an application you sent elsewhere."
          action={
            <Button onClick={() => setLogOpen(true)}>
              <Plus className="h-4 w-4" /> Log an application
            </Button>
          }
        />
      ) : (
        <div className="scrollbar-thin -mx-4 flex snap-x gap-4 overflow-x-auto px-4 pb-4 sm:-mx-6 sm:px-6 lg:-mx-10 lg:px-10">
          {columns.map(({ stage, items }) => (
            <section
              key={stage}
              aria-label={STAGE_LABEL[stage]}
              onDragOver={(e) => {
                e.preventDefault();
                setOver(stage);
              }}
              onDragLeave={() => setOver((s) => (s === stage ? null : s))}
              onDrop={(e) => {
                e.preventDefault();
                setOver(null);
                const app = apps?.find((a) => a.id === dragging);
                if (app) move(app, stage);
                setDragging(null);
              }}
              className={cn(
                "flex w-72 shrink-0 snap-start flex-col rounded-2xl border border-border bg-muted/40 transition-colors",
                over === stage && "border-primary/50 bg-primary/5",
              )}
            >
              <header className="flex items-center justify-between px-4 py-3">
                <h2 className="flex items-center gap-2 text-sm font-semibold">
                  <span className={cn("h-2 w-2 rounded-full", STAGE_STYLE[stage].dot)} />
                  {STAGE_LABEL[stage]}
                </h2>
                <span className="rounded-full bg-card px-2 py-0.5 text-xs font-medium tabular-nums text-muted-foreground ring-1 ring-inset ring-border">
                  {items.length}
                </span>
              </header>
              <div className="flex min-h-[120px] flex-1 flex-col gap-2 px-2 pb-2">
                {items.map((a) => (
                  <BoardCard
                    key={a.id}
                    app={a}
                    dragging={dragging === a.id}
                    onDragStart={() => setDragging(a.id)}
                    onDragEnd={() => {
                      setDragging(null);
                      setOver(null);
                    }}
                    onMove={(to) => move(a, to)}
                  />
                ))}
                {items.length === 0 && (
                  <p className="rounded-xl border border-dashed border-border px-3 py-6 text-center text-xs text-muted-foreground">Drop here</p>
                )}
              </div>
            </section>
          ))}
        </div>
      )}

      <LogApplicationDialog open={logOpen} onClose={() => setLogOpen(false)} />
    </div>
  );
}

function BoardCard({
  app,
  dragging,
  onDragStart,
  onDragEnd,
  onMove,
}: {
  app: Application;
  dragging: boolean;
  onDragStart: () => void;
  onDragEnd: () => void;
  onMove: (to: ApplicationStatus) => void;
}) {
  const [menu, setMenu] = useState(false);

  return (
    <div
      draggable
      onDragStart={(e) => {
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/plain", String(app.id));
        onDragStart();
      }}
      onDragEnd={onDragEnd}
      className={cn(
        "group relative rounded-xl border border-border bg-card p-3 shadow-soft transition-all hover:border-primary/30 hover:shadow-lift",
        dragging && "rotate-1 opacity-50",
      )}
    >
      <div className="flex items-start gap-2.5">
        <GripVertical className="mt-1 h-4 w-4 shrink-0 cursor-grab text-muted-foreground/40 group-hover:text-muted-foreground" aria-hidden />
        <CompanyLogo name={app.companyName} src={app.companyLogoUrl} size="sm" />
        <Link href={`/applications/${app.id}`} className="min-w-0 flex-1">
          <p className="line-clamp-2 text-sm font-medium leading-snug hover:text-primary">{app.jobTitle}</p>
          <p className="truncate text-xs text-muted-foreground">{app.companyName}</p>
        </Link>
        <div className="relative">
          <button
            onClick={() => setMenu((m) => !m)}
            className="rounded-lg p-1 text-muted-foreground opacity-60 transition hover:bg-muted hover:opacity-100 focus:opacity-100"
            aria-label={`Move ${app.companyName}`}
            aria-expanded={menu}
            aria-haspopup="menu"
          >
            <MoreHorizontal className="h-4 w-4" />
          </button>
          {menu && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setMenu(false)} aria-hidden />
              <div role="menu" className="absolute right-0 z-20 mt-1 w-44 animate-scale-in rounded-xl border border-border bg-card p-1 shadow-lift">
                <p className="px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Move to</p>
                {STAGES.filter((s) => s !== app.status).map((s) => (
                  <button
                    key={s}
                    role="menuitem"
                    onClick={() => {
                      setMenu(false);
                      onMove(s);
                    }}
                    className="flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-left text-sm hover:bg-muted"
                  >
                    <span className={cn("h-2 w-2 rounded-full", STAGE_STYLE[s].dot)} />
                    {STAGE_LABEL[s]}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      </div>
      <div className="mt-3 flex items-center justify-between gap-2 pl-6 text-[11px] text-muted-foreground">
        <span>Applied {formatDay(app.dateApplied)}</span>
        {app.needsFollowUp ? (
          <span className="inline-flex items-center gap-1 font-medium text-warning">
            <AlarmClock className="h-3 w-3" /> Follow up
          </span>
        ) : app.nextActionDate ? (
          <span className="truncate">Next: {formatDay(app.nextActionDate)}</span>
        ) : (
          <span>{relativeTime(app.statusUpdatedAt)}</span>
        )}
      </div>
    </div>
  );
}
