import { useCurrentFrame } from "remotion";
import { progress } from "../motion";
import { color, font } from "../theme";
import { AppScreen, Chevron, kicker, RouteRule, TapRipple } from "./AppChrome";

export const routeTapAt = 62;

const routes = [
  { to: "BTS Udom Suk", next: "Next Sat 24 Oct, 08:00", fare: 50, minutes: 30 },
  { to: "Future Park Rangsit", next: "Next Sat 24 Oct, 10:00", fare: 120, minutes: 75 },
  { to: "Mega Bangna", next: "Next Sat 24 Oct, 07:30", fare: 80, minutes: 40 },
  { to: "Siam Paragon", next: "Next Sat 24 Oct, 09:00", fare: 90, minutes: 55 },
];

const picked = "Siam Paragon";

function Filter({ label }: { label: string }) {
  return (
    <span style={{ flex: 1 }}>
      <span style={{ display: "block", fontSize: 14, fontWeight: 500 }}>{label}</span>
      <span
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginTop: 8,
          padding: "11px 14px",
          borderRadius: 12,
          border: `1px solid ${color.line}`,
          background: color.card,
          fontSize: 16,
        }}
      >
        Any
        <Chevron turn={90} />
      </span>
    </span>
  );
}

export function RoutesScreen() {
  const tap = progress(useCurrentFrame(), routeTapAt, 16);
  return (
    <AppScreen>
      <p style={kicker}>Departures</p>
      <p style={{ margin: "6px 0 0", fontFamily: font.display, fontSize: 38, color: color.navy }}>
        Catch the next van
      </p>
      <p style={{ margin: "4px 0 0", fontFamily: font.display, fontStyle: "italic", fontSize: 19, color: color.muted }}>
        Where are you heading?
      </p>
      <div style={{ display: "flex", gap: 12, marginTop: 20 }}>
        <Filter label="From" />
        <Filter label="To" />
      </div>
      <p style={{ ...kicker, display: "flex", alignItems: "center", gap: 8, marginTop: 24, fontSize: 11 }}>
        <RouteRule />
        From Assumption University
      </p>
      <div style={{ marginTop: 10, overflow: "hidden", borderRadius: 20, border: `1px solid ${color.line}`, background: color.card }}>
        {routes.map((route, index) => (
          <div
            key={route.to}
            style={{
              position: "relative",
              display: "flex",
              alignItems: "center",
              gap: 12,
              padding: "16px 18px",
              borderTop: index ? `1px dashed ${color.line}` : "none",
              background: route.to === picked && tap > 0 ? `rgba(227,231,248,${0.7 * tap})` : "transparent",
            }}
          >
            <span style={{ flex: 1 }}>
              <span style={{ display: "block", fontFamily: font.display, fontSize: 23, color: color.navy }}>{route.to}</span>
              <span style={{ display: "block", marginTop: 5, fontSize: 14 }}>{route.next}</span>
            </span>
            <span style={{ textAlign: "right" }}>
              <span style={{ display: "block", fontFamily: font.mono, fontSize: 19, fontWeight: 500 }}>{`฿${route.fare}`}</span>
              <span style={{ display: "block", fontSize: 12, color: color.muted }}>{`${route.minutes} min`}</span>
            </span>
            <Chevron />
            {route.to === picked && <TapRipple at={routeTapAt} left={180} top="50%" />}
          </div>
        ))}
      </div>
    </AppScreen>
  );
}
