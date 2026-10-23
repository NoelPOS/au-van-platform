import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import type { RouteIdentity } from "../../utils/routeIdentity";

export function RouteDot({
  route,
  className = "",
}: {
  route: RouteIdentity;
  className?: string;
}) {
  return (
    <span
      aria-hidden
      className={`size-2 shrink-0 rounded-full ${route.colour.dot} ${className}`}
    />
  );
}

export function RouteName({ route }: { route: RouteIdentity }) {
  const Arrow = route.direction === "back" ? ArrowDownLeft : ArrowUpRight;
  return (
    <span className="flex min-w-0 items-center gap-0.5" title={route.full}>
      {route.direction && (
        <span className="sr-only">
          {route.direction === "out" ? "to " : "from "}
        </span>
      )}
      <span className="truncate">{route.short}</span>
      {route.direction && (
        <Arrow aria-hidden className="size-3 shrink-0" strokeWidth={2.25} />
      )}
    </span>
  );
}
