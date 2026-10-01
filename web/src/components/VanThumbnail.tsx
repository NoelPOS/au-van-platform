import type { Seat } from "../types/inventory";
import { fromSeats, seatAt } from "../utils/seatLayout";
import { VanSeatPlan } from "./VanSeatPlan";

export function VanThumbnail({ seats }: { seats: Seat[] }) {
  const { rows, columns } = fromSeats(seats);
  return (
    <VanSeatPlan
      cellSize={9}
      columns={columns}
      renderCell={(rowNumber, columnNumber) =>
        seatAt(seats, rowNumber, columnNumber) && (
          <span className="block h-full w-full rounded-[3px] bg-brand-600" />
        )
      }
      rows={rows}
    />
  );
}
