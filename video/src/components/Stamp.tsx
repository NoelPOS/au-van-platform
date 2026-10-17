import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { font } from "../theme";

export function Stamp({
  label,
  tone,
  size = 22,
  from = 0,
}: {
  label: string;
  tone: string;
  size?: number;
  from?: number;
}) {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const landed = spring({ frame: frame - from, fps, config: { damping: 14, stiffness: 220 } });
  const scale = interpolate(landed, [0, 1], [2.4, 1]);
  return (
    <span
      style={{
        display: "inline-block",
        color: tone,
        border: `${size / 9}px solid currentColor`,
        outline: `${size / 20}px solid currentColor`,
        outlineOffset: size / 9,
        borderRadius: size / 3.5,
        padding: `${size * 0.45}px ${size * 0.7}px`,
        fontFamily: font.mono,
        fontSize: size,
        fontWeight: 700,
        letterSpacing: "0.16em",
        lineHeight: 1,
        textTransform: "uppercase",
        whiteSpace: "nowrap",
        opacity: interpolate(frame - from, [0, 4], [0, 1], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
        }),
        transform: `rotate(-6deg) scale(${scale})`,
      }}
    >
      {label}
    </span>
  );
}
