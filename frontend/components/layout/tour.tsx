"use client";

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ArrowLeft, ArrowRight, X } from "lucide-react";
import { Button } from "@/components/ui/button";

const DONE_KEY = "applymate_tour_done";
export const TOUR_EVENT = "applymate:tour";

interface Step {
  target: string;
  title: string;
  body: string;
}

const STEPS: Step[] = [
  { target: "hero", title: "Welcome to ApplyMate", body: "Your dashboard shows what needs attention: follow-ups, upcoming next steps and how your search is going." },
  { target: "nav-profile", title: "Start with your resume", body: "Upload it on Profile. It powers match scores, recommendations and every tailored resume." },
  { target: "nav-jobs", title: "Find and add jobs", body: "Search live listings, see recommendations, or paste any posting. Open a job to see how well you match." },
  { target: "nav-tracking", title: "Track every application", body: "Drag cards from Applied to Offer. Each move is kept on the application's timeline." },
  { target: "search", title: "Jump anywhere", body: "Press Ctrl+K (⌘K on a Mac) to search your applications and jobs, or run any action." },
];

/** Fire to replay the tour from anywhere. */
export function startTour() {
  window.dispatchEvent(new Event(TOUR_EVENT));
}

function visibleTarget(name: string): HTMLElement | null {
  const nodes = Array.from(document.querySelectorAll<HTMLElement>(`[data-tour="${name}"]`));
  return nodes.find((n) => n.offsetParent !== null && n.getBoundingClientRect().width > 0) ?? null;
}

/**
 * A guided tour: the current target is spotlit through a cut-out in a dimmed overlay,
 * with a tooltip and arrow beside it. Steps whose target is not on screen (sidebar
 * items on a phone) are skipped, and the position follows resizes and scrolling.
 */
