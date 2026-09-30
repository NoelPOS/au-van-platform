import { useEffect, useState } from "react";
import { apiBaseUrl } from "./services/apiBaseUrl";
import { createLiffSession } from "./services/liffService";
import { E2eSignIn } from "./components/E2eSignIn";
import { configuredLiffId, e2eAuthEnabled } from "./services/liffConfig";
import type { AuthSession } from "./types/auth";
import { Button } from "./components/ui/Button";
import { StudentBookingPage } from "./pages/StudentBookingPage";
import { AdminPage } from "./components/AdminLayout";

type HealthState = "checking" | "available" | "unavailable";

function App() {
  const [healthState, setHealthState] = useState<HealthState>("checking");
  const [session, setSession] = useState<AuthSession | null>(null);
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
      const nextSession = await createLiffSession();
      setSession(nextSession);
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

  if (session?.user.role === "ADMIN") {
    return <AdminPage session={session} />;
  }

  if (session?.user.role === "STUDENT") {
    return <StudentBookingPage session={session} />;
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
      {!session &&
        (!configuredLiffId() ? (
          <p className="mt-6 rounded-xl bg-stone-100 px-4 py-3 text-sm text-muted">
            Add your LIFF ID to <code>web/.env</code> before signing in.
          </p>
        ) : (
          <div className="mt-6">
            <Button disabled={signingIn} onClick={() => void signIn()}>
              {signingIn ? "Signing in…" : "Sign in with LINE"}
            </Button>
          </div>
        ))}
      {/*
        Present only in a build made with VITE_E2E_AUTH=true, which is the
        Playwright stack's web image and nothing else (ADR-013). The flag is a
        build-time constant, so this whole subtree — and the module it comes
        from — is folded away in any other build, and `Container checks` greps
        the served bundle for its marker to prove it.
      */}
      {!session && e2eAuthEnabled && <E2eSignIn onSignedIn={setSession} />}
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

export default App;
