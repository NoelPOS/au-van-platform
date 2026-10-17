import { useMutation } from "@tanstack/react-query";
import type { FormEvent } from "react";
import { Button } from "./ui/Button";
import { Input } from "./ui/Input";
import { DemoSignInUnavailable } from "../services/authService";
import { startDemoStaff } from "../services/demoSession";
import type { AuthSession } from "../types/auth";

export function DemoStaffSignIn({
  onSignedIn,
}: {
  onSignedIn: (session: AuthSession) => void;
}) {
  const signIn = useMutation({
    mutationFn: startDemoStaff,
    onSuccess: (session) => onSignedIn(session),
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    signIn.mutate(String(new FormData(event.currentTarget).get("passcode")));
  }

  if (signIn.error instanceof DemoSignInUnavailable) {
    return (
      <p className="rounded-xl border border-line bg-card px-4 py-3 text-sm text-muted">
        Staff demo is not switched on.
      </p>
    );
  }

  return (
    <form aria-label="Staff demo" className="flex flex-col gap-2" onSubmit={submit}>
      <div className="flex flex-wrap items-end gap-3">
        <Input
          aria-invalid={signIn.isError || undefined}
          autoComplete="off"
          className="min-w-0 flex-1 basis-48"
          label="Demo staff passcode"
          name="passcode"
          required
          type="password"
        />
        <Button disabled={signIn.isPending} type="submit" variant="secondary">
          {signIn.isPending ? "Opening…" : "Open staff demo"}
        </Button>
      </div>
      {signIn.error && (
        <p className="text-sm text-danger" role="alert">
          {signIn.error.message}
        </p>
      )}
    </form>
  );
}
