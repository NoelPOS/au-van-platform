import {
  matchesFilter,
  type RouteDepartures,
  type RouteFilter,
} from "../utils/tripRoutes";
import { Select } from "./ui/Select";

function placeOptions(places: string[]) {
  const distinct = [...new Set(places)].sort((left, right) =>
    left.localeCompare(right),
  );
  return [
    { value: "", label: "Any" },
    ...distinct.map((place) => ({ value: place, label: place })),
  ];
}

export function RouteFilters({
  routes,
  filter,
  onChange,
}: {
  routes: RouteDepartures[];
  filter: RouteFilter;
  onChange: (filter: RouteFilter) => void;
}) {
  const origins = routes
    .filter((route) => matchesFilter(route, { ...filter, from: "" }))
    .map((route) => route.origin);
  const destinations = routes
    .filter((route) => matchesFilter(route, { ...filter, to: "" }))
    .map((route) => route.destination);

  return (
    <div className="grid grid-cols-2 gap-3">
      <Select
        label="From"
        onChange={(from) => onChange({ ...filter, from })}
        options={placeOptions(origins)}
        value={filter.from}
      />
      <Select
        label="To"
        onChange={(to) => onChange({ ...filter, to })}
        options={placeOptions(destinations)}
        value={filter.to}
      />
    </div>
  );
}
