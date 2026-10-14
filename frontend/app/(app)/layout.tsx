"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { AppShell } from "@/components/layout/app-shell";
import { Spinner } from "@/components/ui/misc";
import { getToken } from "@/lib/api";

/**
 * Every page in this group needs a session. The check is client-side because the
 * token lives in localStorage; the API enforces auth regardless, so this only spares
 * a signed-out visitor a page of failed requests.
 */
export default function AuthedLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!getToken()) {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
      return;
    }
    setReady(true);
  }, [router, pathname]);

  if (!ready) return <Spinner className="min-h-screen" />;
  return <AppShell>{children}</AppShell>;
}
