"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  BarChart3,
  Bookmark,
  Briefcase,
  CalendarDays,
  History,
  Home,
  KanbanSquare,
  LogOut,
  Menu,
  Moon,
  Search,
  Sun,
  UserRound,
  X,
} from "lucide-react";
import { useTheme } from "@/components/providers";
import { CommandPalette, openCommandPalette } from "@/components/layout/command-palette";
import { LogApplicationDialog, PasteJobDialog } from "@/components/jobs/add-job-dialogs";
import { Logo } from "@/components/ui/visuals";
import { api, getStoredUser } from "@/lib/api";
import { cn } from "@/lib/utils";

const NAV = [
  { href: "/dashboard", label: "Home", icon: Home },
  { href: "/jobs", label: "Jobs", icon: Briefcase },
  { href: "/saved", label: "Saved", icon: Bookmark },
  { href: "/tracking", label: "Tracking", icon: KanbanSquare },
  { href: "/history", label: "History", icon: History },
  { href: "/calendar", label: "Calendar", icon: CalendarDays },
  { href: "/analytics", label: "Analytics", icon: BarChart3 },
  { href: "/profile", label: "Profile", icon: UserRound },
];

// The five most-used pages get the phone tab bar; the rest live in the menu.
const TABS = ["/dashboard", "/jobs", "/tracking", "/calendar", "/profile"];

function isActive(pathname: string, href: string) {
  if (href === "/jobs") return pathname === "/jobs" || pathname.startsWith("/jobs/");
  if (href === "/tracking") return pathname.startsWith("/tracking") || pathname.startsWith("/applications");
  return pathname === href || pathname.startsWith(href + "/");
}

