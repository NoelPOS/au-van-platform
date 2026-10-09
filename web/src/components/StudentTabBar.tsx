import { BusFront, Ticket, type LucideIcon } from "lucide-react";
import { NavLink } from "react-router";

function Tab({
  to,
  label,
  icon: Icon,
  badge,
}: {
  to: string;
  label: string;
  icon: LucideIcon;
  badge?: number;
}) {
  return (
    <NavLink
      className={({ isActive }) =>
        `relative flex min-h-14 flex-col items-center justify-center gap-1 text-[11px] font-medium tracking-wide transition-colors duration-150 ease-out ${
          isActive
            ? "text-brand-900 before:absolute before:inset-x-8 before:top-0 before:h-0.5 before:rounded-full before:bg-accent"
            : "text-muted hover:text-ink"
        }`
      }
      end
      to={to}
    >
      <span className="relative">
        <Icon aria-hidden className="size-5" strokeWidth={1.75} />
        {badge ? (
          <span aria-hidden className="absolute -top-1.5 -right-2.5 grid min-w-4 place-items-center rounded-full bg-accent px-1 font-mono text-[10px] leading-4 font-semibold text-brand-900">
            {badge}
          </span>
        ) : null}
      </span>
      {label}
      {badge ? <span className="sr-only">{`, ${badge} need you`}</span> : null}
    </NavLink>
  );
}

export function StudentTabBar({ ticketsNeedingAction }: { ticketsNeedingAction: number }) {
  return (
    <nav
      aria-label="Student"
      className="fixed inset-x-0 bottom-0 z-20 border-t border-line bg-card pb-[env(safe-area-inset-bottom)]"
    >
      <div className="mx-auto grid max-w-xl grid-cols-2">
        <Tab icon={BusFront} label="Departures" to="/" />
        <Tab
          badge={ticketsNeedingAction}
          icon={Ticket}
          label="Tickets"
          to="/tickets"
        />
      </div>
    </nav>
  );
}
