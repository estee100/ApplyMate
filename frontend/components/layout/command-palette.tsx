"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useRouter } from "next/navigation";
import {
  BarChart3,
  Bookmark,
  Briefcase,
  CalendarDays,
  CornerDownLeft,
  FileText,
  History,
  Home,
  KanbanSquare,
  LogOut,
  Moon,
  Plus,
  Search,
  UserRound,
} from "lucide-react";
import { useTheme } from "@/components/providers";
import { StageBadge } from "@/components/ui/misc";
import { api } from "@/lib/api";
import { useDebounced } from "@/lib/hooks";
import type { SearchResults, Snippet } from "@/lib/types";
import { cn } from "@/lib/utils";

interface Item {
  id: string;
  group: string;
  label: string;
  hint?: string;
  icon: React.ComponentType<{ className?: string }>;
  snippet?: Snippet;
  badge?: React.ReactNode;
  run: () => void;
}

/**
 * Subsequence match with a score: consecutive and word-start hits score higher, so
 * "trk" finds "Tracking" and "lg app" finds "Log an application".
 */
export function fuzzyScore(query: string, text: string): number {
  const q = query.toLowerCase().replace(/\s+/g, "");
  const t = text.toLowerCase();
  if (!q) return 1;
  let score = 0;
  let ti = 0;
  let streak = 0;
  for (const ch of q) {
    const found = t.indexOf(ch, ti);
    if (found === -1) return 0;
    streak = found === ti ? streak + 1 : 1;
    const wordStart = found === 0 || /\W/.test(t[found - 1]);
    score += 1 + streak + (wordStart ? 3 : 0);
    ti = found + 1;
  }
  return score / (t.length + 1);
}

/** Text with ranges in bold, built from spans: search snippets are never rendered as HTML. */
function Highlighted({ snippet }: { snippet: Snippet }) {
  const parts: React.ReactNode[] = [];
  let at = 0;
  snippet.highlights.forEach(([start, end], i) => {
    if (start > at) parts.push(snippet.text.slice(at, start));
    parts.push(
      <mark key={i} className="rounded bg-primary/15 px-0.5 font-medium text-foreground">
        {snippet.text.slice(start, end)}
      </mark>,
    );
    at = end;
  });
  parts.push(snippet.text.slice(at));
  return <>{parts}</>;
}

