import { WifiOff } from "lucide-react";

export const metadata = { title: "Offline" };

/** Precached by the service worker and shown when a page can't load without a connection. */
export default function OfflinePage() {
  return (
    <div className="aurora flex min-h-screen flex-col items-center justify-center px-4 text-center">
      <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
        <WifiOff className="h-6 w-6" />
      </span>
      <h1 className="mt-6 text-3xl font-semibold tracking-tight">You&apos;re offline</h1>
      <p className="mt-2 max-w-sm text-muted-foreground">ApplyMate needs a connection to load your jobs and applications. It&apos;ll pick up where you left off.</p>
      <a href="/dashboard" className="mt-8 inline-flex h-10 items-center rounded-xl bg-foreground px-4 text-sm font-medium text-background">
        Try again
      </a>
    </div>
  );
}
