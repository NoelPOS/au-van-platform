import { useEffect, useState } from "react";
import { apiBaseUrl } from "./api-base-url";
import { createLiffSession } from "./auth/liff-session";
import { configuredLiffId } from "./auth/liff-config";
import type { AuthSession } from "./auth/session";
import { Button } from "./components/ui/Button";
import { AdminInventoryPage } from "./inventory/AdminInventoryPage";

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
    return <AdminInventoryPage session={session} />;
  }

  return (
    <main className="mx-auto max-w-2xl px-6 py-28">
      <p className="text-xs font-bold uppercase tracking-widest text-brand">
        AU-Van platform
      </p>
      <h1 className="my-4 text-5xl font-bold tracking-tight text-ink">
        Admin portal
      </h1>
      <p className="max-w-xl text-lg leading-7 text-muted">
        Sign in with your approved LINE account to manage routes, vans, seat
        layouts, and scheduled trips.
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
      {signInError && (
        <p
          className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700"
          role="alert"
        >
          {signInError}
        </p>
      )}
      {session?.user.role === "STUDENT" && (
        <p className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
          Your account is signed in but does not have administrator access.
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
