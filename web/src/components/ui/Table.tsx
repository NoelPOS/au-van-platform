import type { ReactNode } from "react";

export type Column<T> = {
  label: string;
  render: (row: T) => ReactNode;
  align?: "end";
};

const mobileLabel =
  "max-md:before:mb-1 max-md:before:block max-md:before:text-[10px] max-md:before:font-semibold max-md:before:tracking-[0.14em] max-md:before:text-muted max-md:before:uppercase max-md:before:content-[attr(data-label)]";

function cellClass(index: number, align?: "end") {
  if (align === "end") return "text-right max-md:col-span-2 max-md:-mb-2";
  if (index === 0) return "font-medium text-ink max-md:col-span-2 max-md:text-base";
  return `text-ink/85 ${mobileLabel}`;
}

export function Table<T>({
  label,
  columns,
  rows,
  rowKey,
  empty,
}: {
  label: string;
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  empty: ReactNode;
}) {
  if (rows.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-line bg-card/60">
        {empty}
      </div>
    );
  }

  return (
    <div className="rounded-2xl border border-line bg-card max-md:border-0 max-md:bg-transparent">
      <table aria-label={label} className="w-full border-collapse text-sm max-md:block">
        <thead className="max-md:sr-only">
          <tr className="border-b border-line">
            {columns.map((column, index) => (
              <th
                className={`px-5 pt-4 pb-3 text-[11px] font-semibold tracking-[0.14em] text-muted uppercase ${column.align === "end" ? "text-right" : "text-left"}`}
                key={`${column.label}-${index}`}
                scope="col"
              >
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="max-md:flex max-md:flex-col max-md:gap-3">
          {rows.map((row) => (
            <tr
              className="border-line transition-colors duration-150 hover:bg-paper/70 max-md:grid max-md:grid-cols-2 max-md:gap-x-4 max-md:gap-y-4 max-md:rounded-2xl max-md:border max-md:bg-card max-md:p-4 md:border-b md:last:border-b-0"
              key={rowKey(row)}
            >
              {columns.map((column, index) => (
                <td
                  className={`px-5 py-4 align-middle tabular-nums max-md:p-0 ${cellClass(index, column.align)}`}
                  data-label={column.label}
                  key={`${column.label}-${index}`}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
