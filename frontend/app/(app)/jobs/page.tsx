"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ChevronDown, Globe2, Inbox, Plus, RefreshCw, Search, SearchX, Sparkles, Trash2 } from "lucide-react";
import { useToast } from "@/components/providers";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/form";
import { Alert, Badge, EmptyState, PageHeader, Skeleton } from "@/components/ui/misc";
import { JobCard, JobGrid } from "@/components/jobs/job-card";
import { LogApplicationDialog, PasteJobDialog } from "@/components/jobs/add-job-dialogs";
import { api, ApiError } from "@/lib/api";
import { useDebounced } from "@/lib/hooks";
import type { FeedResponse, JobCard as Card, JobType, Recommendations } from "@/lib/types";
import { cn, errorMessage, JOB_TYPE_LABEL } from "@/lib/utils";

const DATE_OPTIONS = [
  { value: "", label: "Any time" },
  { value: "1", label: "Past 24 hours" },
  { value: "3", label: "Past 3 days" },
  { value: "7", label: "Past week" },
  { value: "30", label: "Past month" },
];

/** How many recommendations get an AI score automatically, and how many are shown before "Show more". */
const AUTO_SCORE = 6;
const SHOW_FIRST = 3;

export default function JobsPage() {
  const toast = useToast();
  const [q, setQ] = useState("");
  const [location, setLocation] = useState("");
  const [jobType, setJobType] = useState<JobType | "">("");
  const [remoteOnly, setRemoteOnly] = useState(false);
  const [days, setDays] = useState("");
  const [page, setPage] = useState(1);
  const [feed, setFeed] = useState<FeedResponse | null>(null);
  const [feedError, setFeedError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [mine, setMine] = useState<Card[] | null>(null);
  const [recs, setRecs] = useState<Recommendations | null>(null);
  const [recsError, setRecsError] = useState<string | null>(null);
  const [scoring, setScoring] = useState(false);
  const [scoreNote, setScoreNote] = useState<string | null>(null);
  const [showAllRecs, setShowAllRecs] = useState(false);
  const [pasteOpen, setPasteOpen] = useState(false);
  const [logOpen, setLogOpen] = useState(false);

  const debouncedQ = useDebounced(q);
  const debouncedLocation = useDebounced(location);

  // Feed: refetch on filter change, ignoring stale responses.
  useEffect(() => {
    let live = true;
    setLoading(true);
    setFeedError(null);
    api
      .feed({ q: debouncedQ, location: debouncedLocation, jobType, remoteOnly, postedWithinDays: days ? Number(days) : "", page })
      .then((r) => live && setFeed(r))
      .catch((e) => live && setFeedError(errorMessage(e)))
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, [debouncedQ, debouncedLocation, jobType, remoteOnly, days, page]);

  useEffect(() => setPage(1), [debouncedQ, debouncedLocation, jobType, remoteOnly, days]);

  useEffect(() => {
    api.myJobs().then(setMine).catch(() => setMine([]));
    loadRecommendations();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function loadRecommendations() {
    setRecsError(null);
    try {
      const r = await api.recommended();
      setRecs(r);
      scoreTop(r);
    } catch (e) {
      setRecsError(errorMessage(e));
    }
  }

  /**
   * Score the best few recommendations by skill fit, one at a time, skipping cached
   * ones and stopping at the first quota error. A refresh costs at most AUTO_SCORE requests.
   */
  async function scoreTop(r: Recommendations) {
    const toScore = r.items.slice(0, AUTO_SCORE).filter((i) => !i.job.match);
    if (toScore.length === 0) return;
    setScoring(true);
    setScoreNote(null);
    for (const item of toScore) {
      try {
        const m = await api.match(item.job.id);
        setRecs((prev) =>
          prev && {
            ...prev,
            items: prev.items.map((x) =>
              x.job.id === item.job.id
                ? { ...x, job: { ...x.job, match: { percentage: m.matchPercentage, metCount: m.requirementsMet.length, missingCount: m.requirementsMissing.length } } }
                : x,
            ),
          },
        );
      } catch (e) {
        if (e instanceof ApiError && e.isRateLimited) setScoreNote(`${e.message} Showing the best matches scored so far.`);
        else if (e instanceof ApiError && e.status === 503) setScoreNote(`${e.message} Recommendations are ranked by skill fit instead.`);
        break;
      }
    }
    setScoring(false);
  }

  // Scored first by match %, then the rest in skill-fit order.
  const ranked = recs
    ? [...recs.items].sort((a, b) => {
        if (a.job.match && b.job.match) return b.job.match.percentage - a.job.match.percentage;
        if (a.job.match) return -1;
        if (b.job.match) return 1;
        return 0;
      })
    : [];
  const visibleRecs = showAllRecs ? ranked : ranked.slice(0, SHOW_FIRST);

  const filteredMine = (mine ?? []).filter((j) =>
    debouncedQ ? `${j.title} ${j.company}`.toLowerCase().includes(debouncedQ.toLowerCase()) : true,
  );

  const updateCard = (card: Card) => {
    setFeed((f) => f && { ...f, jobs: f.jobs.map((j) => (j.id === card.id ? card : j)) });
    setMine((m) => m && m.map((j) => (j.id === card.id ? card : j)));
    setRecs((r) => r && { ...r, items: r.items.map((i) => (i.job.id === card.id ? { ...i, job: card } : i)) });
  };

  async function removeMine(id: number) {
    try {
      await api.removeMyJob(id);
      setMine((m) => m && m.filter((j) => j.id !== id));
      toast("Removed from your jobs");
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  }

  return (
    <div>
      <PageHeader
        title="Jobs"
        description="Live listings from across the web, plus anything you paste in."
        actions={
          <>
            <Button variant="outline" onClick={() => setLogOpen(true)}>
              Log an application
            </Button>
            <Button variant="gradient" onClick={() => setPasteOpen(true)}>
              <Plus className="h-4 w-4" />
              Add a job
            </Button>
          </>
        }
      />

      {/* Search */}
      <div className="card z-30 mb-8 p-3 md:sticky md:top-4">
        <div className="grid gap-2 md:grid-cols-[1.4fr_1fr_auto]">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Job title, skill or company" className="pl-10" aria-label="Search jobs" />
          </div>
          <div className="relative">
            <Globe2 className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={remoteOnly ? "" : location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder={remoteOnly ? "Remote only" : "City or region"}
              className="pl-10"
              disabled={remoteOnly}
              aria-label="Location"
            />
          </div>
          <label className="flex h-[42px] cursor-pointer items-center gap-2 rounded-xl border border-border px-3.5 text-sm font-medium transition-colors hover:bg-muted/50">
            <input type="checkbox" checked={remoteOnly} onChange={(e) => setRemoteOnly(e.target.checked)} className="h-4 w-4 rounded accent-[hsl(var(--primary))]" />
            Remote only
          </label>
        </div>
        <div className="mt-2 flex flex-wrap gap-2">
          <Select value={jobType} onChange={(e) => setJobType(e.target.value as JobType | "")} className="h-9 py-1.5 text-xs" aria-label="Job type">
            <option value="">All job types</option>
            {(Object.keys(JOB_TYPE_LABEL) as JobType[]).map((t) => (
              <option key={t} value={t}>
                {JOB_TYPE_LABEL[t]}
              </option>
            ))}
          </Select>
          <Select value={days} onChange={(e) => setDays(e.target.value)} className="h-9 py-1.5 text-xs" aria-label="Date posted">
            {DATE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {/* Recommended */}
      {!debouncedQ && page === 1 && (
        <section className="mb-12">
          <div className="mb-4 flex flex-wrap items-end justify-between gap-2">
            <div>
              <h2 className="flex items-center gap-2 text-lg font-semibold tracking-tight">
                <Sparkles className="h-4 w-4 text-primary" />
                Recommended for you
              </h2>
              {recs && recs.searchedTitles.length > 0 && (
                <p className="mt-0.5 text-sm text-muted-foreground">
                  Based on {recs.searchedTitles.join(" and ")}
                  {recs.location ? ` near ${recs.location}` : ""}
                  {recs.includeRemote ? ", including remote" : ""}.
                  {scoring && " Scoring the best matches, so the order may change."}
                </p>
              )}
            </div>
            <Button variant="ghost" size="sm" onClick={loadRecommendations}>
              <RefreshCw className={cn("h-3.5 w-3.5", scoring && "animate-spin")} />
              Refresh
            </Button>
          </div>
          {recsError ? (
            <Alert tone="warning">{recsError}</Alert>
          ) : recs === null ? (
            <JobGrid>
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-52 rounded-2xl" />
              ))}
            </JobGrid>
          ) : recs.items.length === 0 ? (
            <Alert tone="info">
              {recs.notice ?? "No recommendations right now. Try again later or search above."}{" "}
              {recs.notice?.includes("Profile") && (
                <Link href="/profile" className="font-semibold text-primary hover:underline">
                  Go to Profile
                </Link>
              )}
            </Alert>
          ) : (
            <>
              {scoreNote && (
                <Alert tone="warning" className="mb-4">
                  {scoreNote}
                </Alert>
              )}
              <JobGrid>
                {visibleRecs.map((r) => (
                  <JobCard
                    key={r.job.id}
                    job={r.job}
                    onChange={updateCard}
                    footer={
                      r.matchedSkills.length > 0 && (
                        <p className="mt-3 line-clamp-2 text-xs text-muted-foreground">
                          <span className="font-medium text-foreground">Matches {r.matchedSkills.length} of your skills:</span>{" "}
                          {r.matchedSkills.slice(0, 5).join(", ")}
                        </p>
                      )
                    }
                  />
                ))}
              </JobGrid>
              {ranked.length > SHOW_FIRST && (
                <div className="mt-4 text-center">
                  <Button variant="outline" size="sm" onClick={() => setShowAllRecs((v) => !v)}>
                    <ChevronDown className={cn("h-4 w-4 transition-transform", showAllRecs && "rotate-180")} />
                    {showAllRecs ? "Show fewer" : `Show ${ranked.length - SHOW_FIRST} more`}
                  </Button>
                </div>
              )}
            </>
          )}
        </section>
      )}

      {/* Your jobs */}
      {filteredMine.length > 0 && page === 1 && (
        <section className="mb-12">
          <h2 className="mb-4 text-lg font-semibold tracking-tight">Added by you</h2>
          <JobGrid>
            {filteredMine.map((j) => (
              <JobCard
                key={j.id}
                job={j}
                onChange={updateCard}
                footer={
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      removeMine(j.id);
                    }}
                    className="mt-3 inline-flex items-center gap-1 self-start text-xs text-muted-foreground hover:text-danger"
                  >
                    <Trash2 className="h-3 w-3" /> Remove
                  </button>
                }
              />
            ))}
          </JobGrid>
        </section>
      )}

      {/* Feed */}
      <section>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold tracking-tight">{debouncedQ ? `Results for “${debouncedQ}”` : "Latest jobs"}</h2>
          {feed && feed.sources.length > 0 && (
            <div className="flex gap-1.5">
              {feed.sources.map((s) => (
                <Badge key={s}>{s}</Badge>
              ))}
            </div>
          )}
        </div>
        {feed?.notice && (
          <Alert tone="warning" className="mb-4">
            {feed.notice}
          </Alert>
        )}
        {feedError ? (
          <Alert>{feedError}</Alert>
        ) : loading && !feed ? (
          <JobGrid>
            {[0, 1, 2, 3, 4, 5].map((i) => (
              <Skeleton key={i} className="h-52 rounded-2xl" />
            ))}
          </JobGrid>
        ) : feed && feed.jobs.length === 0 ? (
          <EmptyState
            icon={debouncedQ ? SearchX : Inbox}
            title="No jobs found"
            description="Try a broader search, a different location, or paste a job you found elsewhere."
            action={
              <Button variant="outline" onClick={() => setPasteOpen(true)}>
                <Plus className="h-4 w-4" /> Add a job
              </Button>
            }
          />
        ) : (
          <div className={cn("transition-opacity", loading && "opacity-60")}>
            <JobGrid>{feed?.jobs.map((j) => <JobCard key={j.id} job={j} onChange={updateCard} />)}</JobGrid>
            <div className="mt-8 flex items-center justify-center gap-3">
              <Button variant="outline" size="sm" disabled={page === 1 || loading} onClick={() => setPage((p) => p - 1)}>
                Previous
              </Button>
              <span className="text-sm text-muted-foreground">Page {page}</span>
              <Button variant="outline" size="sm" disabled={!feed?.hasMore || loading} onClick={() => setPage((p) => p + 1)}>
                Next
              </Button>
            </div>
          </div>
        )}
      </section>

      <PasteJobDialog open={pasteOpen} onClose={() => setPasteOpen(false)} onAdded={(d) => setMine((m) => [d.job, ...(m ?? []).filter((j) => j.id !== d.job.id)])} />
      <LogApplicationDialog open={logOpen} onClose={() => setLogOpen(false)} />
    </div>
  );
}
