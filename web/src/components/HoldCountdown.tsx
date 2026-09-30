import { useEffect, useRef, useState } from "react";

function remainingMs(expiresAt: string): number {
  return Math.max(0, Date.parse(expiresAt) - Date.now());
}

function format(milliseconds: number): string {
  const totalSeconds = Math.ceil(milliseconds / 1000);
  const seconds = totalSeconds % 60;
  return `${Math.floor(totalSeconds / 60)}:${String(seconds).padStart(2, "0")}`;
}

// Recomputed from expiresAt on every tick because a backgrounded LINE browser
// throttles timers; callers key it on expiresAt so a new hold restarts it.
export function HoldCountdown({
  expiresAt,
  onExpire,
}: {
  expiresAt: string;
  onExpire: () => void;
}) {
  const [remaining, setRemaining] = useState(() => remainingMs(expiresAt));
  const expire = useRef(onExpire);

  useEffect(() => {
    expire.current = onExpire;
  }, [onExpire]);

  useEffect(() => {
    const interval = setInterval(() => {
      const next = remainingMs(expiresAt);
      setRemaining(next);
      if (next === 0) {
        clearInterval(interval);
        expire.current();
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);

  return (
    <p
      aria-live="off"
      className="rounded-lg bg-brand-soft px-3 py-2 text-sm font-semibold text-brand"
      role="timer"
    >
      Seats held for {format(remaining)}
    </p>
  );
}
