import { createContext, useContext } from "react";

export type ToastTone = "success" | "danger";
export type Notify = (message: string, tone?: ToastTone) => void;

export const ToastContext = createContext<Notify | null>(null);

export function useToast(): Notify {
  const notify = useContext(ToastContext);
  if (!notify) throw new Error("useToast is only available inside a ToastProvider.");
  return notify;
}
