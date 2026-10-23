import { useSearchParams } from "react-router";

export function useRouteFilter() {
  const [params, setParams] = useSearchParams();
  const chosen = params.get("routes")?.split(",").filter(Boolean) ?? [];

  function choose(routeIds: string[]) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (routeIds.length > 0) next.set("routes", routeIds.join(","));
        else next.delete("routes");
        return next;
      },
      { replace: true },
    );
  }

  return {
    chosen,
    choose,
    shows: (routeId: string) =>
      chosen.length === 0 || chosen.includes(routeId),
  };
}
