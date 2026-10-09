import type { Notice } from "../types/booking";

export function BookingNotice({ notice }: { notice: Notice | null }) {
  // Both regions stay mounted: a live region inserted with its first text is announced unreliably.
  return (
    <div className={notice ? "" : "sr-only"}>
      <p
        className={
          notice?.tone === "error"
            ? "rounded-2xl border border-danger/20 bg-danger-soft px-4 py-3 text-sm text-danger"
            : ""
        }
        role="alert"
      >
        {notice?.tone === "error" ? notice.message : ""}
      </p>
      <p
        className={
          notice?.tone === "status"
            ? "rounded-2xl border border-brand-100 bg-brand-50 px-4 py-3 text-sm text-brand-700"
            : ""
        }
        role="status"
      >
        {notice?.tone === "status" ? notice.message : ""}
      </p>
    </div>
  );
}
