import { AbsoluteFill, useCurrentFrame } from "remotion";
import { SplitFlap } from "../components/SplitFlap";
import { rise } from "../motion";
import { color, font } from "../theme";

const tile = 60;
const countdownStart = 50;

const rows = [
  { time: "07:30", place: "MEGA BANGNA", seats: 4, every: 14 },
  { time: "09:00", place: "SIAM PARAGON", seats: 7, every: 10 },
  { time: "13:15", place: "BTS UDOM SUK", seats: 2, every: 22 },
  { time: "17:45", place: "FUTURE PARK", seats: 5, every: 15 },
];

function seatsAt(row: (typeof rows)[number], frame: number, appear: number) {
  const gone = Math.max(0, Math.floor((frame - countdownStart) / row.every));
  const left = Math.max(0, row.seats - gone);
  const changedAt = left === row.seats ? appear : countdownStart + (row.seats - left) * row.every;
  return { left, changedAt };
}

const label = {
  fontFamily: font.mono,
  fontSize: 18,
  letterSpacing: "0.18em",
  textTransform: "uppercase" as const,
};

const columns = {
  display: "grid",
  gridTemplateColumns: `${5 * tile * 0.9}px ${12 * tile * 0.9}px ${4 * tile * 0.9}px`,
  columnGap: 64,
  alignItems: "center",
};

export function Board() {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{ background: color.navy, alignItems: "center", paddingTop: 140 }}>
      <div style={{ width: 1262 }}>
        <div
          style={{
            ...rise(frame, 4),
            display: "flex",
            justifyContent: "space-between",
            alignItems: "baseline",
          }}
        >
          <span style={{ ...label, color: color.amber }}>
            Departures · from Assumption University
          </span>
          <span style={{ fontFamily: font.mono, fontSize: 30, color: color.paper }}>07:12</span>
        </div>
        <div
          style={{
            ...columns,
            ...label,
            fontSize: 15,
            color: "rgba(227,231,248,0.6)",
            marginTop: 44,
            paddingBottom: 14,
            borderBottom: "1px dashed rgba(255,255,255,0.18)",
          }}
        >
          <span>Time</span>
          <span>Destination</span>
          <span>Seats</span>
        </div>
        {rows.map((row, index) => {
          const appear = 12 + index * 8;
          const { left, changedAt } = seatsAt(row, frame, appear);
          const full = left === 0;
          return (
            <div
              key={row.time}
              style={{
                ...columns,
                padding: "20px 0",
                borderBottom: "1px dashed rgba(255,255,255,0.18)",
              }}
            >
              <span style={{ opacity: full ? 0.5 : 1 }}>
                <SplitFlap from={appear} size={tile} text={row.time} />
              </span>
              <span style={{ opacity: full ? 0.5 : 1 }}>
                <SplitFlap from={appear + 4} size={tile} text={row.place.padEnd(12)} />
              </span>
              <SplitFlap
                from={changedAt}
                size={tile}
                text={full ? "FULL" : String(left).padStart(2).padEnd(4)}
                tone={full ? color.paper : color.amber}
              />
            </div>
          );
        })}
        <p
          style={{
            margin: "56px 0 0",
            fontFamily: font.display,
            fontSize: 60,
            lineHeight: 1.12,
            letterSpacing: "-0.02em",
            color: color.paper,
          }}
        >
          <span style={{ display: "block", ...rise(frame, 62) }}>Seats go in minutes.</span>
          <em style={{ display: "block", color: color.amber, ...rise(frame, 96) }}>
            Two students tap the same seat.
          </em>
        </p>
      </div>
    </AbsoluteFill>
  );
}
