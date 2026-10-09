import {
  Activity,
  Armchair,
  CalendarClock,
  Ellipsis,
  HandCoins,
  LayoutDashboard,
  ReceiptText,
  Signpost,
  Van,
  type LucideIcon,
} from "lucide-react";
import { useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router";
import { useLiveUpdates } from "../hooks/useLiveUpdates";
import { useRefundsDue } from "../hooks/useRefundQueries";
import type { AuthSession } from "../types/auth";
import { Drawer } from "./ui/Drawer";
import { ToastProvider } from "./ui/Toast";

type Destination = {
  path: string;
  label: string;
  icon: LucideIcon;
  tucked?: boolean;
};

const destinations: Destination[] = [
  { path: "/admin", label: "Overview", icon: LayoutDashboard },
  { path: "/admin/trips", label: "Trips", icon: CalendarClock },
  { path: "/admin/routes", label: "Routes", icon: Signpost, tucked: true },
  { path: "/admin/vans", label: "Vans", icon: Van, tucked: true },
  {
    path: "/admin/seat-layouts",
    label: "Seat layouts",
    icon: Armchair,
    tucked: true,
  },
  { path: "/admin/payments", label: "Payments", icon: ReceiptText },
  { path: "/admin/refunds", label: "Refunds", icon: HandCoins, tucked: true },
  { path: "/admin/operations", label: "Operations", icon: Activity },
];

const tucked = destinations.filter((entry) => entry.tucked);

const itemClass =
  "flex min-h-11 flex-col items-center justify-center gap-1 rounded-xl px-1 text-[11px] font-medium transition-colors duration-150 lg:flex-row lg:justify-start lg:gap-3 lg:px-3 lg:text-sm";

function linkClass({ isActive }: { isActive: boolean }) {
  return `${itemClass} ${isActive ? "bg-paper text-brand-900" : "text-brand-100 hover:bg-white/8 hover:text-white"}`;
}

function RefundsBadge({ count }: { count: number }) {
  if (count === 0) return null;
  return (
    <span className="rounded-full bg-accent px-1.5 py-0.5 font-mono text-[10px] leading-none font-semibold text-brand-900 tabular-nums lg:ml-auto">
      <span aria-hidden>{count}</span>
      <span className="sr-only">{`, ${count} refunds due`}</span>
    </span>
  );
}

function Wordmark() {
  return (
    <span className="flex flex-col leading-none">
      <span className="font-display text-2xl font-normal tracking-tight">
        AU<span className="text-accent">·</span>Van
      </span>
      <span className="mt-1.5 text-[10px] font-semibold tracking-[0.2em] text-brand-100/80 uppercase">
        Operations
      </span>
    </span>
  );
}

export function AdminLayout({ session }: { session: AuthSession }) {
  const [moreOpen, setMoreOpen] = useState(false);
  const { pathname } = useLocation();
  const name = session.user.displayName ?? "Administrator";
  const inTucked = tucked.some((entry) => pathname.startsWith(entry.path));
  const refundsDue = useRefundsDue(session).data?.length ?? 0;
  useLiveUpdates(session);

  return (
    <ToastProvider>
      <header className="sticky top-0 z-30 flex h-16 items-center justify-between bg-brand-900 px-5 text-paper lg:hidden">
        <Wordmark />
        <span className="max-w-40 truncate text-sm text-brand-100">{name}</span>
      </header>
      <aside className="fixed inset-x-0 bottom-0 z-30 bg-brand-900 text-paper lg:inset-y-0 lg:right-auto lg:flex lg:w-64 lg:flex-col lg:px-5 lg:py-8">
        <div className="hidden px-3 lg:block">
          <Wordmark />
        </div>
        <nav
          aria-label="Administration"
          className="grid grid-cols-5 gap-1 px-2 pt-2 pb-[max(0.5rem,env(safe-area-inset-bottom))] lg:mt-12 lg:flex lg:flex-col lg:p-0"
        >
          {destinations.map(({ path, label, icon: Icon, tucked: hidden }) => (
            <NavLink
              className={(state) =>
                `${linkClass(state)} ${hidden ? "max-lg:hidden" : ""}`
              }
              end={path === "/admin"}
              key={path}
              to={path}
            >
              <Icon
                aria-hidden
                className="size-5 shrink-0"
                strokeWidth={1.75}
              />
              {label}
              {path === "/admin/refunds" && <RefundsBadge count={refundsDue} />}
            </NavLink>
          ))}
          <button
            className={`${itemClass} relative lg:hidden ${inTucked ? "bg-paper text-brand-900" : "text-brand-100"}`}
            aria-haspopup="dialog"
            onClick={() => setMoreOpen(true)}
            type="button"
          >
            <Ellipsis aria-hidden className="size-5" strokeWidth={1.75} />
            More
            <span className="absolute top-1 left-1/2 ml-1.5">
              <RefundsBadge count={refundsDue} />
            </span>
          </button>
        </nav>
        <div className="mt-auto hidden border-t border-white/10 px-3 pt-5 lg:block">
          <p className="text-[10px] font-semibold tracking-[0.2em] text-brand-100/80 uppercase">
            Signed in
          </p>
          <p className="mt-1 truncate text-sm text-paper">{name}</p>
        </div>
      </aside>
      <Drawer onClose={() => setMoreOpen(false)} open={moreOpen} title="More">
        <ul className="flex flex-col gap-1">
          {tucked.map(({ path, label, icon: Icon }) => (
            <li key={path}>
              <NavLink
                className={({ isActive }) =>
                  `flex min-h-12 items-center gap-3 rounded-xl px-3 text-base ${isActive ? "bg-brand-50 text-brand-900" : "text-ink hover:bg-paper"}`
                }
                onClick={() => setMoreOpen(false)}
                to={path}
              >
                <Icon
                  aria-hidden
                  className="size-5 text-brand-500"
                  strokeWidth={1.75}
                />
                {label}
                {path === "/admin/refunds" && (
                  <span className="ml-auto">
                    <RefundsBadge count={refundsDue} />
                  </span>
                )}
              </NavLink>
            </li>
          ))}
        </ul>
      </Drawer>
      <div className="pb-24 lg:pb-0 lg:pl-64">
        <Outlet />
      </div>
    </ToastProvider>
  );
}
