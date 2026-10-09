import { useEffect, useRef, useState } from "react";
import { E2eSignIn } from "../components/E2eSignIn";
import { Button } from "../components/ui/Button";
import { FailurePanel } from "../components/ui/FailurePanel";
import { Skeleton } from "../components/ui/Skeleton";
import { apiBaseUrl } from "../services/apiBaseUrl";
import { configuredLiffId, e2eAuthEnabled } from "../services/liffConfig";
import {
  createLiffSession,
  resumeLiffSession,
} from "../services/liffService";
import type { AuthSession } from "../types/auth";

type HealthState = "checking" | "available" | "unavailable";

function asError(failure: unknown): Error {
  return failure instanceof Error
    ? failure
    : new Error("LINE sign-in could not be completed.");
}

export function SignInPage({
  onSignedIn,
}: {
  onSignedIn: (session: AuthSession) => void;
}) {
  const liffId = configuredLiffId();
  const [healthState, setHealthState] = useState<HealthState>("checking");
  const [signInError, setSignInError] = useState<Error | null>(null);
  const [connecting, setConnecting] = useState(Boolean(liffId));
  const [signingIn, setSigningIn] = useState(false);
  const resumed = useRef(false);

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

  useEffect(() => {
    // StrictMode runs effects twice; a second attempt would trip the relogin guard.
    if (!liffId || resumed.current) return;
    resumed.current = true;
    resumeLiffSession()
      .then((session) => {
        if (session) onSignedIn(session);
      })
      .catch((failure: unknown) => setSignInError(asError(failure)))
      .finally(() => setConnecting(false));
  }, [liffId, onSignedIn]);

  async function signIn() {
    setSigningIn(true);
    setSignInError(null);
    try {
      onSignedIn(await createLiffSession());
    } catch (failure) {
      setSignInError(asError(failure));
      setSigningIn(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-svh max-w-xl flex-col px-6 py-12">
      <p className="font-display text-2xl tracking-tight text-ink">
        AU<span className="text-accent">·</span>Van
      </p>
      <div className="flex flex-1 flex-col justify-center py-16">
        <h1 className="font-display text-5xl leading-tight tracking-tight text-ink">
          Van bookings
        </h1>
        <p className="mt-4 max-w-md text-lg leading-7 text-muted">
          Sign in with LINE to book a seat on the next van. Approved staff
          accounts land in the transport inventory instead.
        </p>
        <div className="mt-8 flex flex-col gap-4">
          {!liffId && (
            <p className="rounded-xl border border-line bg-card px-4 py-3 text-sm text-muted">
              Add your LIFF ID to <code>web/.env</code> before signing in.
            </p>
          )}
          {liffId && connecting && (
            <div className="flex flex-col gap-3" role="status">
              <Skeleton className="h-11 w-52 rounded-full" />
              <p className="text-sm text-muted">Connecting to LINE…</p>
            </div>
          )}
          {liffId && !connecting && (
            <Button
              className="self-start"
              disabled={signingIn}
              onClick={() => void signIn()}
            >
              {signingIn ? "Signing in…" : "Sign in with LINE"}
            </Button>
          )}
          {signInError && (
            <FailurePanel error={signInError} title="Sign-in did not finish" />
          )}
        </div>
        {/* Dropped from any build without VITE_E2E_AUTH=true; CI greps the bundle for it. */}
        {e2eAuthEnabled && <E2eSignIn onSignedIn={onSignedIn} />}
      </div>
      <p
        className="flex items-center gap-2 text-xs text-muted"
        aria-live="polite"
      >
        <span
          className={`h-2 w-2 rounded-full ${healthState === "available" ? "bg-success" : healthState === "unavailable" ? "bg-danger" : "bg-line"}`}
          aria-hidden="true"
        />
        <span className="font-semibold">API health</span>
        <span>
          {healthState === "checking" && "Checking local API…"}
          {healthState === "available" && "Available"}
          {healthState === "unavailable" &&
            "Unavailable — start the API on port 8080"}
        </span>
      </p>
    </main>
  );
}
