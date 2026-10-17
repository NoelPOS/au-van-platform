import { random, useCurrentFrame } from "remotion";
import { color, font } from "../theme";

const glyphs = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

function Tile({ char, size, tone }: { char: string; size: number; tone: string }) {
  return (
    <span
      style={{
        position: "relative",
        display: "grid",
        placeItems: "center",
        width: size * 0.8,
        height: size * 1.3,
        borderRadius: size * 0.1,
        background: "rgba(10,13,38,0.55)",
        fontFamily: font.mono,
        fontSize: size,
        fontWeight: 500,
        color: tone,
      }}
    >
      {char}
      <span
        style={{
          position: "absolute",
          left: 0,
          right: 0,
          top: "50%",
          height: 2,
          background: color.navy,
        }}
      />
    </span>
  );
}

export function SplitFlap({
  text,
  from,
  size,
  tone = color.paper,
}: {
  text: string;
  from: number;
  size: number;
  tone?: string;
}) {
  const frame = useCurrentFrame();
  return (
    <span style={{ display: "flex", gap: size * 0.1 }}>
      {[...text].map((char, index) => {
        const settled = frame >= from + index * 2 + 6;
        const spinning = frame >= from && !settled;
        const noise = random(`${text}-${index}-${Math.floor(frame / 2)}`);
        const shown = settled
          ? char
          : spinning
            ? glyphs[Math.floor(noise * glyphs.length)]
            : "";
        return <Tile char={shown} key={index} size={size} tone={tone} />;
      })}
    </span>
  );
}
