import { useId, useState, type FormEvent } from "react";
import { useCancelTrip } from "../hooks/useInventoryQueries";
import { useToast } from "../hooks/useToast";
import type { AuthSession } from "../types/auth";
import type { Trip, VanRoute, Vehicle } from "../types/inventory";
import { bangkokDay, bangkokTime } from "../utils/dates";
import { ErrorMessage } from "./ErrorMessage";
import { TripImpact } from "./TripImpact";
import { Button } from "./ui/Button";
import { RouteLine } from "./ui/RouteLine";

const maxReason = 300;

export function CancelTrip({
  session,
  trip,
  route,
  van,
  onBack,
  onDone,
}: {
  session: AuthSession;
  trip: Trip;
  route?: VanRoute;
  van?: Vehicle;
  onBack: () => void;
  onDone: () => void;
}) {
  const cancel = useCancelTrip(session);
  const notify = useToast();
  const id = useId();
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const missing = submitted && reason.trim() === "";

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (reason.trim() === "") return;
    cancel.mutate(
      { id: trip.id, reason: reason.trim() },
      {
        onSuccess: () => {
          notify("Trip cancelled");
          onDone();
        },
      },
    );
  }

  return (
    <form className="grid gap-6" noValidate onSubmit={submit}>
      <div className="flex items-baseline gap-4 border-b border-dashed border-line pb-5">
        <span className="font-mono text-[28px] font-medium tracking-tight text-ink tabular-nums">
          {bangkokTime(trip.departureAt)}
        </span>
        <span className="min-w-0">
          <span className="block text-sm text-muted">{`${bangkokDay(trip.departureAt)} · ${van?.code ?? "No van"}`}</span>
          {route && (
            <span className="mt-0.5 block font-medium text-ink">
              <RouteLine destination={route.destination} origin={route.origin} />
            </span>
          )}
        </span>
      </div>
      <TripImpact change="cancel" session={session} tripId={trip.id} />
      <div className="flex flex-col gap-1.5">
        <label className="text-[13px] font-medium text-ink" htmlFor={id}>
          Reason passengers will read
        </label>
        <textarea
          aria-describedby={`${id}-hint`}
          aria-invalid={missing || undefined}
          className="w-full rounded-lg border border-line bg-card px-3 py-2 text-[15px] text-ink transition-colors duration-150 ease-out hover:border-ink/25 focus:border-brand-500 aria-invalid:border-danger"
          id={id}
          maxLength={maxReason}
          onChange={(event) => setReason(event.target.value)}
          placeholder="The van failed its safety check this morning."
          rows={3}
          value={reason}
        />
        <p className={`flex justify-between gap-3 text-xs ${missing ? "text-danger" : "text-muted"}`} id={`${id}-hint`}>
          <span>{missing ? "Say why, so passengers are not left guessing." : "Sent on LINE with the cancellation."}</span>
          <span className="font-mono tabular-nums">{`${reason.length}/${maxReason}`}</span>
        </p>
      </div>
      <ErrorMessage error={cancel.error} />
      <div className="flex flex-wrap justify-end gap-2 border-t border-line pt-5">
        <Button onClick={onBack} type="button" variant="secondary">
          Keep trip
        </Button>
        <Button disabled={cancel.isPending} type="submit" variant="danger">
          {cancel.isPending ? "Cancelling…" : "Cancel trip"}
        </Button>
      </div>
    </form>
  );
}
