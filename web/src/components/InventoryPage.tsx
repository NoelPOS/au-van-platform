import type { ReactNode } from "react";
import { Button } from "./ui/Button";
import { PageHeader } from "./ui/PageHeader";
import { Panel } from "./ui/Panel";
import { Skeleton } from "./ui/Skeleton";

type Loadable = {
  error: Error | null;
  isPending: boolean;
  refetch: () => unknown;
};

export function InventoryPage({
  title,
  description,
  action,
  queries,
  children,
}: {
  title: string;
  description: string;
  action?: ReactNode;
  queries: Loadable[];
  children: ReactNode;
}) {
  const failure = queries.find((query) => query.error)?.error;
  const loading = queries.some((query) => query.isPending);

  return (
    <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8 lg:px-12 lg:py-14">
      <PageHeader
        action={failure || loading ? undefined : action}
        description={description}
        eyebrow="Inventory"
        title={title}
      />
      <div className="mt-8">
        {failure ? (
          <Panel className="max-w-xl p-6">
            <h2 className="font-display text-2xl font-light text-brand-900">
              Could not load {title.toLowerCase()}
            </h2>
            <p className="mt-2 mb-5 text-muted" role="alert">
              {failure.message}
            </p>
            <Button onClick={() => queries.forEach((query) => void query.refetch())}>
              Try again
            </Button>
          </Panel>
        ) : loading ? (
          <div className="flex flex-col gap-3" role="status">
            <span className="sr-only">Loading {title.toLowerCase()}…</span>
            <Skeleton className="h-12" />
            <Skeleton className="h-16" />
            <Skeleton className="h-16" />
          </div>
        ) : (
          children
        )}
      </div>
    </main>
  );
}
