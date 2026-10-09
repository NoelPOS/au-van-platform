import type { FormEvent } from "react";
import { Button } from "./ui/Button";
import { Input } from "./ui/Input";
import { HoldCountdown } from "./HoldCountdown";
import { StickyActionBar } from "./StickyActionBar";
import { formatBaht } from "../utils/format";
import type { AvailableTrip, SeatHold } from "../types/booking";

export function PassengerDetailsSection({
  trip,
  hold,
  defaults,
  submitting,
  onSubmit,
  onEdit,
  onExpire,
  onChangeSeats,
}: {
  trip: AvailableTrip;
  hold: SeatHold;
  defaults: { name: string; phone: string };
  submitting: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onEdit: () => void;
  onExpire: () => void;
  onChangeSeats: () => void;
}) {
  const labels = hold.seats.map((seat) => seat.label).sort();

  return (
    <form className="flex flex-col gap-5" onInput={onEdit} onSubmit={onSubmit}>
      <div>
        <h2 className="font-display text-2xl tracking-tight text-brand-900">
          Passenger details
        </h2>
        <p className="mt-1 text-sm text-muted">
          {`${labels.length === 1 ? "Seat" : "Seats"} ${labels.join(", ")} · ${formatBaht(trip.fare * labels.length)}. Staff check this name when you board.`}
        </p>
      </div>
      <HoldCountdown expiresAt={hold.expiresAt} key={hold.expiresAt} onExpire={onExpire} />
      <Input
        autoComplete="name"
        defaultValue={defaults.name}
        label="Full name"
        maxLength={100}
        minLength={2}
        name="passengerName"
        placeholder="Somchai P."
        required
      />
      <Input
        autoComplete="tel"
        defaultValue={defaults.phone}
        inputMode="tel"
        label="Phone number"
        maxLength={15}
        minLength={9}
        name="passengerPhone"
        placeholder="0812345678"
        required
      />
      <StickyActionBar>
        <Button onClick={onChangeSeats} type="button" variant="secondary">
          Change seats
        </Button>
        <Button className="flex-1" disabled={submitting} type="submit">
          {submitting ? "Confirming…" : "Confirm booking"}
        </Button>
      </StickyActionBar>
    </form>
  );
}
