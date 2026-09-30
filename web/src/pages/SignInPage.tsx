import { useEffect, useState } from "react";
import { E2eSignIn } from "../components/E2eSignIn";
import { Button } from "../components/ui/Button";
import { apiBaseUrl } from "../services/apiBaseUrl";
import { configuredLiffId, e2eAuthEnabled } from "../services/liffConfig";
import { createLiffSession } from "../services/liffService";
import type { AuthSession } from "../types/auth";

type HealthState = "checking" | "available" | "unavailable";

export function SignInPage({
  onSignedIn,
}: {
  onSignedIn: (session: AuthSession) => void;
}) {
  const [healthState, setHealthState] = useState<HealthState>("checking");
  const [signInError, setSignInError] = useState<string | null>(null);
  const [signingIn, setSigningIn] = useState(false);

  useEffect(() => {
    async function checkApiHealth() {
      try {
        const response = await fetch(`${apiBaseUrl}/actuator/health`);
        setHealthState(response.ok ? "available" : "unavailable");
      } catch {
        setHealthState("unavailable");
      }
    }

    void checkApiHealth();
  }, []);

  async function signIn() {
    setSigningIn(true);
    setSignInError(null);
    try {
      onSignedIn(await createLiffSession());
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "LINE sign-in could not be completed.";
      if (message !== "Redirecting to LINE sign-in.") setSignInError(message);
    } finally {
      setSigningIn(false);
    }
  }

  return (
    <main className="mx-auto max-w-2xl px-6 py-28">
      <p className="text-xs font-bold uppercase tracking-widest text-brand">
        AU-Van platform
      </p>
      <h1 className="my-4 text-5xl font-bold tracking-tight text-ink">
        Van bookings
      </h1>
      <p className="max-w-xl text-lg leading-7 text-muted">
        Sign in with LINE to book a seat on the next van. Approved staff
        accounts land in the transport inventory instead.
      </p>
      {!configuredLiffId() ? (
        <p className="mt-6 rounded-xl bg-stone-100 px-4 py-3 text-sm text-muted">
          Add your LIFF ID to <code>web/.env</code> before signing in.
        </p>
      ) : (
        <div className="mt-6">
          <Button disabled={signingIn} onClick={() => void signIn()}>
            {signingIn ? "Signing in…" : "Sign in with LINE"}
          </Button>
        </div>
      )}
      {/* A build-time constant: any build without VITE_E2E_AUTH=true drops this
          control, and Container checks greps the bundle to prove it. */}
      {e2eAuthEnabled && <E2eSignIn onSignedIn={onSignedIn} />}
      {signInError && (
        <p
          className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700"
          role="alert"
        >
          {signInError}
        </p>
      )}
      <section
        className="mt-10 flex items-center gap-4 rounded-2xl border border-line bg-white p-5"
        aria-live="polite"
      >
        <span
          className={`h-3 w-3 rounded-full ${healthState === "available" ? "bg-emerald-700" : healthState === "unavailable" ? "bg-red-700" : "bg-stone-400"}`}
          aria-hidden="true"
        />
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-brand">
            API health
          </p>
          <p className="mt-1 text-sm text-ink">
            {healthState === "checking" && "Checking local API…"}
            {healthState === "available" && "Available"}
            {healthState === "unavailable" &&
              "Unavailable — start the API on port 8080"}
          </p>
        </div>
      </section>
    </main>
  );
}
