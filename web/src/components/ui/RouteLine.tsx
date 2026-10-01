export function RouteRule({ className = "w-8" }: { className?: string }) {
  return (
    <span aria-hidden className="flex shrink-0 items-center">
      <span className="size-1.5 rounded-full bg-brand-500" />
      <span
        className={`border-t border-dashed border-brand-500/60 ${className}`}
      />
      <span className="size-1.5 rounded-full border border-brand-500" />
    </span>
  );
}

export function RouteLine({
  origin,
  destination,
}: {
  origin: string;
  destination: string;
}) {
  return (
    <span className="inline-flex flex-wrap items-center gap-x-2.5 gap-y-1">
      {origin}{" "}
      <RouteRule />
      <span className="sr-only">→</span>{" "}
      {destination}
    </span>
  );
}
