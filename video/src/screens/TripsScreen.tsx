import { useCurrentFrame } from "remotion";
import { progress } from "../motion";
import { color, font } from "../theme";
import { AppScreen, kicker } from "./AppChrome";

const days = [
  ["Tomorrow", "18", "Oct"],
  ["Mon", "19", "Oct"],
  ["Tue", "20", "Oct"],
  ["Wed", "21", "Oct"],
];

const trips = [
  { time: "07:30", place: "Mega Bangna", detail: "40 min · ฿80", left: 0 },
  { time: "09:00", place: "Siam Paragon", detail: "55 min · ฿90", left: 3 },
  { time: "09:00", place: "Mega Bangna", detail: "40 min · ฿80", left: 7 },
  { time: "12:00", place: "BTS Udom Suk", detail: "30 min · ฿50", left: 6 },
  { time: "16:30", place: "Future Park Rangsit", detail: "60 min · ฿120", left: 10 },
];

export function TripsScreen() {
  const frame = useCurrentFrame();
  const tap = progress(frame, 58, 16);
  return (
    <AppScreen>
      <p style={kicker}>Departures</p>
      <p style={{ margin: "6px 0 0", fontFamily: font.display, fontSize: 38, color: color.navy }}>
        Catch the next van
      </p>
      <p style={{ margin: "4px 0 0", fontFamily: font.display, fontStyle: "italic", fontSize: 19, color: color.muted }}>
        Sunday 18 October
      </p>
      <div style={{ display: "flex", gap: 9, marginTop: 20 }}>
        {days.map(([name, day, month], index) => (
          <span
            key={day}
            style={{
              width: 78,
              padding: "10px 0 9px",
              borderRadius: 18,
              textAlign: "center",
              border: `1px solid ${index === 0 ? color.navy : color.line}`,
              background: index === 0 ? color.navy : color.card,
              color: index === 0 ? color.paper : color.ink,
            }}
          >
            <span style={{ ...kicker, display: "block", fontSize: 11, color: index === 0 ? "rgba(246,243,236,0.7)" : color.muted }}>
              {name}
            </span>
            <span style={{ display: "block", marginTop: 4, fontFamily: font.display, fontSize: 30, lineHeight: 1 }}>{day}</span>
            <span style={{ display: "block", fontSize: 12, opacity: 0.7 }}>{month}</span>
          </span>
        ))}
      </div>
      <div style={{ marginTop: 20, overflow: "hidden", borderRadius: 20, border: `1px solid ${color.line}`, background: color.card }}>
        {trips.map((trip, index) => (
          <div
            key={`${trip.time}-${trip.place}`}
            style={{
              position: "relative",
              display: "grid",
              gridTemplateColumns: "84px 1fr auto",
              alignItems: "center",
              padding: "17px 18px",
              borderTop: index ? `1px dashed ${color.line}` : "none",
              background: index === 1 && tap > 0 ? `rgba(227,231,248,${0.7 * tap})` : "transparent",
            }}
          >
            <span style={{ fontFamily: font.mono, fontSize: 24, fontWeight: 500 }}>{trip.time}</span>
            <span>
              <span style={{ display: "block", fontSize: 18, fontWeight: 500 }}>{trip.place}</span>
              <span style={{ display: "block", marginTop: 3, fontFamily: font.mono, fontSize: 13, color: color.muted }}>
                {trip.detail}
              </span>
            </span>
            {trip.left ? (
              <span style={{ textAlign: "right" }}>
                <span style={{ display: "block", fontFamily: font.mono, fontSize: 21, fontWeight: 500, color: trip.left <= 3 ? color.warning : color.ink }}>
                  {trip.left}
                </span>
                <span style={{ display: "block", fontSize: 12, color: color.muted }}>left</span>
              </span>
            ) : (
              <span style={{ ...kicker, padding: "3px 8px", border: `1px solid ${color.line}`, borderRadius: 6, fontSize: 12 }}>
                Full
              </span>
            )}
            {index === 1 && tap > 0 && tap < 1 && (
              <span
                style={{
                  position: "absolute",
                  left: 200,
                  top: "50%",
                  width: 140 * tap,
                  height: 140 * tap,
                  marginLeft: -70 * tap,
                  marginTop: -70 * tap,
                  borderRadius: "50%",
                  background: `rgba(48,65,161,${0.25 * (1 - tap)})`,
                }}
              />
            )}
          </div>
        ))}
      </div>
    </AppScreen>
  );
}

export function TapOverlay() {
  const tap = progress(useCurrentFrame(), 58, 16);
  if (tap === 0 || tap === 1) return null;
  return (
    <span
      style={{
        position: "absolute",
        left: 224 - 70 * tap,
        top: 533 - 70 * tap,
        width: 140 * tap,
        height: 140 * tap,
        borderRadius: "50%",
        background: `rgba(48,65,161,${0.3 * (1 - tap)})`,
      }}
    />
  );
}
