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
  const [total] = useState(() => Math.max(remaining, 1));
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
    <div className="rounded-2xl border border-accent/40 bg-warning-soft px-4 py-3">
      <p aria-live="off" className="text-sm text-ink" role="timer">
        Seats held for{" "}
        <span className="font-mono font-semibold tabular-nums">
          {format(remaining)}
        </span>
      </p>
      <div aria-hidden className="mt-2 h-1 overflow-hidden rounded-full bg-accent/20">
        <div
          className="h-full rounded-full bg-accent transition-[width] duration-1000 ease-linear"
          style={{ width: `${(remaining / total) * 100}%` }}
        />
      </div>
    </div>
  );
}
