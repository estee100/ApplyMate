"use client";

import { useState } from "react";
import { cn, matchBand } from "@/lib/utils";

/** A circular match score. Colour comes from the band, never implying certainty. */
export function MatchRing({ value, size = 56, stroke = 5, className }: { value: number; size?: number; stroke?: number; className?: string }) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const band = matchBand(value);
  return (
    <div className={cn("relative shrink-0", className)} style={{ width: size, height: size }} aria-label={`${value}% match`}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} className="stroke-muted" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={c - (c * Math.max(0, Math.min(100, value))) / 100}
          className={cn(band.ring, "transition-[stroke-dashoffset] duration-700 ease-out")}
        />
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-[13px] font-semibold tabular-nums">{value}%</span>
    </div>
  );
}

const PALETTE = [
  "from-violet-500 to-indigo-500",
  "from-sky-500 to-cyan-500",
  "from-emerald-500 to-teal-500",
  "from-amber-500 to-orange-500",
  "from-rose-500 to-pink-500",
  "from-fuchsia-500 to-purple-500",
];

/** Company logo with a gradient monogram fallback. Logos are https-only (enforced server-side). */
export function CompanyLogo({ name, src, size = "md" }: { name: string; src?: string | null; size?: "sm" | "md" | "lg" }) {
  const [failed, setFailed] = useState(false);
  const dims = { sm: "h-8 w-8 text-xs rounded-lg", md: "h-11 w-11 text-sm rounded-xl", lg: "h-14 w-14 text-base rounded-2xl" }[size];
  const initials = (name || "?")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join("");
  const hue = PALETTE[[...(name || "")].reduce((a, ch) => a + ch.charCodeAt(0), 0) % PALETTE.length];

  if (src && !failed) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img
        src={src}
        alt=""
        referrerPolicy="no-referrer"
        loading="lazy"
        onError={() => setFailed(true)}
        className={cn(dims, "shrink-0 border border-border bg-white object-contain p-1")}
      />
    );
  }
  return (
    <span className={cn(dims, "flex shrink-0 items-center justify-center bg-gradient-to-br font-semibold text-white shadow-sm", hue)}>
      {initials || "?"}
    </span>
  );
}

/** Wordmark. */
export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-2 font-semibold tracking-tight", className)}>
      <span className="relative flex h-8 w-8 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-accent shadow-glow">
        <svg viewBox="0 0 24 24" className="h-4 w-4 text-white" fill="none" stroke="currentColor" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round">
          <path d="M5 13l4 4L19 7" />
        </svg>
      </span>
      <span>
        Apply<span className="text-gradient">Mate</span>
      </span>
    </span>
  );
}
