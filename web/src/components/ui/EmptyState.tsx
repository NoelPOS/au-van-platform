import type { ReactNode } from "react";
import { RouteRule } from "./RouteLine";

export function EmptyState({
  title,
  detail,
  action,
}: {
  title: string;
  detail?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      <RouteRule className="w-16" />
      <p className="mt-5 font-display text-2xl font-light text-brand-900 italic">
        {title}
      </p>
      {detail && <p className="mt-2 max-w-sm text-sm text-muted">{detail}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
