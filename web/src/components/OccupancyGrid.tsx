export function OccupancyGrid({ claimed, total }: { claimed: number; total: number }) {
  return (
    <div>
      <div
        aria-hidden="true"
        className="grid grid-cols-[repeat(auto-fill,minmax(1.75rem,1fr))] gap-1.5"
      >
        {Array.from({ length: total }, (_, index) => (
          <span
            className={`aspect-square rounded-md rounded-t-xl ${
              index < claimed ? "bg-brand-600" : "border border-line bg-paper"
            }`}
            key={index}
          />
        ))}
      </div>
      <p className="mt-3 text-sm text-ink">{`${claimed} of ${total} seats claimed`}</p>
      <ul className="mt-2 flex gap-4 text-xs text-muted">
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" className="size-3 rounded-sm bg-brand-600" />
          Claimed
        </li>
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" className="size-3 rounded-sm border border-line bg-paper" />
          Open
        </li>
      </ul>
    </div>
  );
}
