const departures = [
  { time: "07:30", destination: "BTS Udom Suk", seats: "4 left" },
  { time: "10:00", destination: "Mega Bangna", seats: "9 left" },
  { time: "13:15", destination: "Siam Paragon", seats: "Full" },
  { time: "17:45", destination: "Future Park", seats: "2 left" },
];

export function SampleDepartures() {
  return (
    <section aria-label="Sample departures" className="bg-brand-900 text-paper">
      <div className="mx-auto max-w-6xl px-5 py-6 sm:px-8">
        <p className="font-mono text-[10px] tracking-[0.2em] text-accent uppercase">
          Departures · from Assumption University
        </p>
        <ol className="mt-4 grid grid-cols-1 divide-y divide-dashed divide-white/15 sm:grid-cols-2 sm:divide-y-0 lg:grid-cols-4">
          {departures.map((departure) => (
            <li
              className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-baseline gap-x-4 py-3 sm:py-2 lg:grid-cols-[auto_minmax(0,1fr)] lg:gap-y-1 lg:border-l lg:border-dashed lg:border-white/15 lg:px-6 lg:first:border-l-0 lg:first:pl-0"
              key={departure.time}
            >
              <span className="font-mono text-2xl font-medium tracking-tight tabular-nums lg:row-span-2">
                {departure.time}
              </span>
              <span className="truncate text-[15px] text-brand-100">
                {departure.destination}
              </span>
              <span
                className={`font-mono text-xs tracking-[0.12em] uppercase ${departure.seats === "Full" ? "text-brand-100/60" : "text-accent"}`}
              >
                {departure.seats}
              </span>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
