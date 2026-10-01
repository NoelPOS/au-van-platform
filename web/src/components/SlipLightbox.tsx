import { useEffect, useRef, type KeyboardEvent } from "react";

export function SlipLightbox({
  source,
  reference,
  onClose,
}: {
  source: string;
  reference: string;
  onClose: () => void;
}) {
  const closeButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    closeButton.current?.focus();
    return () => opener?.focus();
  }, []);

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === "Escape") onClose();
    if (event.key === "Tab") event.preventDefault();
  }

  return (
    <div
      aria-label={`Payment slip for booking ${reference} at full size`}
      aria-modal="true"
      className="fixed inset-0 z-50 flex flex-col gap-4 bg-brand-900/95 p-4 sm:p-8"
      onKeyDown={onKeyDown}
      role="dialog"
    >
      <div className="flex items-center justify-between gap-4 text-paper">
        <p className="font-mono text-xs tracking-[0.14em] uppercase">{reference}</p>
        <button
          className="inline-flex min-h-11 items-center gap-2 rounded-full border border-paper/40 px-4 text-sm font-semibold hover:bg-paper/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-paper"
          onClick={onClose}
          ref={closeButton}
          type="button"
        >
          Close
          <svg
            aria-hidden="true"
            className="size-4"
            fill="none"
            stroke="currentColor"
            strokeLinecap="round"
            strokeWidth="2"
            viewBox="0 0 24 24"
          >
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>
      <img
        alt={`Payment slip for booking ${reference} at full size`}
        className="mx-auto min-h-0 w-full flex-1 object-contain"
        src={source}
      />
    </div>
  );
}
