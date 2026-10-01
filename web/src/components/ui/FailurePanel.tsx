import { CircleAlert } from "lucide-react";

export function FailurePanel({ title, error }: { title: string; error: Error }) {
  return (
    <div className="flex gap-3 rounded-2xl border border-danger/20 bg-danger-soft p-5">
      <CircleAlert aria-hidden className="mt-0.5 size-5 shrink-0 text-danger" />
      <div>
        <strong className="block text-ink">{title}</strong>
        <span className="mt-1 block text-sm text-muted" role="alert">
          {error.message}
        </span>
      </div>
    </div>
  );
}
