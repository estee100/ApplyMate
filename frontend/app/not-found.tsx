import { Compass } from "lucide-react";
import { ButtonLink } from "@/components/ui/button";

export default function NotFound() {
  return (
    <div className="aurora flex min-h-screen flex-col items-center justify-center px-4 text-center">
      <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
        <Compass className="h-6 w-6" />
      </span>
      <h1 className="mt-6 text-3xl font-semibold tracking-tight">Page not found</h1>
      <p className="mt-2 text-muted-foreground">That page doesn&apos;t exist, or it has moved.</p>
      <ButtonLink href="/dashboard" className="mt-8">
        Back to ApplyMate
      </ButtonLink>
    </div>
  );
}
