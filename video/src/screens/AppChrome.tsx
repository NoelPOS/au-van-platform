import type { ReactNode } from "react";
import { useCurrentFrame } from "remotion";
import { Logo, Wordmark } from "../components/Logo";
import { progress } from "../motion";
import { color, font } from "../theme";

export const kicker = {
  margin: 0,
  fontFamily: font.mono,
  fontSize: 13,
  letterSpacing: "0.18em",
  textTransform: "uppercase" as const,
  color: color.muted,
};

export function StatusBar() {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "space-between",
        padding: "20px 34px 0",
        fontFamily: font.mono,
        fontSize: 15,
        fontWeight: 600,
        color: color.ink,
      }}
    >
      <span>13:52</span>
      <span>5G</span>
    </div>
  );
}

export function Chevron({ turn = 0 }: { turn?: number }) {
  return (
    <svg height={16} style={{ flexShrink: 0, transform: `rotate(${turn}deg)` }} viewBox="0 0 24 24" width={16}>
      <path d="m9 18 6-6-6-6" fill="none" stroke={color.muted} strokeLinecap="round" strokeWidth={2} />
    </svg>
  );
}

export function RouteRule() {
  return (
    <span style={{ display: "flex", alignItems: "center" }}>
      <span style={{ width: 6, height: 6, borderRadius: 3, background: color.brandBright }} />
      <span style={{ width: 12, borderTop: "1px dashed rgba(63,83,201,0.6)" }} />
      <span style={{ width: 6, height: 6, borderRadius: 3, border: `1px solid ${color.brandBright}` }} />
    </span>
  );
}

export function TapRipple({ at, left, top }: { at: number; left: number; top: number | string }) {
  const tap = progress(useCurrentFrame(), at, 16);
  if (tap === 0 || tap === 1) return null;
  const size = 140 * tap;
  return (
    <span
      style={{
        position: "absolute",
        left,
        top,
        width: size,
        height: size,
        marginLeft: -size / 2,
        marginTop: -size / 2,
        borderRadius: "50%",
        background: `rgba(48,65,161,${0.3 * (1 - tap)})`,
      }}
    />
  );
}

export function AppScreen({ children, tabs = true }: { children: ReactNode; tabs?: boolean }) {
  return (
    <div style={{ position: "absolute", inset: 0, fontFamily: font.sans, color: color.ink }}>
      <StatusBar />
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          padding: "41px 32px 0",
        }}
      >
        <span style={{ display: "flex", alignItems: "center", gap: 11 }}>
          <Logo size={27} />
          <Wordmark size={21} />
        </span>
        <span style={{ fontSize: 16, color: color.muted }}>Ploy</span>
      </div>
      <div style={{ padding: "28px 32px 0" }}>{children}</div>
      {tabs && <TabBar />}
    </div>
  );
}

const tabIcons = [
  <>
    <path d="M4 6 2 7" />
    <path d="M10 6h4" />
    <path d="m22 7-2-1" />
    <rect height="16" rx="2" width="16" x="4" y="3" />
    <path d="M4 11h16" />
    <path d="M8 15h.01" />
    <path d="M16 15h.01" />
    <path d="M6 19v2" />
    <path d="M18 21v-2" />
  </>,
  <>
    <path d="M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z" />
    <path d="M13 5v2" />
    <path d="M13 17v2" />
    <path d="M13 11v2" />
  </>,
];

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
        height: 60,
        borderTop: `1px solid ${color.line}`,
        background: color.card,
        fontSize: 12,
        fontWeight: 500,
        letterSpacing: "0.02em",
      }}
    >
      {["Departures", "Tickets"].map((label, index) => (
        <span
          key={label}
          style={{
            position: "relative",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            gap: 4,
            color: index === 0 ? color.navy : color.muted,
          }}
        >
          {index === 0 && (
            <span
              style={{
                position: "absolute",
                top: 0,
                left: 34,
                right: 34,
                height: 2,
                borderRadius: 2,
                background: color.amber,
              }}
            />
          )}
          <svg
            fill="none"
            height={21}
            stroke="currentColor"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.75}
            viewBox="0 0 24 24"
            width={21}
          >
            {tabIcons[index]}
          </svg>
          {label}
        </span>
      ))}
    </div>
  );
}
