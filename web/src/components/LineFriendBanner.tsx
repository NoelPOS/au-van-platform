import { MessageCircle } from "lucide-react";
import { useLineFriendship } from "../hooks/useLineFriendship";

export function LineFriendBanner() {
  const { addUrl, notFriend } = useLineFriendship();
  if (!notFriend || !addUrl) return null;
  return (
    <aside className="flex items-start gap-3 rounded-2xl border border-success/25 bg-success-soft px-4 py-3.5">
      <MessageCircle
        aria-hidden
        className="mt-0.5 size-5 shrink-0 text-success"
        strokeWidth={1.75}
      />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-ink">Turn on LINE updates</p>
        <p className="mt-0.5 text-sm text-muted">
          Add AU-Van as a friend so we can tell you when your seat is
          confirmed or your slip needs another look.
        </p>
        <a
          className="mt-2.5 inline-flex min-h-10 items-center rounded-full bg-success px-4 text-sm font-semibold text-white hover:bg-success/90"
          href={addUrl}
          rel="noreferrer"
          target="_blank"
        >
          Add AU-Van on LINE
        </a>
      </div>
    </aside>
  );
}
