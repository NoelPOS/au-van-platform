import { Skeleton } from "./ui/Skeleton";
import { TextLink } from "./ui/TextLink";

type ListQuery = {
  data?: unknown[];
  error: Error | null;
  isPending: boolean;
};

export function OverviewCounter({
  title,
  query,
  to,
  action,
  waiting,
  settled,
}: {
  title: string;
  query: ListQuery;
  to: string;
  action: string;
  waiting: string;
  settled: string;
}) {
  const count = query.data?.length ?? 0;
  return (
    <section
      aria-label={title}
      className="rounded-2xl border border-line bg-card px-6 pt-6 pb-3"
    >
      <h2 className="text-[11px] font-semibold tracking-[0.16em] text-muted uppercase">
        {title}
      </h2>
      {query.isPending ? (
        <Skeleton className="mt-4 h-14 w-20" />
      ) : query.error ? (
        <p className="mt-3 text-sm text-danger" role="alert">
          {query.error.message}
        </p>
      ) : (
        <>
          <p className="mt-2 flex items-center gap-3">
            <span className="font-display text-6xl leading-none font-light text-brand-900 tabular-nums">
              {count}
            </span>
            {count > 0 && (
              <span aria-hidden className="size-2.5 rounded-full bg-accent" />
            )}
          </p>
          <p className="mt-2 text-sm text-muted">
            {count > 0 ? waiting : settled}
          </p>
        </>
      )}
      <div className="mt-2">
        <TextLink to={to}>{action}</TextLink>
      </div>
    </section>
  );
}
