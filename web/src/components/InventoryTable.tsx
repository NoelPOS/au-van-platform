import type { ReactNode } from "react";
import { Panel } from "./ui/Panel";

export function InventoryTable({
  headings,
  children,
}: {
  headings: string[];
  children: ReactNode;
}) {
  return (
    <Panel className="overflow-hidden p-0">
      <div className="overflow-x-auto">
        <table className="w-full min-w-150 border-collapse text-sm">
          <thead className="bg-surface text-left text-xs uppercase tracking-wider text-muted">
            <tr>
              {headings.map((heading, index) => (
                <th
                  className="px-4 py-3 font-semibold"
                  key={`${heading}-${index}`}
                >
                  {heading}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>{children}</tbody>
        </table>
      </div>
    </Panel>
  );
}

export function EmptyRow({
  columns,
  title,
  detail,
}: {
  columns: number;
  title: string;
  detail: string;
}) {
  return (
    <tr>
      <td colSpan={columns} className="px-4 py-10 text-center">
        <strong className="block text-ink">{title}</strong>
        <span className="mt-1 block text-muted">{detail}</span>
      </td>
    </tr>
  );
}
