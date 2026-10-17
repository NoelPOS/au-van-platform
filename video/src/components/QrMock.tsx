import { random } from "remotion";
import { color } from "../theme";

const modules = 25;

function inFinder(x: number, y: number) {
  const near = (value: number) => value < 8 || value >= modules - 8;
  return near(x) && near(y) && !(x >= modules - 8 && y >= modules - 8);
}

function finderCell(x: number, y: number) {
  const fx = x >= modules - 8 ? x - (modules - 7) : x;
  const fy = y >= modules - 8 ? y - (modules - 7) : y;
  if (fx < 0 || fy < 0 || fx > 6 || fy > 6) return false;
  const ring = Math.min(fx, fy, 6 - fx, 6 - fy);
  return ring !== 1;
}

export function QrMock({ size }: { size: number }) {
  const cell = size / modules;
  const cells = [];
  for (let y = 0; y < modules; y++) {
    for (let x = 0; x < modules; x++) {
      const on = inFinder(x, y) ? finderCell(x, y) : random(`qr-${x}-${y}`) > 0.52;
      if (on) cells.push(<rect height={cell} key={`${x}-${y}`} width={cell} x={x * cell} y={y * cell} />);
    }
  }
  return (
    <svg fill={color.ink} height={size} shapeRendering="crispEdges" width={size}>
      {cells}
    </svg>
  );
}