export function Tour({ autoStart }: { autoStart: boolean }) {
  const [steps, setSteps] = useState<Step[]>([]);
  const [index, setIndex] = useState(-1);
  const [rect, setRect] = useState<DOMRect | null>(null);
  const tooltip = useRef<HTMLDivElement>(null);
  const [tipSize, setTipSize] = useState({ w: 320, h: 160 });

  const begin = useCallback(() => {
    const available = STEPS.filter((s) => visibleTarget(s.target));
    if (available.length === 0) return;
    setSteps(available);
    setIndex(0);
  }, []);

  const finish = useCallback(() => {
    setIndex(-1);
    try {
      localStorage.setItem(DONE_KEY, "1");
    } catch {
      // private mode: it will offer itself again next time
    }
  }, []);

  useEffect(() => {
    window.addEventListener(TOUR_EVENT, begin);
    let seen = false;
    try {
      seen = localStorage.getItem(DONE_KEY) === "1";
    } catch {
      seen = true;
    }
    // Wait a beat for the page to lay out before measuring targets.
    const t = autoStart && !seen ? setTimeout(begin, 700) : undefined;
    return () => {
      window.removeEventListener(TOUR_EVENT, begin);
      if (t) clearTimeout(t);
    };
  }, [autoStart, begin]);

  const step = index >= 0 ? steps[index] : null;

  const measure = useCallback(() => {
    if (!step) return;
    const el = visibleTarget(step.target);
    if (!el) return;
    setRect(el.getBoundingClientRect());
  }, [step]);

  useLayoutEffect(() => {
    if (!step) return;
    visibleTarget(step.target)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
    measure();
    const onChange = () => measure();
    window.addEventListener("resize", onChange);
    window.addEventListener("scroll", onChange, true);
    const timer = setTimeout(measure, 350); // after a smooth scroll settles
    return () => {
      window.removeEventListener("resize", onChange);
      window.removeEventListener("scroll", onChange, true);
      clearTimeout(timer);
    };
  }, [step, measure]);

  useLayoutEffect(() => {
    if (tooltip.current) {
      const r = tooltip.current.getBoundingClientRect();
      setTipSize({ w: r.width, h: r.height });
      tooltip.current.focus();
    }
  }, [index, rect]);

  useEffect(() => {
    if (!step) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") finish();
      if (e.key === "ArrowRight") setIndex((i) => (i + 1 < steps.length ? i + 1 : i));
      if (e.key === "ArrowLeft") setIndex((i) => Math.max(0, i - 1));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [step, steps.length, finish]);

  if (!step || !rect || typeof document === "undefined") return null;

  const pad = 8;
  const hole = { x: rect.left - pad, y: rect.top - pad, w: rect.width + pad * 2, h: rect.height + pad * 2 };
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const gap = 16;
  // Prefer the right of the target (sidebar items), then below, then above.
  let placement: "right" | "below" | "above" = "right";
  if (hole.x + hole.w + gap + tipSize.w > vw - 12) placement = hole.y + hole.h + gap + tipSize.h < vh - 12 ? "below" : "above";
  let left: number;
  let top: number;
  if (placement === "right") {
    left = hole.x + hole.w + gap;
    top = Math.min(Math.max(12, hole.y + hole.h / 2 - tipSize.h / 2), vh - tipSize.h - 12);
  } else {
    left = Math.min(Math.max(12, hole.x + hole.w / 2 - tipSize.w / 2), vw - tipSize.w - 12);
    top = placement === "below" ? hole.y + hole.h + gap : hole.y - gap - tipSize.h;
  }
  const last = index === steps.length - 1;

  return createPortal(
    <div className="fixed inset-0 z-[95]" aria-live="polite">
      <svg className="absolute inset-0 h-full w-full" onClick={finish} aria-hidden>
        <defs>
          <mask id="tour-hole">
            <rect width="100%" height="100%" fill="white" />
            <rect x={hole.x} y={hole.y} width={hole.w} height={hole.h} rx="14" fill="black" className="transition-all duration-300" />
          </mask>
        </defs>
        <rect width="100%" height="100%" fill="rgba(0,0,0,0.55)" mask="url(#tour-hole)" />
        <rect
          x={hole.x}
          y={hole.y}
          width={hole.w}
          height={hole.h}
          rx="14"
          fill="none"
          className="stroke-primary transition-all duration-300"
          strokeWidth="2"
        />
      </svg>
      <div
        ref={tooltip}
        role="dialog"
        aria-modal="true"
        aria-label={step.title}
        tabIndex={-1}
        style={{ left, top }}
        className="absolute w-[min(320px,calc(100vw-24px))] animate-scale-in rounded-2xl border border-border bg-card p-5 shadow-lift outline-none"
      >
        <span
          aria-hidden
          className="absolute h-3 w-3 rotate-45 border-border bg-card"
          style={
            placement === "right"
              ? { left: -7, top: Math.min(Math.max(16, hole.y + hole.h / 2 - top - 6), tipSize.h - 20), borderLeftWidth: 1, borderBottomWidth: 1 }
              : placement === "below"
                ? { top: -7, left: Math.min(Math.max(16, hole.x + hole.w / 2 - left - 6), tipSize.w - 20), borderLeftWidth: 1, borderTopWidth: 1 }
                : { bottom: -7, left: Math.min(Math.max(16, hole.x + hole.w / 2 - left - 6), tipSize.w - 20), borderRightWidth: 1, borderBottomWidth: 1 }
          }
        />
        <div className="flex items-start justify-between gap-3">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-primary">
            Step {index + 1} of {steps.length}
          </p>
          <button onClick={finish} className="-m-1 rounded-lg p-1 text-muted-foreground hover:bg-muted hover:text-foreground" aria-label="Skip tour">
            <X className="h-4 w-4" />
          </button>
        </div>
        <h3 className="mt-1 font-semibold tracking-tight">{step.title}</h3>
        <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{step.body}</p>
        <div className="mt-4 flex items-center justify-between">
          <div className="flex gap-1" aria-hidden>
            {steps.map((_, i) => (
              <span key={i} className={`h-1.5 rounded-full transition-all ${i === index ? "w-5 bg-primary" : "w-1.5 bg-border"}`} />
            ))}
          </div>
          <div className="flex gap-2">
            {index > 0 && (
              <Button size="sm" variant="ghost" onClick={() => setIndex(index - 1)} aria-label="Previous step">
                <ArrowLeft className="h-3.5 w-3.5" />
              </Button>
            )}
            <Button size="sm" variant={last ? "gradient" : "primary"} onClick={() => (last ? finish() : setIndex(index + 1))}>
              {last ? "Get started" : "Next"}
              {!last && <ArrowRight className="h-3.5 w-3.5" />}
            </Button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}
