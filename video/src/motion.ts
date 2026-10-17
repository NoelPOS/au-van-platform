import type { CSSProperties } from "react";
import { Easing, interpolate } from "remotion";

const ease = Easing.bezier(0.2, 0.7, 0.2, 1);

export function progress(frame: number, start: number, length = 18): number {
  return interpolate(frame, [start, start + length], [0, 1], {
    easing: ease,
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
}

export function rise(frame: number, start: number, distance = 28): CSSProperties {
  const t = progress(frame, start);
  return { opacity: t, transform: `translateY(${(1 - t) * distance}px)` };
}