export function CommandPalette({ onAction }: { onAction: (action: "add-job" | "log-application") => void }) {
  const router = useRouter();
  const { toggle } = useTheme();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const [results, setResults] = useState<SearchResults | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLDivElement>(null);
  const debounced = useDebounced(query, 180);

  // Ctrl/Cmd+K anywhere, and a custom event so buttons can open it.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    const onOpen = () => setOpen(true);
    window.addEventListener("keydown", onKey);
    window.addEventListener("applymate:command-palette", onOpen);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("applymate:command-palette", onOpen);
    };
  }, []);

  useEffect(() => {
    if (open) {
      setQuery("");
      setResults(null);
      setActive(0);
      setTimeout(() => input.current?.focus(), 0);
    }
  }, [open]);

  useEffect(() => {
    if (!open || debounced.trim().length < 2) {
      setResults(null);
      return;
    }
    let live = true;
    api
      .search(debounced)
      .then((r) => live && setResults(r))
      .catch(() => live && setResults(null));
    return () => {
      live = false;
    };
  }, [debounced, open]);

  const go = (href: string) => () => {
    setOpen(false);
    router.push(href);
  };

  const commands: Item[] = useMemo(
    () => [
      { id: "home", group: "Go to", label: "Home", icon: Home, run: go("/dashboard") },
      { id: "jobs", group: "Go to", label: "Jobs", icon: Briefcase, run: go("/jobs") },
      { id: "saved", group: "Go to", label: "Saved jobs", icon: Bookmark, run: go("/saved") },
      { id: "tracking", group: "Go to", label: "Tracking board", icon: KanbanSquare, run: go("/tracking") },
      { id: "history", group: "Go to", label: "History", icon: History, run: go("/history") },
      { id: "calendar", group: "Go to", label: "Calendar", icon: CalendarDays, run: go("/calendar") },
      { id: "analytics", group: "Go to", label: "Analytics", icon: BarChart3, run: go("/analytics") },
      { id: "profile", group: "Go to", label: "Profile and resume", icon: UserRound, run: go("/profile") },
      {
        id: "add-job",
        group: "Actions",
        label: "Add a job from a link or text",
        icon: Plus,
        run: () => {
          setOpen(false);
          onAction("add-job");
        },
      },
      {
        id: "log",
        group: "Actions",
        label: "Log an application",
        icon: FileText,
        run: () => {
          setOpen(false);
          onAction("log-application");
        },
      },
      {
        id: "theme",
        group: "Actions",
        label: "Toggle dark mode",
        icon: Moon,
        run: () => {
          toggle();
          setOpen(false);
        },
      },
      {
        id: "logout",
        group: "Actions",
        label: "Sign out",
        icon: LogOut,
        run: async () => {
          setOpen(false);
          await api.logout();
          router.replace("/login");
        },
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [toggle, onAction],
  );

  const items: Item[] = useMemo(() => {
    const matched = commands
      .map((c) => ({ c, s: fuzzyScore(query, c.label) }))
      .filter((x) => x.s > 0)
      .sort((a, b) => b.s - a.s)
      .map((x) => x.c);
    const apps: Item[] = (results?.applications ?? []).map((a) => ({
      id: `app-${a.id}`,
      group: "Applications",
      label: `${a.jobTitle} · ${a.companyName}`,
      icon: KanbanSquare,
      snippet: a.snippet.highlights.length ? a.snippet : undefined,
      badge: <StageBadge status={a.status} />,
      run: go(`/applications/${a.id}`),
    }));
    const jobs: Item[] = (results?.jobs ?? []).map((j) => ({
      id: `job-${j.id}`,
      group: "Jobs",
      label: `${j.title} · ${j.company}`,
      hint: j.location ?? undefined,
      icon: j.saved ? Bookmark : Briefcase,
      snippet: j.snippet.highlights.length ? j.snippet : undefined,
      run: go(`/jobs/${j.id}`),
    }));
    return [...apps, ...jobs, ...matched];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [commands, query, results]);

  useEffect(() => setActive(0), [query, results]);
  useEffect(() => {
    list.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: "nearest" });
  }, [active]);

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((i) => Math.min(items.length - 1, i + 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((i) => Math.max(0, i - 1));
    } else if (e.key === "Enter") {
      e.preventDefault();
      items[active]?.run();
    } else if (e.key === "Escape") {
      setOpen(false);
    }
  }

  if (!open || typeof document === "undefined") return null;

  let lastGroup = "";
  return createPortal(
    <div className="fixed inset-0 z-[90] flex items-start justify-center px-4 pt-[12vh]">
      <div className="absolute inset-0 animate-fade-in bg-black/40 backdrop-blur-sm" onClick={() => setOpen(false)} />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Command palette"
        className="relative w-full max-w-xl animate-scale-in overflow-hidden rounded-2xl border border-border bg-card shadow-lift"
      >
        <div className="flex items-center gap-3 border-b border-border px-4">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
          <input
            ref={input}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Search applications and jobs, or type a command"
            className="h-14 flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            role="combobox"
            aria-expanded="true"
            aria-controls="command-results"
            aria-activedescendant={items[active] ? `cmd-${items[active].id}` : undefined}
          />
          <kbd className="hidden rounded-md border border-border px-1.5 py-0.5 text-[10px] text-muted-foreground sm:block">Esc</kbd>
        </div>
        <div ref={list} id="command-results" role="listbox" className="scrollbar-thin max-h-[52vh] overflow-y-auto p-2">
          {items.length === 0 ? (
            <p className="px-3 py-10 text-center text-sm text-muted-foreground">No matches.</p>
          ) : (
            items.map((item, index) => {
              const heading = item.group !== lastGroup ? item.group : null;
              lastGroup = item.group;
              const Icon = item.icon;
              return (
                <div key={item.id}>
                  {heading && <p className="px-3 pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">{heading}</p>}
                  <button
                    id={`cmd-${item.id}`}
                    data-index={index}
                    role="option"
                    aria-selected={index === active}
                    onMouseMove={() => setActive(index)}
                    onClick={item.run}
                    className={cn(
                      "flex w-full items-start gap-3 rounded-xl px-3 py-2.5 text-left text-sm",
                      index === active ? "bg-muted text-foreground" : "text-muted-foreground",
                    )}
                  >
                    <Icon className={cn("mt-0.5 h-4 w-4 shrink-0", index === active && "text-primary")} />
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-2">
                        <span className="truncate font-medium text-foreground">{item.label}</span>
                        {item.badge}
                      </span>
                      {item.hint && <span className="block truncate text-xs">{item.hint}</span>}
                      {item.snippet && (
                        <span className="mt-0.5 line-clamp-2 block text-xs">
                          <Highlighted snippet={item.snippet} />
                        </span>
                      )}
                    </span>
                    {index === active && <CornerDownLeft className="mt-0.5 h-3.5 w-3.5 shrink-0 text-muted-foreground" />}
                  </button>
                </div>
              );
            })
          )}
        </div>
        <div className="flex items-center gap-4 border-t border-border bg-muted/30 px-4 py-2 text-[11px] text-muted-foreground">
          <span>
            <kbd className="font-sans">↑↓</kbd> to move
          </span>
          <span>
            <kbd className="font-sans">↵</kbd> to open
          </span>
          <span className="ml-auto">Full-text search over your own data</span>
        </div>
      </div>
    </div>,
    document.body,
  );
}

/** Opens the palette from anywhere, e.g. a sidebar button. */
export function openCommandPalette() {
  window.dispatchEvent(new Event("applymate:command-palette"));
}
