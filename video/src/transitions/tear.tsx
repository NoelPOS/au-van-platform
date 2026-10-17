import type {
  TransitionPresentation,
  TransitionPresentationComponentProps,
} from "@remotion/transitions";
import { AbsoluteFill, random } from "remotion";

type NoProps = Record<string, never>;

const teeth = Array.from({ length: 121 }, (_, index) => {
  const depth = 2 + random(`tear-${index}`) * 6;
  return `${(index * 100) / 120}% calc(50% + ${index % 2 ? depth : -depth}px)`;
});
const upper = `polygon(0 0, 100% 0, ${[...teeth].reverse().join(", ")})`;
const lower = `polygon(${teeth.join(", ")}, 100% 100%, 0 100%)`;
const shadow = "drop-shadow(0 18px 28px rgba(20,23,43,0.35))";

function Tear({
  children,
  presentationDirection,
  presentationProgress,
}: TransitionPresentationComponentProps<NoProps>) {
  if (presentationDirection === "entering") return <AbsoluteFill>{children}</AbsoluteFill>;
  const pull = presentationProgress;
  return (
    <AbsoluteFill style={{ zIndex: 1 }}>
      <AbsoluteFill
        style={{
          filter: shadow,
          transform: `translateY(${-pull * 62}%) rotate(${-pull * 5}deg)`,
          transformOrigin: "0 50%",
        }}
      >
        <AbsoluteFill style={{ clipPath: upper }}>{children}</AbsoluteFill>
      </AbsoluteFill>
      <AbsoluteFill
        style={{
          filter: shadow,
          transform: `translateY(${pull * 62}%) rotate(${pull * 3}deg)`,
          transformOrigin: "100% 50%",
        }}
      >
        <AbsoluteFill style={{ clipPath: lower }}>{children}</AbsoluteFill>
      </AbsoluteFill>
    </AbsoluteFill>
  );
}

export function tear(): TransitionPresentation<NoProps> {
  return { component: Tear, props: {} };
}
