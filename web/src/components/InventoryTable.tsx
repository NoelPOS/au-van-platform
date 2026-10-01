import type { ReactNode } from "react";
import { EmptyState } from "./ui/EmptyState";
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
          <thead className="border-b border-line text-left text-[11px] tracking-[0.14em] text-muted uppercase">
            <tr>
              {headings.map((heading, index) => (
                <th
                  className="px-5 pt-4 pb-3 font-semibold"
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
      <td colSpan={columns}>
        <EmptyState title={title} detail={detail} />
      </td>
    </tr>
  );
}
