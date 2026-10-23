import type { ReactNode } from "react";
import type { AuthSession } from "../types/auth";
import type { Trip } from "../types/inventory";
import { TripPassengers } from "./TripPassengers";
import { SegmentedControl } from "./ui/SegmentedControl";

export type TripTab = "passengers" | "edit";

export function TripDetail({
  session,
  trip,
  tab = "passengers",
  onTab,
  children,
}: {
  session: AuthSession;
  trip: Trip | null;
  tab?: TripTab;
  onTab: (tab: TripTab) => void;
  children: ReactNode;
}) {
  if (!trip) return children;

  const tabs: { value: TripTab; label: string }[] = [
    { value: "passengers", label: "Passengers" },
    { value: "edit", label: trip.status === "CANCELLED" ? "Details" : "Edit" },
  ];

  return (
    <div className="grid gap-6">
      <SegmentedControl
        hideLabel
        label="Trip view"
        onChange={onTab}
        options={tabs}
        value={tab}
      />
      {tab === "passengers" ? (
        <TripPassengers session={session} tripId={trip.id} />
      ) : (
        children
      )}
    </div>
  );
}
