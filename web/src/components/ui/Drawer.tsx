import { X } from "lucide-react";
import { useEffect, useId, useRef, type ReactNode } from "react";

export function Drawer({
  open,
  title,
  description,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    if (open) dialog.current?.showModal();
  }, [open]);

  if (!open) return null;

  return (
    <dialog
      aria-labelledby={titleId}
      className="fixed inset-y-0 right-0 left-auto m-0 h-dvh max-h-none w-full max-w-md bg-card p-0 text-ink shadow-[0_0_48px_rgba(20,23,43,0.16)] transition-[translate,opacity] duration-200 ease-out backdrop:bg-brand-900/35 starting:translate-x-6 starting:opacity-0"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      onClose={onClose}
      ref={dialog}
    >
      <div className="flex h-full flex-col">
        <header className="flex items-start justify-between gap-4 border-b border-line px-6 pt-6 pb-5">
          <div>
            <h2
              className="font-display text-[1.75rem] leading-tight font-light text-brand-900"
              id={titleId}
            >
              {title}
            </h2>
            {description && (
              <p className="mt-1 text-sm text-muted">{description}</p>
            )}
          </div>
          <button
            aria-label="Close"
            className="-mt-1 -mr-3 grid size-11 shrink-0 place-items-center rounded-full text-muted transition-colors hover:bg-paper hover:text-ink"
            onClick={onClose}
            type="button"
          >
            <X aria-hidden className="size-5" />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto px-6 py-6">{children}</div>
      </div>
    </dialog>
  );
}
