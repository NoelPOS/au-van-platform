import { useState, type ReactNode } from "react";
import { RouteRule } from "./ui/RouteLine";

const fits = { cover: "object-cover", contain: "object-contain" };

export function ShotImage({
  src,
  alt,
  note = "Screenshot to come",
  fit = "cover",
}: {
  src: string;
  alt: string;
  note?: string;
  fit?: keyof typeof fits;
}) {
  const [missing, setMissing] = useState(false);

  if (missing) {
    return (
      <div className="flex size-full flex-col items-center justify-center gap-4 bg-paper bg-[repeating-linear-gradient(0deg,transparent_0_27px,var(--color-line)_27px_28px)] px-6 text-center">
        <RouteRule className="w-12" />
        <p className="font-display text-lg text-brand-900 italic">{alt}</p>
        <p className="font-mono text-[10px] tracking-[0.18em] text-muted uppercase">
          {note}
        </p>
      </div>
    );
  }

  return (
    <img
      alt={alt}
      className={`size-full object-top ${fits[fit]}`}
      decoding="async"
      loading="lazy"
      onError={() => setMissing(true)}
      src={src}
    />
  );
}

export function PhoneFrame({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-[2.6rem] bg-ink p-2 shadow-[0_30px_60px_-36px_rgba(20,23,43,0.6)]">
      <div className="relative aspect-[9/19.5] overflow-hidden rounded-[2.1rem] bg-paper">
        <span
          aria-hidden
          className="absolute top-2.5 left-1/2 z-10 h-5 w-20 -translate-x-1/2 rounded-full bg-ink"
        />
        <div className="absolute inset-x-0 top-9 bottom-0">{children}</div>
      </div>
    </div>
  );
}

export function BrowserFrame({
  address,
  aspect = "aspect-[16/10]",
  children,
}: {
  address: string;
  aspect?: string;
  children: ReactNode;
}) {
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-card shadow-[0_30px_60px_-40px_rgba(20,23,43,0.45)]">
      <div className="flex h-10 items-center border-b border-line px-4">
        <span className="truncate rounded-md bg-paper px-3 py-1 font-mono text-[11px] text-muted">
          {address}
        </span>
      </div>
      <div className={aspect}>{children}</div>
    </div>
  );
}
