import type { ComponentType } from "react";
import { AbsoluteFill, Sequence, useCurrentFrame } from "remotion";
import { PhoneFrame, Screenshot } from "../components/Devices";
import { progress, rise } from "../motion";
import { PassScreen } from "../screens/PassScreen";
import { PayScreen } from "../screens/PayScreen";
import { SeatScreen } from "../screens/SeatScreen";
import { TripsScreen } from "../screens/TripsScreen";
import { color, font } from "../theme";

type Step = { title: string; detail: string; shot: string | null; Mock: ComponentType };

const steps: Step[] = [
  {
    title: "Pick a departure.",
    detail: "Today’s vans and the seats left, right inside LINE.",
    shot: "student-trips.webp",
    Mock: TripsScreen,
  },
  {
    title: "Hold a seat.",
    detail: "It stays yours for five minutes while you pay.",
    shot: "seat-picker.webp",
    Mock: SeatScreen,
  },
  {
    title: "Pay by PromptPay.",
    detail: "Scan the QR with any Thai bank app, then upload the slip.",
    shot: null,
    Mock: PayScreen,
  },
  {
    title: "Ride with your pass.",
    detail: "Staff approve the slip and the boarding pass arrives in LINE.",
    shot: "boarding-pass.webp",
    Mock: PassScreen,
  },
];

const stepFrames = 112;
const slideFrames = 14;

function PhoneScreen({ step, last, screenshots }: { step: Step; last: boolean; screenshots: boolean }) {
  const frame = useCurrentFrame();
  const enter = progress(frame, 0, slideFrames);
  const leave = last ? 0 : progress(frame, stepFrames, slideFrames);
  return (
    <AbsoluteFill
      style={{ background: color.paper, transform: `translateX(${(1 - enter) * 100 - leave * 35}%)` }}
    >
      {screenshots && step.shot ? <Screenshot file={step.shot} /> : <step.Mock />}
    </AbsoluteFill>
  );
}

function Caption({ step, index }: { step: Step; index: number }) {
  const frame = useCurrentFrame();
  const fade = index === steps.length - 1 ? 1 : 1 - progress(frame, stepFrames - 12, 10);
  return (
    <div style={{ opacity: fade }}>
      <p style={{ margin: 0, fontFamily: font.mono, fontSize: 20, letterSpacing: "0.18em", color: color.muted, ...rise(frame, 4, 16) }}>
        {`STEP ${String(index + 1).padStart(2, "0")} / 04`}
      </p>
      <p
        style={{
          margin: "26px 0 0",
          fontFamily: font.display,
          fontSize: 96,
          lineHeight: 1.02,
          letterSpacing: "-0.025em",
          color: color.navy,
          ...rise(frame, 8),
        }}
      >
        {step.title}
      </p>
      <p style={{ margin: "28px 0 0", maxWidth: 680, fontSize: 36, lineHeight: 1.4, color: color.muted, ...rise(frame, 14) }}>
        {step.detail}
      </p>
    </div>
  );
}

export function StudentFlow({ screenshots }: { screenshots: boolean }) {
  const frame = useCurrentFrame();
  const current = Math.min(steps.length - 1, Math.floor(frame / stepFrames));
  return (
    <AbsoluteFill
      style={{
        flexDirection: "row",
        alignItems: "center",
        gap: 150,
        paddingLeft: 250,
        background: color.paper,
        fontFamily: font.sans,
      }}
    >
      <PhoneFrame>
        {steps.map((step, index) => (
          <Sequence
            durationInFrames={index === steps.length - 1 ? undefined : stepFrames + slideFrames}
            from={index * stepFrames}
            key={step.title}
            layout="none"
          >
            <PhoneScreen last={index === steps.length - 1} screenshots={screenshots} step={step} />
          </Sequence>
        ))}
      </PhoneFrame>
      <div style={{ position: "relative", width: 760, height: 440 }}>
        <div style={{ display: "flex", gap: 10 }}>
          {steps.map((step, index) => (
            <span
              key={step.title}
              style={{ width: 64, height: 4, borderRadius: 2, background: index <= current ? color.amber : color.line }}
            />
          ))}
        </div>
        {steps.map((step, index) => (
          <Sequence
            durationInFrames={index === steps.length - 1 ? undefined : stepFrames}
            from={index * stepFrames}
            key={step.title}
            layout="none"
          >
            <div style={{ position: "absolute", inset: "52px 0 0" }}>
              <Caption index={index} step={step} />
            </div>
          </Sequence>
        ))}
      </div>
    </AbsoluteFill>
  );
}
