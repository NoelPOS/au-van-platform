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
