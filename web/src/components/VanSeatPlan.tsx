import type { ReactNode } from "react";

const cell = 40;
const gap = 8;
const side = 22;
const nose = 64;
const cab = 64;
const tail = 28;

function geometry(rows: number, columns: number) {
  const width = 2 * side + columns * cell + (columns - 1) * gap;
  const gridTop = nose + cab;
  const height = gridTop + rows * cell + (rows - 1) * gap + tail;
  return { width, height, gridTop };
}

function bodyPath(width: number, height: number): string {
  const right = width - 6;
  const bottom = height - 4;
  const round = Math.min(36, (width - 12) / 2);
  return [
    `M6 ${bottom - 12} V${4 + round} Q6 4 ${6 + round} 4`,
    `H${right - round} Q${right} 4 ${right} ${4 + round}`,
    `V${bottom - 12} Q${right} ${bottom} ${right - 12} ${bottom}`,
    `H18 Q6 ${bottom} 6 ${bottom - 12} Z`,
  ].join(" ");
}

export function VanSeatPlan({
  rows,
  columns,
  renderCell,
  label,
  cellSize = 56,
}: {
  rows: number;
  columns: number;
  renderCell: (rowNumber: number, columnNumber: number) => ReactNode;
  label?: string;
  cellSize?: number;
}) {
  const { width, height, gridTop } = geometry(rows, columns);
  const driverX = width - side - cell;
  const doorHeight = Math.min(2, rows) * (cell + gap) - gap;
  const percent = (value: number, total: number) => `${(value / total) * 100}%`;

  return (
    <div
      aria-hidden={label ? undefined : true}
      aria-label={label}
      className="relative w-full"
      role={label ? "group" : undefined}
      style={{ aspectRatio: `${width} / ${height}`, maxWidth: (width * cellSize) / cell }}
    >
      <svg
        aria-hidden="true"
        className="absolute inset-0 h-full w-full"
        viewBox={`0 0 ${width} ${height}`}
      >
        <rect className="fill-brand-900" height={14} rx={3} width={8} x={0} y={60} />
        <rect className="fill-brand-900" height={14} rx={3} width={8} x={width - 8} y={60} />
        <path className="fill-card stroke-brand-900" d={bodyPath(width, height)} strokeWidth={2.5} />
        <path
          className="fill-brand-100 stroke-brand-700"
          d={`M${side} 40 Q${width / 2} 20 ${width - side} 40 L${width - side} 54 Q${width / 2} 40 ${side} 54 Z`}
          strokeWidth={1.5}
        />
        <circle className="fill-none stroke-brand-700" cx={driverX + cell / 2} cy={nose + 8} r={10} strokeWidth={3} />
        <rect className="fill-brand-900" height={cell - 4} rx={8} width={cell} x={driverX} y={nose + 20} />
        <rect className="fill-accent" height={doorHeight} rx={3} width={6} x={3} y={gridTop} />
        <line className="stroke-line" strokeWidth={2} x1={side} x2={width - side} y1={height - 14} y2={height - 14} />
        {cellSize >= 32 && (
          <g className="fill-muted text-[8px] font-semibold uppercase tracking-widest">
            <text className="fill-card" textAnchor="middle" x={driverX + cell / 2} y={nose + 41}>
              Driver
            </text>
            <text
              textAnchor="middle"
              transform={`translate(15 ${gridTop + doorHeight / 2}) rotate(-90)`}
            >
              Door
            </text>
          </g>
        )}
      </svg>
      {Array.from({ length: rows }, (_, rowIndex) =>
        Array.from({ length: columns }, (_, columnIndex) => {
          const content = renderCell(rowIndex + 1, columnIndex + 1);
          if (!content) return null;
          return (
            <div
              className="absolute"
              key={`${rowIndex}:${columnIndex}`}
              style={{
                left: percent(side + columnIndex * (cell + gap), width),
                top: percent(gridTop + rowIndex * (cell + gap), height),
                width: percent(cell, width),
                height: percent(cell, height),
              }}
            >
              {content}
            </div>
          );
        }),
      )}
    </div>
  );
}
