import type { ReactNode } from "react";
import { Button } from "../../components/ui/Button";
import { Panel } from "../../components/ui/Panel";

export function FormCard({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <Panel className="p-5">
      <h2 className="mb-4 text-lg font-bold text-ink">{title}</h2>
      {children}
    </Panel>
  );
}

export function FormActions({
  busy,
  submitLabel,
  onCancel,
}: {
  busy: boolean;
  submitLabel: string;
  onCancel?: () => void;
}) {
  return (
    <div className="col-span-full flex justify-end gap-2">
      {onCancel && (
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      )}
      <Button disabled={busy} type="submit">
        {busy ? "Saving…" : submitLabel}
      </Button>
    </div>
  );
}

export function ErrorMessage({ error }: { error: unknown }) {
  if (!(error instanceof Error)) return null;
  return (
    <p
      className="col-span-full rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700"
      role="alert"
    >
      {error.message}
    </p>
  );
}
