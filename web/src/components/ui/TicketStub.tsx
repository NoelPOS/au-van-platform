import type { ReactNode } from "react";

const notch =
  "absolute left-0 size-4 -translate-x-1/2 rounded-full border border-line bg-paper";

export function TicketStub({
  stub,
  children,
  className = "",
}: {
  stub: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <article
      className={`flex overflow-hidden rounded-2xl border border-line bg-card ${className}`}
    >
      <div className="flex w-24 shrink-0 flex-col justify-center px-3.5 py-5 sm:w-32 sm:px-5">
        {stub}
      </div>
      <div className="relative min-w-0 flex-1 border-l border-dashed border-line px-4 py-5 sm:px-5">
        <span aria-hidden className={`${notch} -top-2`} />
        <span aria-hidden className={`${notch} -bottom-2`} />
        {children}
      </div>
    </article>
  );
}
