type Tab = "routes" | "seatLayouts" | "vehicles" | "trips";

const tabs: { id: Tab; label: string; description: string }[] = [
  {
    id: "routes",
    label: "Routes",
    description: "Journeys, fares, and durations",
  },
  {
    id: "seatLayouts",
    label: "Seat layouts",
    description: "Reusable van seating plans",
  },
  {
    id: "vehicles",
    label: "Vehicles",
    description: "Physical vans and their layouts",
  },
  { id: "trips", label: "Trips", description: "Scheduled van departures" },
];

export type InventoryTab = Tab;

export function InventoryTabs({
  active,
  onChange,
}: {
  active: Tab;
  onChange: (tab: Tab) => void;
}) {
  return (
    <nav
      className="mb-6 grid gap-2 sm:grid-cols-2 lg:grid-cols-4"
      aria-label="Transport inventory sections"
    >
      {tabs.map((tab) => (
        <button
          key={tab.id}
          onClick={() => onChange(tab.id)}
          className={`rounded-xl border p-3 text-left transition-colors ${active === tab.id ? "border-brand bg-brand-soft text-brand" : "border-line bg-white text-muted hover:border-muted"}`}
        >
          <strong className="block text-sm">{tab.label}</strong>
          <span className="mt-1 block text-xs">{tab.description}</span>
        </button>
      ))}
    </nav>
  );
}
