import { useLocation } from "react-router";

export function RouteSearch() {
  const routes = new URLSearchParams(useLocation().search).get("routes");
  return <p data-testid="route-search">{routes ?? "all"}</p>;
}
