import { Panel } from "./Panel";

export function FailurePanel({ title, error }: { title: string; error: Error }) {
  return (
    <Panel className="border-red-200 p-5">
      <strong className="block text-ink">{title}</strong>
      <span className="mt-1 block text-muted" role="alert">
        {error.message}
      </span>
    </Panel>
  );
}
