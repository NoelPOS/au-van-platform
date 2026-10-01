import { CircleAlert, CircleCheck } from "lucide-react";
import { useCallback, useState, type ReactNode } from "react";
import { ToastContext, type Notify, type ToastTone } from "../../hooks/useToast";

type Toast = { id: number; message: string; tone: ToastTone };

let nextId = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const notify = useCallback<Notify>((message, tone = "success") => {
    const id = (nextId += 1);
    setToasts((current) => [...current, { id, message, tone }]);
    window.setTimeout(
      () => setToasts((current) => current.filter((toast) => toast.id !== id)),
      4000,
    );
  }, []);

  return (
    <ToastContext value={notify}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-4 top-20 z-50 flex flex-col items-center gap-2 lg:inset-x-auto lg:top-auto lg:right-6 lg:bottom-6 lg:items-end"
        role="status"
      >
        {toasts.map((toast) => (
          <p
            className={`pointer-events-auto flex items-center gap-2.5 rounded-full px-4 py-3 text-sm font-medium shadow-[0_8px_24px_rgba(20,23,43,0.18)] ${toast.tone === "success" ? "bg-brand-900 text-paper" : "bg-danger text-white"}`}
            key={toast.id}
          >
            {toast.tone === "success" ? (
              <CircleCheck aria-hidden className="size-4 text-accent" />
            ) : (
              <CircleAlert aria-hidden className="size-4" />
            )}
            {toast.message}
          </p>
        ))}
      </div>
    </ToastContext>
  );
}
