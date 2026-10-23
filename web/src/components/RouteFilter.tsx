import type { RouteIdentity } from "../utils/routeIdentity";
import { RouteDot, RouteName } from "./ui/RouteMark";

const chip =
  "inline-flex min-h-9 items-center gap-2 rounded-full border px-3.5 text-[13px] font-medium whitespace-nowrap transition-colors duration-150 ease-out";

function chipTone(pressed: boolean, dimmed: boolean) {
  if (pressed) return "border-brand-500/50 bg-brand-50 text-brand-900";
  if (dimmed) return "border-line bg-card text-muted hover:text-ink";
  return "border-line bg-card text-ink hover:border-brand-500/40";
}

export function RouteFilter({
  routes,
  chosen,
  onChoose,
  className = "",
}: {
  routes: RouteIdentity[];
  chosen: string[];
  onChoose: (routeIds: string[]) => void;
  className?: string;
}) {
  const all = chosen.length === 0;
  if (routes.length < 2 && all) return null;

  function toggle(routeId: string) {
    onChoose(
      chosen.includes(routeId)
        ? chosen.filter((entry) => entry !== routeId)
        : [...chosen, routeId],
    );
  }

  return (
    <div
      aria-label="Routes"
      className={`flex flex-wrap gap-2 ${className}`}
      role="group"
    >
      <button
        aria-pressed={all}
        className={`${chip} ${chipTone(all, false)}`}
        onClick={() => onChoose([])}
        type="button"
      >
        All routes
      </button>
      {routes.map((route) => {
        const pressed = chosen.includes(route.routeId);
        return (
          <button
            aria-label={route.full}
            aria-pressed={pressed}
            className={`${chip} ${chipTone(pressed, !all)}`}
            key={route.routeId}
            onClick={() => toggle(route.routeId)}
            type="button"
          >
            <RouteDot
              className={!all && !pressed ? "opacity-40" : undefined}
              route={route}
            />
            <RouteName route={route} />
          </button>
        );
      })}
    </div>
  );
}
