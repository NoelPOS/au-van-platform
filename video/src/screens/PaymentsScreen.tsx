import { useCurrentFrame } from "remotion";
import { progress } from "../motion";
import { color, font } from "../theme";
import { PaymentQueue } from "./PaymentQueue";
import { SlipReview } from "./SlipReview";

const tabs = ["Overview", "Insights", "Trips", "Payments"];

export function PaymentsScreen({ approveAt }: { approveAt: number }) {
  const frame = useCurrentFrame();
  const approved = frame >= approveAt;
  const waiting = approved ? 3 : 4;
  return (
    <div style={{ position: "absolute", inset: 0, background: color.paper, fontFamily: font.sans, color: color.ink }}>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 36,
          height: 64,
          padding: "0 32px",
          borderBottom: `1px solid ${color.line}`,
          background: color.card,
          fontSize: 15,
          fontWeight: 500,
        }}
      >
        <span style={{ fontFamily: font.display, fontSize: 24, color: color.navy }}>
          AU<span style={{ color: color.amber }}>·</span>Van
          <span style={{ marginLeft: 10, fontFamily: font.mono, fontSize: 12, letterSpacing: "0.16em", color: color.muted }}>
            OPERATIONS
          </span>
        </span>
        {tabs.map((tab) => (
          <span
            key={tab}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 8,
              padding: "21px 0",
              color: tab === "Payments" ? color.navy : color.muted,
              borderBottom: `3px solid ${tab === "Payments" ? color.amber : "transparent"}`,
            }}
          >
            {tab}
            {tab === "Payments" && (
              <span style={{ padding: "1px 7px", borderRadius: 9, background: color.amber, fontFamily: font.mono, fontSize: 12, color: color.ink }}>
                {waiting}
              </span>
            )}
          </span>
        ))}
      </div>
      <div style={{ padding: "26px 32px" }}>
        <p style={{ margin: 0, fontFamily: font.display, fontSize: 36, color: color.navy }}>Payment review</p>
        <p style={{ margin: "4px 0 0", fontSize: 16, color: color.muted }}>
          {`${waiting} slips waiting`}
        </p>
        <div style={{ display: "grid", gridTemplateColumns: "430px 1fr", alignItems: "start", gap: 24, marginTop: 22 }}>
          <PaymentQueue approved={progress(frame, approveAt + 24, 16)} />
          <SlipReview approvedAt={approved ? approveAt : null} pressed={frame >= approveAt - 4 && frame < approveAt + 3} />
        </div>
      </div>
    </div>
  );
}
