import { useState, type FormEvent } from "react";
import { Button } from "./ui/Button";
import { exchangeLineIdToken, type AuthSession } from "../services/authService";

/**
 * Sign-in for the Playwright suite, and for nothing else (ADR-013).
 *
 * <p>It skips `liff.init()` — which contacts LINE and cannot be driven by a
 * browser automation — and posts the typed subject to the ordinary exchange
 * endpoint as an id token. That is the whole of it: `exchangeLineIdToken` is
 * reused rather than wrapped, so the E2E path and the real path share one
 * client and one failure handling, and there is no second thing to keep right.
 *
 * <p><strong>This is not an authentication bypass.</strong> The API verifies
 * the string exactly as it verifies a real id token, against
 * `auth.line.api-base-url`. A subject typed here is accepted only by an
 * instance deliberately pointed at a verification double, which only
 * `compose.e2e.yaml` does; against the default `https://api.line.me` it is
 * refused like any other made-up token.
 *
 * <p>`App.tsx` renders this behind `e2eAuthEnabled()`, so nothing below reaches
 * a production bundle. The marker on the form is what `Container checks` greps
 * for to prove that.
 */
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
