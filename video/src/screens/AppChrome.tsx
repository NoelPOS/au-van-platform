import type { ReactNode } from "react";
import { color, font } from "../theme";

export const kicker = {
  margin: 0,
  fontFamily: font.mono,
  fontSize: 13,
  letterSpacing: "0.18em",
  textTransform: "uppercase" as const,
  color: color.muted,
};

export function AppScreen({ children, tabs = true }: { children: ReactNode; tabs?: boolean }) {
  return (
    <div style={{ position: "absolute", inset: 0, fontFamily: font.sans, color: color.ink }}>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          padding: "20px 34px 0",
          fontFamily: font.mono,
          fontSize: 15,
          fontWeight: 600,
        }}
      >
        <span>07:12</span>
        <span>5G</span>
      </div>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "baseline",
          padding: "26px 26px 0",
        }}
      >
        <span style={{ fontFamily: font.display, fontSize: 26, color: color.navy }}>
          AU<span style={{ color: color.amber }}>·</span>Van
        </span>
        <span style={{ fontSize: 16, color: color.muted }}>Ploy</span>
      </div>
      <div style={{ padding: "22px 26px 0" }}>{children}</div>
      {tabs && <TabBar />}
    </div>
  );
}

function TabBar() {
  return (
    <div
      style={{
        position: "absolute",
        left: 0,
        right: 0,
        bottom: 0,
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        height: 82,
        paddingBottom: 14,
        borderTop: `1px solid ${color.line}`,
        background: color.card,
        fontSize: 14,
        fontWeight: 500,
      }}
    >
      {["Departures", "Tickets"].map((label, index) => (
        <span
          key={label}
          style={{
            position: "relative",
            display: "grid",
            placeItems: "center",
            color: index === 0 ? color.navy : color.muted,
          }}
        >
          {index === 0 && (
            <span
              style={{
                position: "absolute",
                top: 0,
                left: 60,
                right: 60,
                height: 3,
                borderRadius: 2,
                background: color.amber,
              }}
            />
          )}
          {label}
        </span>
      ))}
    </div>
  );
}
