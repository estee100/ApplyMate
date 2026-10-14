"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Alert, PageHeader } from "@/components/ui/misc";
import { api } from "@/lib/api";
import { useAsync } from "@/lib/hooks";
import type { CalendarEvent } from "@/lib/types";
import { cn, parseDay, toDay, today } from "@/lib/utils";

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

const EVENT_STYLE: Record<CalendarEvent["type"], string> = {
  APPLIED: "bg-sky-500/10 text-sky-700 dark:text-sky-300",
  NEXT_ACTION: "bg-primary/10 text-primary",
  STATUS_CHANGE: "bg-muted text-muted-foreground",
};

/** Six weeks starting on the Monday on or before the 1st. */
function monthGrid(year: number, month: number) {
  const first = new Date(year, month, 1);
  const start = new Date(first);
  start.setDate(1 - ((first.getDay() + 6) % 7));
  return Array.from({ length: 42 }, (_, i) => {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    return d;
  });
}

export default function CalendarPage() {
  const now = new Date();
  const [cursor, setCursor] = useState({ year: now.getFullYear(), month: now.getMonth() });
  const days = useMemo(() => monthGrid(cursor.year, cursor.month), [cursor]);
  const from = toDay(days[0]);
  const to = toDay(days[days.length - 1]);
  const { data: events, error } = useAsync(() => api.calendar(from, to), [from, to]);
  const [selected, setSelected] = useState(today());

  const byDay = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>();
    for (const e of events ?? []) map.set(e.date, [...(map.get(e.date) ?? []), e]);
    return map;
  }, [events]);

  const shift = (delta: number) =>
    setCursor(({ year, month }) => {
      const d = new Date(year, month + delta, 1);
      return { year: d.getFullYear(), month: d.getMonth() };
    });

  const label = new Date(cursor.year, cursor.month, 1).toLocaleDateString(undefined, { month: "long", year: "numeric" });
  const selectedEvents = byDay.get(selected) ?? [];
  const upcoming = (events ?? []).filter((e) => e.type === "NEXT_ACTION" && e.date >= today()).slice(0, 6);

  return (
    <div>
      <PageHeader
        title="Calendar"
        description="Applications, next steps and replies. Synced across your devices."
        actions={
          <div className="flex items-center gap-1">
            <Button variant="outline" size="icon" onClick={() => shift(-1)} aria-label="Previous month">
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="h-9"
              onClick={() => {
                setCursor({ year: now.getFullYear(), month: now.getMonth() });
                setSelected(today());
              }}
            >
              Today
            </Button>
            <Button variant="outline" size="icon" onClick={() => shift(1)} aria-label="Next month">
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        }
      />
      {error && <Alert className="mb-4">{error}</Alert>}

      <div className="grid gap-6 lg:grid-cols-[1fr_300px]">
        <div className="card overflow-hidden">
          <div className="border-b border-border px-5 py-4">
            <h2 className="font-semibold tracking-tight">{label}</h2>
          </div>
          <div className="grid grid-cols-7 border-b border-border bg-muted/30 text-center text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
            {WEEKDAYS.map((d) => (
              <div key={d} className="py-2">
                {d}
              </div>
            ))}
          </div>
          <div className="grid grid-cols-7">
            {days.map((d) => {
              const key = toDay(d);
              const items = byDay.get(key) ?? [];
              const inMonth = d.getMonth() === cursor.month;
              const isToday = key === today();
              return (
                <button
                  key={key}
                  onClick={() => setSelected(key)}
                  className={cn(
                    "group relative flex min-h-[64px] flex-col items-start gap-1 border-b border-r border-border p-1.5 text-left transition-colors hover:bg-muted/40 sm:min-h-[104px] sm:p-2 [&:nth-child(7n)]:border-r-0",
                    !inMonth && "bg-muted/20 text-muted-foreground/60",
                    selected === key && "bg-primary/5 ring-1 ring-inset ring-primary/40",
                  )}
                  aria-label={`${d.toDateString()}, ${items.length} events`}
                >
                  <span
                    className={cn(
                      "flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium",
                      isToday && "bg-primary text-primary-foreground",
                    )}
                  >
                    {d.getDate()}
                  </span>
                  <div className="hidden w-full space-y-0.5 sm:block">
                    {items.slice(0, 3).map((e) => (
                      <p
                        key={e.id}
                        className={cn("truncate rounded px-1.5 py-0.5 text-[10px] font-medium", EVENT_STYLE[e.type], e.overdue && "bg-warning/15 text-warning")}
                      >
                        {e.title}
                      </p>
                    ))}
                    {items.length > 3 && <p className="px-1.5 text-[10px] text-muted-foreground">+{items.length - 3} more</p>}
                  </div>
                  {items.length > 0 && (
                    <div className="flex gap-0.5 sm:hidden">
                      {items.slice(0, 3).map((e) => (
                        <span key={e.id} className={cn("h-1.5 w-1.5 rounded-full", e.type === "NEXT_ACTION" ? "bg-primary" : "bg-sky-500")} />
                      ))}
                    </div>
                  )}
                </button>
              );
            })}
          </div>
        </div>

        <aside className="space-y-6">
          <div className="card p-5">
            <h3 className="font-semibold">{parseDay(selected).toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}</h3>
            {selectedEvents.length === 0 ? (
              <p className="mt-3 text-sm text-muted-foreground">Nothing on this day.</p>
            ) : (
              <ul className="mt-3 space-y-2">
                {selectedEvents.map((e) => (
                  <EventRow key={e.id} event={e} />
                ))}
              </ul>
            )}
          </div>
          <div className="card p-5">
            <h3 className="font-semibold">Coming up</h3>
            {upcoming.length === 0 ? (
              <p className="mt-3 text-sm text-muted-foreground">Set a next step on an application to see it here.</p>
            ) : (
              <ul className="mt-3 space-y-2">
                {upcoming.map((e) => (
                  <EventRow key={e.id} event={e} showDate />
                ))}
              </ul>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}

function EventRow({ event, showDate }: { event: CalendarEvent; showDate?: boolean }) {
  return (
    <li>
      <Link
        href={`/applications/${event.applicationId}`}
        className={cn("block rounded-xl px-3 py-2 text-sm transition-colors hover:brightness-95", EVENT_STYLE[event.type], event.overdue && "bg-warning/15 text-warning")}
      >
        <span className="font-medium">{event.title}</span>
        {showDate && <span className="block text-xs opacity-80">{parseDay(event.date).toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" })}</span>}
        {event.detail && <span className="block text-xs opacity-80">{event.detail}</span>}
      </Link>
    </li>
  );
}
