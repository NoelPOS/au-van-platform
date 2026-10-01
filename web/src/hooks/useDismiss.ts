import { useEffect, type RefObject } from "react";

export function useDismiss(
  container: RefObject<HTMLElement | null>,
  open: boolean,
  dismiss: () => void,
) {
  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      if (!container.current?.contains(event.target as Node)) dismiss();
    }
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [container, open, dismiss]);
}
