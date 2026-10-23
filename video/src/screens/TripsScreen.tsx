import { useCurrentFrame } from "remotion";
import { progress } from "../motion";
import { color, font } from "../theme";
import { AppScreen, Chevron, kicker, TapRipple } from "./AppChrome";

const tripTapAt = 62;

const days = [
  ["Tomorrow", "24", 4],
  ["Sun", "25", 3],
  ["Mon", "26", 4],
  ["Tue", "27", 3],
] as const;

const trips = [
  { time: "07:00", arrives: "07:55", left: -1 },
  { time: "09:00", arrives: "09:55", left: 3 },
  { time: "12:00", arrives: "12:55", left: 8 },
  { time: "17:30", arrives: "18:25", left: 0 },
];

const tapped = "09:00";
const columns = { display: "grid", gridTemplateColumns: "70px 1fr auto", alignItems: "center", gap: 12 };
const badge = { ...kicker, padding: "3px 8px", border: `1px solid ${color.line}`, borderRadius: 6, fontSize: 11 };

function Seats({ left }: { left: number }) {
  if (left < 0) return <span style={badge}>Closed</span>;
  if (left === 0) return <span style={badge}>Full</span>;
  return (
    <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
      <span style={{ textAlign: "right" }}>
        <span style={{ display: "block", fontFamily: font.mono, fontSize: 19, fontWeight: 500, color: left <= 3 ? color.warning : color.ink }}>
          {left}
        </span>
        <span style={{ display: "block", fontSize: 12, color: color.muted }}>left</span>
      </span>
      <Chevron />
    </span>
  );
}

function Waitlist() {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 10,
        marginTop: 10,
        padding: "9px 12px",
        borderRadius: 12,
        background: color.paper,
        fontSize: 13,
        color: color.muted,
      }}
    >
      We message you on LINE if a seat opens up.
      <span style={{ flexShrink: 0, padding: "7px 12px", borderRadius: 10, border: `1px solid ${color.line}`, background: color.card, fontWeight: 600, color: color.ink }}>
        Join waitlist
      </span>
    </div>
  );
}

function DayStrip() {
  return (
    <div style={{ display: "flex", gap: 9, marginTop: 16 }}>
      {days.map(([name, day, count], index) => {
        const active = index === 0;
        return (
          <span
            key={day}
            style={{
              width: 76,
              padding: "9px 0 8px",
              borderRadius: 18,
              textAlign: "center",
              border: `1px solid ${active ? color.navy : color.line}`,
              background: active ? color.navy : color.card,
              color: active ? color.paper : color.ink,
            }}
          >
            <span style={{ ...kicker, display: "block", fontSize: 10, color: active ? "rgba(246,243,236,0.7)" : color.muted }}>
              {name}
            </span>
            <span style={{ display: "block", marginTop: 4, fontFamily: font.display, fontSize: 28, lineHeight: 1 }}>{day}</span>
            <span style={{ display: "block", fontSize: 12, opacity: 0.7 }}>Oct</span>
            <span style={{ display: "flex", justifyContent: "center", gap: 2, marginTop: 5 }}>
              {Array.from({ length: count }, (_, dot) => (
                <span
                  key={dot}
                  style={{ width: 4, height: 4, borderRadius: 2, background: active ? color.amber : "rgba(63,83,201,0.5)" }}
                />
              ))}
            </span>
          </span>
        );
      })}
    </div>
  );
}

export function TripsScreen() {
  const tap = progress(useCurrentFrame(), tripTapAt, 16);
  return (
    <AppScreen>
      <p style={kicker}>Departures</p>
      <p style={{ margin: "6px 0 0", fontFamily: font.display, fontSize: 38, color: color.navy }}>
        Catch the next van
      </p>
      <p style={{ margin: "4px 0 0", fontFamily: font.display, fontStyle: "italic", fontSize: 19, color: color.muted }}>
        Saturday 24 October
      </p>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 18, fontSize: 15 }}>
        <span>
          <span style={{ display: "block", fontWeight: 600 }}>To Siam Paragon</span>
          <span style={{ display: "block", color: color.muted }}>from Assumption University · ฿90</span>
        </span>
        <span style={{ fontWeight: 500, color: color.brand }}>← Change route</span>
      </div>
      <DayStrip />
      <div style={{ marginTop: 16, overflow: "hidden", borderRadius: 20, border: `1px solid ${color.line}`, background: color.card }}>
        <div style={{ ...columns, ...kicker, padding: "10px 18px", borderBottom: `1px solid ${color.line}`, fontSize: 10 }}>
          <span>Time</span>
          <span>Ride</span>
          <span>Seats</span>
        </div>
        {trips.map((trip, index) => (
          <div
            key={trip.time}
            style={{
              position: "relative",
              padding: "15px 18px",
              borderTop: index ? `1px dashed ${color.line}` : "none",
              background: trip.time === tapped && tap > 0 ? `rgba(227,231,248,${0.7 * tap})` : "transparent",
            }}
          >
            <div style={columns}>
              <span style={{ fontFamily: font.mono, fontSize: 22, fontWeight: 500, color: trip.left > 0 ? color.ink : color.muted }}>
                {trip.time}
              </span>
              <span style={{ opacity: trip.left < 0 ? 0.7 : 1 }}>
                <span style={{ display: "block", fontSize: 16, fontWeight: 500 }}>{`Arrives ${trip.arrives}`}</span>
                <span style={{ display: "block", marginTop: 2, fontFamily: font.mono, fontSize: 12, color: color.muted }}>
                  55 min ride
                </span>
              </span>
              <Seats left={trip.left} />
            </div>
            {trip.left === 0 && <Waitlist />}
            {trip.time === tapped && <TapRipple at={tripTapAt} left={180} top="50%" />}
          </div>
        ))}
      </div>
    </AppScreen>
  );
}
