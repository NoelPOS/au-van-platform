import { AbsoluteFill, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { Logo, Wordmark } from "../components/Logo";
import { progress, rise } from "../motion";
import { color, font } from "../theme";

export function Title() {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const arrive = spring({ frame, fps, config: { damping: 16 } });
  const dot = spring({ frame: frame - 40, fps, config: { damping: 9, stiffness: 180 } });
  return (
    <AbsoluteFill
      style={{ background: color.paper, alignItems: "center", justifyContent: "center" }}
    >
      <div style={{ transform: `scale(${0.7 + 0.3 * arrive})`, opacity: arrive }}>
        <Logo dot={dot} draw={progress(frame, 6, 36)} size={210} />
      </div>
      <div style={{ marginTop: 52, ...rise(frame, 34) }}>
        <Wordmark size={156} />
      </div>
      <p
        style={{
          margin: "30px 0 0",
          fontFamily: font.sans,
          fontSize: 46,
          color: color.muted,
          ...rise(frame, 50),
        }}
      >
        Campus van seats, booked inside LINE.
      </p>
      <p
        style={{
          position: "absolute",
          bottom: 72,
          margin: 0,
          fontFamily: font.mono,
          fontSize: 18,
          letterSpacing: "0.2em",
          textTransform: "uppercase",
          color: color.muted,
          ...rise(frame, 64, 12),
        }}
      >
        Assumption University · Campus vans
      </p>
    </AbsoluteFill>
  );
}
