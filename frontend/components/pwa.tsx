"use client";

import { useEffect, useState } from "react";
import { Download, WifiOff, X } from "lucide-react";
import { Button } from "@/components/ui/button";

const SNOOZE_KEY = "applymate_install_snoozed_until";
const SNOOZE_DAYS = 14;

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

/** Registers the service worker (production only: in dev it would cache stale bundles). */
export function useServiceWorker() {
  useEffect(() => {
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;
    navigator.serviceWorker.register("/sw.js").catch(() => undefined);
  }, []);
}

/** A thin banner while the browser reports no connection. */
export function OfflineBanner() {
  const [offline, setOffline] = useState(false);
  useEffect(() => {
    const update = () => setOffline(!navigator.onLine);
    update();
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);
  if (!offline) return null;
  return (
    <div role="status" className="no-print fixed inset-x-0 top-0 z-[100] flex items-center justify-center gap-2 bg-warning px-4 py-1.5 text-xs font-medium text-black">
      <WifiOff className="h-3.5 w-3.5" /> You&apos;re offline. Changes will fail until you reconnect.
    </div>
  );
}

/**
 * Offers to install the app when the browser allows it. Dismissing snoozes it for two
 * weeks rather than asking on every visit.
 */
export function InstallPrompt() {
  const [event, setEvent] = useState<BeforeInstallPromptEvent | null>(null);

  useEffect(() => {
    const onPrompt = (e: Event) => {
      e.preventDefault();
      let snoozed = false;
      try {
        snoozed = Number(localStorage.getItem(SNOOZE_KEY) ?? 0) > Date.now();
      } catch {
        // storage unavailable: show it
      }
      if (!snoozed) setEvent(e as BeforeInstallPromptEvent);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);
    return () => window.removeEventListener("beforeinstallprompt", onPrompt);
  }, []);

  if (!event) return null;

  function snooze() {
    try {
      localStorage.setItem(SNOOZE_KEY, String(Date.now() + SNOOZE_DAYS * 86400000));
    } catch {
      // ignore
    }
    setEvent(null);
  }

  async function install() {
    await event!.prompt();
    const choice = await event!.userChoice;
    if (choice.outcome === "dismissed") snooze();
    else setEvent(null);
  }

  return (
    <div className="no-print fixed inset-x-4 bottom-24 z-[60] mx-auto flex max-w-md animate-slide-up items-center gap-3 rounded-2xl border border-border bg-card p-4 shadow-lift md:bottom-6 md:left-auto md:right-6 md:mx-0">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-accent text-white">
        <Download className="h-5 w-5" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold">Install ApplyMate</p>
        <p className="text-xs text-muted-foreground">Open it from your home screen like an app.</p>
      </div>
      <Button size="sm" onClick={install}>
        Install
      </Button>
      <button onClick={snooze} className="rounded-lg p-1 text-muted-foreground hover:text-foreground" aria-label="Not now">
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
