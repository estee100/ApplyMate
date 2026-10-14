"use client";

import Link from "next/link";
import { useState } from "react";
import { MailCheck } from "lucide-react";
import { AuthShell } from "@/components/layout/auth-shell";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { Alert } from "@/components/ui/misc";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/utils";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      setSent((await api.forgotPassword(email)).message);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell
      title="Reset your password"
      subtitle="Enter your account email and we'll send you a link to choose a new password."
      footer={
        <Link href="/login" className="font-semibold text-foreground hover:text-primary">
          Back to sign in
        </Link>
      }
    >
      {sent ? (
        <div className="card flex flex-col items-center p-8 text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-success/10 text-success">
            <MailCheck className="h-5 w-5" />
          </span>
          <p className="mt-4 font-medium">Check your inbox</p>
          <p className="mt-1 text-sm text-muted-foreground">{sent} The link works for 30 minutes.</p>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-5">
          {error && <Alert>{error}</Alert>}
          <Field label="Email">
            {(p) => <Input {...p} type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />}
          </Field>
          <Button type="submit" variant="gradient" size="lg" className="w-full" loading={loading}>
            Send reset link
          </Button>
        </form>
      )}
    </AuthShell>
  );
}