function ThemeButton({ className }: { className?: string }) {
  const { theme, toggle } = useTheme();
  return (
    <button
      onClick={toggle}
      className={cn("rounded-xl p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground", className)}
      aria-label={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}
    >
      {theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
    </button>
  );
}

function NavList({ pathname, onNavigate }: { pathname: string; onNavigate?: () => void }) {
  return (
    <nav className="flex flex-col gap-0.5" aria-label="Main">
      {NAV.map(({ href, label, icon: Icon }) => {
        const active = isActive(pathname, href);
        return (
          <Link
            key={href}
            href={href}
            onClick={onNavigate}
            aria-current={active ? "page" : undefined}
            className={cn(
              "group relative flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition-colors",
              active ? "bg-card text-foreground shadow-soft ring-1 ring-border" : "text-muted-foreground hover:bg-muted/70 hover:text-foreground",
            )}
          >
            <Icon className={cn("h-4 w-4 transition-colors", active ? "text-primary" : "group-hover:text-foreground")} />
            {label}
          </Link>
        );
      })}
    </nav>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [drawer, setDrawer] = useState(false);
  const [dialog, setDialog] = useState<"add-job" | "log-application" | null>(null);
  const [mac, setMac] = useState(false);

  useEffect(() => setMac(/Mac|iPhone|iPad/.test(navigator.platform)), []);
  const [user, setUser] = useState<{ email: string; name: string | null } | null>(null);

  useEffect(() => setUser(getStoredUser()), []);
  useEffect(() => setDrawer(false), [pathname]);

  async function logout() {
    await api.logout();
    router.replace("/login");
  }

  const display = user?.name || user?.email?.split("@")[0] || "";

  const userCard = (
    <div className="flex items-center gap-3 rounded-xl p-2">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary to-accent text-sm font-semibold text-white">
        {display.slice(0, 1).toUpperCase() || "?"}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{display}</p>
        <p className="truncate text-xs text-muted-foreground">{user?.email}</p>
      </div>
      <button
        onClick={logout}
        className="rounded-lg p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        aria-label="Sign out"
        title="Sign out"
      >
        <LogOut className="h-4 w-4" />
      </button>
    </div>
  );

  return (
    <div className="min-h-screen">
      {/* Desktop sidebar */}
      <aside className="no-print fixed inset-y-0 left-0 z-40 hidden w-64 flex-col border-r border-border bg-muted/30 px-4 py-5 backdrop-blur md:flex">
        <div className="mb-6 flex items-center justify-between px-2">
          <Link href="/dashboard" aria-label="ApplyMate home">
            <Logo />
          </Link>
          <ThemeButton />
        </div>
        <button
          onClick={openCommandPalette}
          className="mb-5 flex items-center gap-2 rounded-xl border border-border bg-card px-3 py-2 text-sm text-muted-foreground shadow-sm transition-colors hover:border-foreground/20 hover:text-foreground"
        >
          <Search className="h-4 w-4" />
          Search
          <kbd className="ml-auto rounded-md border border-border bg-muted px-1.5 py-0.5 text-[10px] font-medium">{mac ? "⌘K" : "Ctrl K"}</kbd>
        </button>
        <NavList pathname={pathname} />
        <div className="mt-auto border-t border-border pt-4">{userCard}</div>
      </aside>

      {/* Mobile top bar */}
      <header className="no-print sticky top-0 z-40 flex items-center justify-between border-b border-border bg-background/80 px-4 py-3 backdrop-blur-xl md:hidden">
        <Link href="/dashboard" aria-label="ApplyMate home">
          <Logo />
        </Link>
        <div className="flex items-center gap-1">
          <button onClick={openCommandPalette} className="rounded-xl p-2 text-muted-foreground hover:bg-muted" aria-label="Search">
            <Search className="h-4 w-4" />
          </button>
          <ThemeButton />
          <button onClick={() => setDrawer(true)} className="rounded-xl p-2 text-muted-foreground hover:bg-muted" aria-label="Open menu">
            <Menu className="h-5 w-5" />
          </button>
        </div>
      </header>

      {/* Mobile drawer */}
      {drawer && (
        <div className="fixed inset-0 z-[70] md:hidden">
          <div className="absolute inset-0 animate-fade-in bg-black/40 backdrop-blur-sm" onClick={() => setDrawer(false)} />
          <div className="absolute inset-y-0 right-0 flex w-72 animate-slide-up flex-col bg-background p-4 shadow-lift">
            <div className="mb-6 flex items-center justify-between">
              <Logo />
              <button onClick={() => setDrawer(false)} className="rounded-xl p-2 text-muted-foreground hover:bg-muted" aria-label="Close menu">
                <X className="h-5 w-5" />
              </button>
            </div>
            <NavList pathname={pathname} onNavigate={() => setDrawer(false)} />
            <div className="mt-auto border-t border-border pt-4">{userCard}</div>
          </div>
        </div>
      )}

      <main className="md:pl-64 print:p-0">
        <div className="mx-auto w-full max-w-6xl px-4 pb-28 pt-6 sm:px-6 md:pb-12 md:pt-10 lg:px-10 print:max-w-none print:p-0">{children}</div>
      </main>

      <CommandPalette onAction={setDialog} />
      <PasteJobDialog open={dialog === "add-job"} onClose={() => setDialog(null)} onAdded={(d) => router.push(`/jobs/${d.job.id}`)} />
      <LogApplicationDialog open={dialog === "log-application"} onClose={() => setDialog(null)} />

      {/* Mobile tab bar */}
      <nav
        className="no-print fixed inset-x-0 bottom-0 z-40 grid grid-cols-5 border-t border-border bg-background/90 pb-[env(safe-area-inset-bottom)] backdrop-blur-xl md:hidden"
        aria-label="Tabs"
      >
        {NAV.filter((n) => TABS.includes(n.href)).map(({ href, label, icon: Icon }) => {
          const active = isActive(pathname, href);
          return (
            <Link
              key={href}
              href={href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex flex-col items-center gap-1 py-2.5 text-[11px] font-medium transition-colors",
                active ? "text-primary" : "text-muted-foreground",
              )}
            >
              <Icon className="h-5 w-5" />
              {label}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
