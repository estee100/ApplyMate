import { ArrowUpRight } from "lucide-react";
import { cn, safeHref } from "@/lib/utils";

/**
 * The outbound link to the original posting: the only route to actually applying,
 * so it is the primary action wherever a job is shown. Hidden, not dead, without a URL.
 */
export function ApplyLink({
  url,
  publisher,
  size = "md",
  className,
}: {
  url: string | null;
  publisher: string | null;
  size?: "sm" | "md";
  className?: string;
}) {
  const href = safeHref(url);
  if (!href) return null;
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(e) => e.stopPropagation()}
      className={cn(
        "inline-flex items-center justify-center gap-1.5 rounded-xl bg-foreground font-medium text-background shadow-sm transition-all hover:bg-foreground/90 active:scale-[0.98]",
        size === "sm" ? "h-8 px-3 text-xs" : "h-10 px-4 text-sm",
        className,
      )}
    >
      {publisher ? `Apply on ${publisher}` : "Apply"}
      <ArrowUpRight className={size === "sm" ? "h-3.5 w-3.5" : "h-4 w-4"} />
    </a>
  );
}
