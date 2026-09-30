import type { FormEvent } from "react";
import { Button } from "./ui/Button";
import { Panel } from "./ui/Panel";
import { HoldCountdown } from "./HoldCountdown";
import { formatDeparture, formatFare } from "../utils/format";
import type { AvailableTrip, SeatHold } from "../types/booking";

const fieldClass =
  "mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 font-normal text-ink outline-none focus:border-brand";

export function PassengerDetailsSection({
  trip,
  hold,
  submitting,
  onSubmit,
  onEdit,
  onExpire,
  onChangeSeats,
}: {
  trip: AvailableTrip;
  hold: SeatHold;
  submitting: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onEdit: () => void;
  onExpire: () => void;
  onChangeSeats: () => void;
}) {
  const seatLabels = hold.seats.map((seat) => seat.label).join(", ");

  return (
    <Panel className="p-5">
      <h2 className="text-lg font-bold text-ink">Passenger details</h2>
      <p className="mt-1 text-sm text-muted">
        {trip.origin} → {trip.destination} · {formatDeparture(trip.departureAt)}
      </p>
      <p className="mt-1 text-sm text-muted">
        Seat {seatLabels} · {formatFare(trip.fare * hold.seats.length)}
      </p>
      <div className="mt-4">
        <HoldCountdown
          expiresAt={hold.expiresAt}
          key={hold.expiresAt}
          onExpire={onExpire}
        />
      </div>
      <form className="mt-4 flex flex-col gap-3" onInput={onEdit} onSubmit={onSubmit}>
        <label className="text-sm font-semibold text-ink">
          Full name
          <input
            className={fieldClass}
            maxLength={100}
            minLength={2}
            name="passengerName"
            placeholder="Somchai P."
            required
          />
        </label>
        <label className="text-sm font-semibold text-ink">
          Phone number
          <input
            className={fieldClass}
            maxLength={15}
            minLength={9}
            name="passengerPhone"
            placeholder="0812345678"
            required
          />
        </label>
        <div className="flex gap-2">
          <Button onClick={onChangeSeats} type="button" variant="secondary">
            Change seats
          </Button>
          <Button disabled={submitting} type="submit">
            {submitting ? "Confirming…" : "Confirm booking"}
          </Button>
        </div>
      </form>
    </Panel>
  );
}
