import { TriangleAlert } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Button } from "./ui/Button";
import { Panel } from "./ui/Panel";

export function FormCard({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <Panel className="p-6">
      <h2 className="mb-5 font-display text-2xl font-light text-brand-900">
        {title}
      </h2>
      {children}
    </Panel>
  );
}

export function FormActions({
  busy,
  submitLabel,
  onCancel,
  confirm,
}: {
  busy: boolean;
  submitLabel: string;
  onCancel?: () => void;
  confirm?: string;
}) {
  const [asking, setAsking] = useState(false);

  if (confirm && asking) {
    return (
      <div
        aria-label="Confirm change"
        className="col-span-full rounded-xl border border-warning/25 bg-warning-soft p-4"
        role="group"
      >
        <p className="flex gap-2 text-sm text-warning">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          {confirm}
        </p>
        <div className="mt-4 flex justify-end gap-2">
          <Button
            autoFocus
            onClick={() => setAsking(false)}
            type="button"
            variant="secondary"
          >
            Go back
          </Button>
          <Button disabled={busy} type="submit">
            {busy ? "Saving…" : "Yes, save"}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="col-span-full flex justify-end gap-2 border-t border-line pt-5">
      {onCancel && (
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      )}
      <Button
        disabled={busy}
        onClick={confirm ? () => setAsking(true) : undefined}
        type={confirm ? "button" : "submit"}
      >
        {busy ? "Saving…" : submitLabel}
      </Button>
    </div>
  );
}

export function ErrorMessage({ error }: { error: unknown }) {
  if (!(error instanceof Error)) return null;
  return (
    <p
      className="col-span-full rounded-xl border border-danger/20 bg-danger-soft px-4 py-3 text-sm text-danger"
      role="alert"
    >
      {error.message}
    </p>
  );
}
