import { useMutation } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import { HeroTicket } from "./HeroTicket";
import { OpenInLine } from "./OpenInLine";
import { Button } from "./ui/Button";
import { startDemoStudent } from "../services/demoSession";
import { demoLoginEnabled } from "../services/liffConfig";
import type { AuthSession } from "../types/auth";

export function LandingHero({
  onSignedIn,
}: {
  onSignedIn: (session: AuthSession) => void;
}) {
  const demo = useMutation({
    mutationFn: startDemoStudent,
    onSuccess: (session) => onSignedIn(session),
  });

  return (
    <section className="mx-auto grid max-w-6xl gap-16 px-5 pt-14 pb-20 sm:px-8 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)] lg:items-center lg:gap-20 lg:pt-24 lg:pb-28">
      <div>
        <p className="font-mono text-[11px] tracking-[0.2em] text-muted uppercase">
          Assumption University · Campus vans
        </p>
        <h1 className="mt-6 font-display text-[44px] leading-[1.02] tracking-tight text-brand-900 sm:text-6xl lg:text-[76px]">
          Book a seat on the campus van{" "}
          <em className="text-brand-600">without leaving LINE.</em>
        </h1>
        <p className="mt-7 max-w-lg text-lg leading-8 text-muted">
          Pick a departure, hold your seat for five minutes and pay by
          PromptPay. Once staff approve your slip, the ticket arrives in LINE.
        </p>
        <div className="mt-10 flex flex-wrap items-center gap-x-8 gap-y-4">
          {demoLoginEnabled && (
            <Button
              className="h-12 px-6 text-[15px]"
              disabled={demo.isPending}
              onClick={() => demo.mutate()}
            >
              {demo.isPending ? "Opening…" : "Try the student app"}
              <ArrowRight aria-hidden className="size-4" />
            </Button>
          )}
          <OpenInLine />
        </div>
        {demo.error && (
          <p className="mt-4 text-sm text-danger" role="alert">
            {demo.error.message}
          </p>
        )}
      </div>
      <HeroTicket />
    </section>
  );
}
