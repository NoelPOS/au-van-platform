export function FailurePanel({ title, error }: { title: string; error: Error }) {
  return (
    <div className="flex gap-3 rounded-2xl border border-danger/30 bg-danger/5 p-5">
      <svg
        aria-hidden="true"
        className="mt-0.5 size-5 shrink-0 text-danger"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="2"
        viewBox="0 0 24 24"
      >
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7.5v5M12 16.5h.01" />
      </svg>
      <div>
        <strong className="block text-ink">{title}</strong>
        <span className="mt-1 block text-sm text-muted" role="alert">
          {error.message}
        </span>
      </div>
    </div>
  );
}
