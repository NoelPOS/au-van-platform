import { useEffect, useRef, useState } from "react";

function remainingMs(expiresAt: string): number {
  return Math.max(0, Date.parse(expiresAt) - Date.now());
}

function format(milliseconds: number): string {
  const totalSeconds = Math.ceil(milliseconds / 1000);
  const seconds = totalSeconds % 60;
  return `${Math.floor(totalSeconds / 60)}:${String(seconds).padStart(2, "0")}`;
}

/**
 * Counts down to the server's expiry instant. Every tick recomputes from
 * `expiresAt` instead of decrementing, so a throttled timer in a backgrounded
 * LINE browser costs smoothness rather than accuracy. Give it the expiry as a
 * `key`: a fresh hold is a fresh countdown.
 */
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
