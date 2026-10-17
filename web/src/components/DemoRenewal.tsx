import { useEffect, useRef, useState } from "react";
import { Button } from "./ui/Button";
import { EmptyState } from "./ui/EmptyState";

export function DemoRenewal({
  renew,
  onLeave,
}: {
  renew: () => Promise<void>;
  onLeave: () => void;
}) {
  const [failed, setFailed] = useState(false);
  const started = useRef(false);

  useEffect(() => {
    // StrictMode runs effects twice; one renewal is enough.
    if (started.current) return;
    started.current = true;
    renew().catch(() => setFailed(true));
  }, [renew]);

  if (failed) {
    return (
      <main className="mx-auto max-w-xl px-5 py-16" role="alert">
        <EmptyState
          action={<Button onClick={onLeave}>Start the demo again</Button>}
          detail="The demo sign-in ran out and could not be renewed."
          title="Sign in again"
        />
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-xl px-5 py-16" role="status">
      <EmptyState
        detail="Renewing your demo sign-in…"
        title="Picking up where you left off"
      />
    </main>
  );
}
