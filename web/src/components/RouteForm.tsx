import { useState, type FormEvent } from "react";
import { useCreateRoute, useUpdateRoute } from "../hooks/useInventoryQueries";
import { useToast } from "../hooks/useToast";
import type { AuthSession } from "../types/auth";
import type { RouteStatus, VanRoute } from "../types/inventory";
import { ErrorMessage, FormActions } from "./FormCard";
import { Input } from "./ui/Input";
import { SegmentedControl } from "./ui/SegmentedControl";

const statuses: { value: RouteStatus; label: string }[] = [
  { value: "ACTIVE", label: "Active" },
  { value: "INACTIVE", label: "Inactive" },
];

export function RouteForm({
  session,
  route,
  onDone,
}: {
  session: AuthSession;
  route: VanRoute | null;
  onDone: () => void;
}) {
  const createRoute = useCreateRoute(session);
  const updateRoute = useUpdateRoute(session);
  const notify = useToast();
  const [status, setStatus] = useState<RouteStatus>(route?.status ?? "ACTIVE");
  const values = route ?? {
    origin: "",
    destination: "",
    fare: 0,
    durationMinutes: 30,
  };
  const retiring = route?.status === "ACTIVE" && status === "INACTIVE";

  function saved(message: string) {
    return () => {
      notify(message);
      onDone();
    };
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input = {
      origin: String(form.get("origin")).trim(),
      destination: String(form.get("destination")).trim(),
      fare: Number(form.get("fare")),
      durationMinutes: Number(form.get("durationMinutes")),
    };
    if (route)
      updateRoute.mutate(
        { id: route.id, input: { ...input, status } },
        { onSuccess: saved("Route updated") },
      );
    else createRoute.mutate(input, { onSuccess: saved("Route created") });
  }

  return (
    <form className="grid gap-5" onSubmit={submit}>
      <Input
        defaultValue={values.origin}
        label="Origin"
        name="origin"
        placeholder="Assumption University"
        required
      />
      <Input
        defaultValue={values.destination}
        label="Destination"
        name="destination"
        placeholder="Mega Bangna"
        required
      />
      <div className="grid grid-cols-2 gap-4">
        <Input
          defaultValue={values.fare}
          inputMode="decimal"
          label="Fare (THB)"
          min="0"
          name="fare"
          required
          step="0.01"
          type="number"
        />
        <Input
          defaultValue={values.durationMinutes}
          label="Duration (minutes)"
          min="1"
          name="durationMinutes"
          required
          type="number"
        />
      </div>
      {route && (
        <SegmentedControl
          label="Status"
          onChange={setStatus}
          options={statuses}
          value={status}
        />
      )}
      <ErrorMessage error={createRoute.error ?? updateRoute.error} />
      <FormActions
        busy={createRoute.isPending || updateRoute.isPending}
        confirm={
          retiring
            ? "Mark this route as inactive? You can make it active again later."
            : undefined
        }
        onCancel={onDone}
        submitLabel={route ? "Save route" : "Create route"}
      />
    </form>
  );
}
