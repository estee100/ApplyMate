"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { cn } from "@/lib/utils";

// Object URLs by avatar version, shared by every avatar on the page.
const cache = new Map<string, Promise<string | null>>();

/** Fires when the profile picture changes, so every avatar re-renders. */
export const AVATAR_EVENT = "applymate:avatar";

export function announceAvatar(version: string | null) {
  try {
    const raw = localStorage.getItem("applymate_user");
    if (raw) localStorage.setItem("applymate_user", JSON.stringify({ ...JSON.parse(raw), avatarVersion: version }));
  } catch {
    // storage unavailable
  }
  window.dispatchEvent(new CustomEvent(AVATAR_EVENT, { detail: version }));
}

/**
 * The user's picture, or a gradient initial. The image needs the bearer token, so it is
 * fetched as a blob rather than used as a plain <img src>, and cached by version.
 */
export function UserAvatar({ name, version, className }: { name: string; version: string | null | undefined; className?: string }) {
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    if (!version) {
      setSrc(null);
      return;
    }
    if (!cache.has(version)) cache.set(version, api.avatar(version).catch(() => null));
    cache.get(version)!.then((url) => live && setSrc(url));
    return () => {
      live = false;
    };
  }, [version]);

  const initial = (name || "?").trim().slice(0, 1).toUpperCase();
  return src ? (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt="" className={cn("shrink-0 rounded-full object-cover ring-2 ring-background", className)} />
  ) : (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary to-accent font-semibold text-white",
        className,
      )}
    >
      {initial}
    </span>
  );
}
