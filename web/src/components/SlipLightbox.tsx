import { X } from "lucide-react";
import { useEffect, useRef } from "react";

export function SlipLightbox({
  source,
  reference,
  onClose,
}: {
  source: string;
  reference: string;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const label = `Payment slip for booking ${reference} at full size`;

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  return (
    <dialog
      aria-label={label}
      className="m-0 h-dvh max-h-none w-full max-w-none bg-brand-900/95 p-4 backdrop:bg-brand-900/60 sm:p-8"
      onClose={onClose}
      ref={dialog}
    >
      <div className="flex h-full flex-col gap-4">
        <div className="flex items-center justify-between gap-4 text-paper">
          <p className="font-mono text-xs tracking-[0.14em] uppercase">{reference}</p>
          <button
            className="inline-flex min-h-11 items-center gap-2 rounded-full border border-paper/40 px-4 text-sm font-semibold hover:bg-paper/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-paper"
            onClick={() => dialog.current?.close()}
            type="button"
          >
            Close
            <X aria-hidden className="size-4" />
          </button>
        </div>
        <img alt={label} className="mx-auto min-h-0 w-full flex-1 object-contain" src={source} />
      </div>
    </dialog>
  );
}
