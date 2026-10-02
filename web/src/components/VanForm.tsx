import { useState, type FormEvent } from "react";
import {
  useCreateVehicle,
  useUpdateVehicle,
} from "../hooks/useInventoryQueries";
import { useToast } from "../hooks/useToast";
import type { AuthSession } from "../types/auth";
import type { SeatLayout, Vehicle, VehicleStatus } from "../types/inventory";
import { ErrorMessage, FormActions } from "./FormCard";
import { SeatLayoutPicker } from "./SeatLayoutPicker";
import { EmptyState } from "./ui/EmptyState";
import { Input } from "./ui/Input";
import { SegmentedControl } from "./ui/SegmentedControl";
import { TextLink } from "./ui/TextLink";
import { statusLabel } from "../utils/statusLabels";

const statuses = (["ACTIVE", "INACTIVE"] satisfies VehicleStatus[]).map(
  (value) => ({ value, label: statusLabel(value) }),
);

export function VanForm({
  session,
  van,
  layouts,
  onDone,
}: {
  session: AuthSession;
  van: Vehicle | null;
  layouts: SeatLayout[];
  onDone: () => void;
}) {
  const createVan = useCreateVehicle(session);
  const updateVan = useUpdateVehicle(session);
  const notify = useToast();
  const [status, setStatus] = useState<VehicleStatus>(van?.status ?? "ACTIVE");
  const retiring = van?.status === "ACTIVE" && status === "INACTIVE";

  if (!van && layouts.length === 0) {
    return (
      <EmptyState
        action={
          <TextLink to="/admin/seat-layouts">Create a seat layout</TextLink>
        }
        detail="A van takes its seats from a layout, so make one first."
        title="No seat layouts yet"
      />
    );
  }

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
      code: String(form.get("code")).trim(),
      name: String(form.get("name")).trim(),
      seatLayoutId: String(form.get("seatLayoutId")),
    };
    if (van)
      updateVan.mutate(
        { id: van.id, input: { ...input, status } },
        { onSuccess: saved("Van updated") },
      );
    else createVan.mutate(input, { onSuccess: saved("Van added") });
  }

  return (
    <form className="grid gap-5" onSubmit={submit}>
      <Input
        defaultValue={van?.code}
        label="Van code"
        name="code"
        placeholder="VAN-01"
        required
      />
      <Input
        defaultValue={van?.name}
        label="Name"
        name="name"
        placeholder="White Toyota Hiace"
        required
      />
      <SeatLayoutPicker
        defaultValue={van?.seatLayoutId ?? layouts[0].id}
        layouts={layouts}
      />
      {van && (
        <SegmentedControl
          label="Status"
          onChange={setStatus}
          options={statuses}
          value={status}
        />
      )}
      <ErrorMessage error={createVan.error ?? updateVan.error} />
      <FormActions
        busy={createVan.isPending || updateVan.isPending}
        confirm={
          retiring
            ? "Mark this van as inactive? You can make it active again later."
            : undefined
        }
        onCancel={onDone}
        submitLabel={van ? "Save van" : "Add van"}
      />
    </form>
  );
}
