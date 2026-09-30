import type { Notice } from "../types/booking";

export function BookingNotice({ notice }: { notice: Notice | null }) {
  /*
    Both live regions stay mounted and only their text changes. A
    `role="status"` region inserted together with its first text is
    announced unreliably, and swapping the role on one element has the
    same problem. Empty, the wrapper is taken out of the flex flow rather
    than unmounted, so it neither leaves a gap nor leaves the tree.
  */
  return (
    <div className={notice ? "" : "sr-only"}>
      <p
        className={
          notice?.tone === "error"
            ? "rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700"
            : ""
        }
        role="alert"
      >
        {notice?.tone === "error" ? notice.message : ""}
      </p>
      <p
        className={
          notice?.tone === "status"
            ? "rounded-xl bg-brand-soft px-4 py-3 text-sm text-brand"
            : ""
        }
        role="status"
      >
        {notice?.tone === "status" ? notice.message : ""}
      </p>
    </div>
  );
}
