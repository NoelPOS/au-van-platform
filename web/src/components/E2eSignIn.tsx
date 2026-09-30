import { useState, type FormEvent } from "react";
import { Button } from "./ui/Button";
import { exchangeLineIdToken } from "../services/authService";
import type { AuthSession } from "../types/auth";

export function E2eSignIn({
  onSignedIn,
}: {
  onSignedIn: (session: AuthSession) => void;
}) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const subject = String(
      new FormData(event.currentTarget).get("subject"),
    ).trim();
    setBusy(true);
    setError(null);
    try {
      onSignedIn(await exchangeLineIdToken(subject));
    } catch (failure) {
      setError(
        failure instanceof Error
          ? failure.message
          : "The test sign-in could not be completed.",
      );
    } finally {
      setBusy(false);
    }
  }

  // CI and the e2e global setup grep for this data-testid: never rename it.
  return (
    <form
      className="mt-6 flex flex-wrap items-end gap-2"
      data-testid="auvan-e2e-sign-in"
      onSubmit={(event) => void submit(event)}
    >
      <label className="text-sm font-semibold text-ink">
        End-to-end sign-in subject
        <input
          className="mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 font-normal text-ink outline-none focus:border-brand"
          name="subject"
          required
        />
      </label>
      <Button disabled={busy} type="submit">
        {busy ? "Signing in…" : "Sign in as subject"}
      </Button>
      {error && (
        <p
          className="w-full rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700"
          role="alert"
        >
          {error}
        </p>
      )}
    </form>
  );
}
