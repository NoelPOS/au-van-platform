import { AbsoluteFill, useCurrentFrame } from "remotion";
import { Logo, Wordmark } from "../components/Logo";
import { Stamp } from "../components/Stamp";
import { rise } from "../motion";
import { color, font } from "../theme";

const stampAt = 34;

const kicker = {
  margin: 0,
  fontFamily: font.mono,
  fontSize: 18,
  letterSpacing: "0.2em",
  color: color.muted,
};

function Notch({ edge }: { edge: "top" | "bottom" }) {
  return (
    <span
      style={{
        position: "absolute",
        left: -19,
        [edge]: -20,
        width: 40,
        height: 40,
        borderRadius: 20,
        border: `1px solid ${color.line}`,
        background: color.paper,
      }}
    />
  );
}

export function EndCard() {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{ background: color.paper, alignItems: "center", justifyContent: "center" }}>
      <div
        style={{
          position: "relative",
          display: "flex",
          width: 1300,
          height: 440,
          overflow: "hidden",
          borderRadius: 32,
          border: `1px solid ${color.line}`,
          background: color.card,
          boxShadow: "0 50px 90px -60px rgba(27,37,102,0.5)",
          ...rise(frame, 4, 40),
        }}
      >
        <div style={{ width: 400, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 30 }}>
          <Logo size={150} />
          <Wordmark size={70} />
        </div>
        <div style={{ position: "relative" }}>
          <span style={{ position: "absolute", top: 36, bottom: 36, borderLeft: `3px dashed ${color.line}` }} />
          <Notch edge="top" />
          <Notch edge="bottom" />
        </div>
        <div style={{ flex: 1, display: "flex", flexDirection: "column", justifyContent: "center", padding: "0 80px" }}>
          <p style={kicker}>TRY IT</p>
          <p style={{ margin: "12px 0 0", fontFamily: font.display, fontSize: 84, letterSpacing: "-0.025em", lineHeight: 1, color: color.navy }}>
            auvan.duckdns.org
          </p>
          <p style={{ ...kicker, marginTop: 50 }}>SOURCE</p>
          <p style={{ margin: "12px 0 0", fontFamily: font.mono, fontSize: 32, color: color.ink }}>
            github.com/NoelPOS/au-van-platform
          </p>
        </div>
        <div style={{ position: "absolute", top: 44, right: 56 }}>
          {frame >= stampAt && <Stamp from={stampAt} label="Live demo" size={22} tone={color.warning} />}
        </div>
      </div>
      <p style={{ ...kicker, position: "absolute", bottom: 72, fontSize: 16, ...rise(frame, 20, 10) }}>
        A PORTFOLIO PROJECT · PAYMENTS ARE SAMPLES, NOTHING IS CHARGED
      </p>
    </AbsoluteFill>
  );
}
