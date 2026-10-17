import type { ReactNode } from "react";

export function LandingSection({
  id,
  kicker,
  title,
  intro,
  dark = false,
  children,
}: {
  id: string;
  kicker: string;
  title: string;
  intro?: string;
  dark?: boolean;
  children: ReactNode;
}) {
  return (
    <section
      aria-labelledby={`${id}-title`}
      className={dark ? "bg-brand-900 text-paper" : "border-t border-line"}
      id={id}
    >
      <div className="mx-auto grid max-w-6xl gap-y-6 px-5 py-20 sm:px-8 lg:grid-cols-[12rem_minmax(0,1fr)] lg:gap-x-14 lg:py-28">
        <p
          className={`font-mono text-[11px] tracking-[0.2em] uppercase lg:pt-4 ${dark ? "text-accent" : "text-muted"}`}
        >
          {kicker}
        </p>
        <div className="min-w-0">
          <h2
            className={`max-w-2xl font-display text-[36px] leading-[1.06] tracking-tight sm:text-5xl ${dark ? "text-paper" : "text-brand-900"}`}
            id={`${id}-title`}
          >
            {title}
          </h2>
          {intro && (
            <p
              className={`mt-5 max-w-xl text-lg leading-8 ${dark ? "text-brand-100/80" : "text-muted"}`}
            >
              {intro}
            </p>
          )}
          <div className="mt-12">{children}</div>
        </div>
      </div>
    </section>
  );
}
