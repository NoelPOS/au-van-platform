import type { ReactNode } from "react";
import { LandingSection } from "./LandingSection";
import { StatusStamp } from "./StatusStamp";

const badge =
  "rounded-md border border-line px-2 py-1 font-mono text-[11px] tracking-[0.14em] text-muted uppercase";

const steps: { title: string; detail: string; status: ReactNode }[] = [
  {
    title: "Pick a departure",
    detail: "Every van for the day on one board, with the seats left on each.",
    status: <span className={badge}>4 left</span>,
  },
  {
    title: "Hold a seat for five minutes",
    detail:
      "Choose a seat on that van’s own layout. Nobody else can take it while the clock runs.",
    status: (
      <span className="font-mono text-xl font-medium text-warning tabular-nums">
        05:00
      </span>
    ),
  },
  {
    title: "Pay by PromptPay, upload the slip",
    detail:
      "Scan the QR with any Thai banking app, then send a photo of the slip.",
    status: <span className={badge}>PromptPay</span>,
  },
  {
    title: "Staff approve, LINE sends your ticket",
    detail:
      "Someone at the transport desk checks the slip. Your boarding pass arrives as a LINE message.",
    status: <StatusStamp label="Confirmed" tone="success" />,
  },
];

export function HowItWorks() {
  return (
    <LandingSection
      id="how-it-works"
      intro="All a student needs is LINE and the banking app already on their phone."
      kicker="How it works"
      title="From the departure board to a seat in four steps"
    >
      <div className="overflow-hidden rounded-2xl border border-line bg-card">
        <div
          aria-hidden
          className="hidden grid-cols-[4.5rem_minmax(0,1fr)_8rem] gap-x-6 border-b border-line px-7 py-3 font-mono text-[10px] tracking-[0.16em] text-muted uppercase sm:grid"
        >
          <span>Step</span>
          <span>What happens</span>
          <span>Status</span>
        </div>
        <ol className="divide-y divide-dashed divide-line">
          {steps.map((step, index) => (
            <li
              className="grid grid-cols-[2.75rem_minmax(0,1fr)] gap-x-4 gap-y-4 px-5 py-6 sm:grid-cols-[4.5rem_minmax(0,1fr)_8rem] sm:items-center sm:gap-x-6 sm:px-7 sm:py-7"
              key={step.title}
            >
              <span className="font-mono text-[26px] leading-none font-medium text-brand-900 tabular-nums sm:text-[30px]">
                {String(index + 1).padStart(2, "0")}
              </span>
              <div>
                <h3 className="font-display text-[22px] leading-tight text-ink sm:text-2xl">
                  {step.title}
                </h3>
                <p className="mt-2 max-w-md text-[15px] leading-7 text-muted">
                  {step.detail}
                </p>
              </div>
              <div className="col-start-2 sm:col-start-auto">{step.status}</div>
            </li>
          ))}
        </ol>
      </div>
    </LandingSection>
  );
}
