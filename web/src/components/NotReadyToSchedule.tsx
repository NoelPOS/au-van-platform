import { EmptyState } from "./ui/EmptyState";
import { TextLink } from "./ui/TextLink";

export function NotReadyToSchedule({ hasRoutes }: { hasRoutes: boolean }) {
  return (
    <EmptyState
      action={
        <TextLink to={hasRoutes ? "/admin/vans" : "/admin/routes"}>
          {hasRoutes ? "Add a van" : "Create a route"}
        </TextLink>
      }
      detail="A trip needs a route to run and a van to run it."
      title="Not quite ready to schedule"
    />
  );
}
