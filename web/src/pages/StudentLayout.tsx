import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Outlet, useLocation, useMatch } from "react-router";
import { BookingNotice } from "../components/BookingNotice";
import { LineFriendBanner } from "../components/LineFriendBanner";
import { SignInAgain } from "../components/SignInAgain";
import { StudentTabBar } from "../components/StudentTabBar";
import { useMyBookings } from "../hooks/useBookingQueries";
import { useLiveUpdates } from "../hooks/useLiveUpdates";
import type { StudentContext } from "../hooks/useStudent";
import type { AuthSession } from "../types/auth";
import type { Notice } from "../types/booking";
import { isUnauthorized } from "../utils/errors";
import { needsPayment } from "../utils/tickets";

function useSignInExpired(): boolean {
  const client = useQueryClient();
  const [expired, setExpired] = useState(false);
  useEffect(() => {
    const check = (error: unknown) => {
      if (isUnauthorized(error)) setExpired(true);
    };
    const queries = client
      .getQueryCache()
      .subscribe((event) => check(event.query.state.error));
    const mutations = client
      .getMutationCache()
      .subscribe((event) => check(event.mutation?.state.error));
    return () => {
      queries();
      mutations();
    };
  }, [client]);
  return expired;
}

export function StudentLayout({ session }: { session: AuthSession }) {
  const location = useLocation();
  const booking = useMatch("/trips/:tripId");
  const bookings = useMyBookings(session);
  const expired = useSignInExpired();
  useLiveUpdates(session);
  const carried = (location.state as { notice?: Notice } | null)?.notice ?? null;
  // Keyed to the screen it was raised on, so a message never outlives its screen.
  const [raised, setRaised] = useState<{ key: string; notice: Notice | null } | null>(null);
  const notice = raised?.key === location.key ? raised.notice : carried;

  if (expired) return <SignInAgain />;

  const context: StudentContext = {
    session,
    setNotice: (next) => setRaised({ key: location.key, notice: next }),
  };
  const firstName = session.user.displayName?.split(" ")[0];

  return (
    <div className="min-h-dvh bg-paper pb-[calc(5rem+env(safe-area-inset-bottom))]">
      <header className="mx-auto flex max-w-xl items-baseline justify-between px-5 pt-5">
        <p className="font-display text-xl tracking-tight text-brand-900">
          AU<span className="text-accent">·</span>Van
        </p>
        {firstName && <p className="text-sm text-muted">{firstName}</p>}
      </header>
      <main className="mx-auto flex max-w-xl flex-col gap-5 px-5 pt-5">
        <LineFriendBanner />
        <BookingNotice notice={notice} />
        <Outlet context={context} />
      </main>
      {!booking && (
        <StudentTabBar
          ticketsNeedingAction={(bookings.data ?? []).filter(needsPayment).length}
        />
      )}
    </div>
  );
}
