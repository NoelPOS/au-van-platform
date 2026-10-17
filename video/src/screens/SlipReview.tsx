import type { ReactNode } from "react";
import { Stamp } from "../components/Stamp";
import { color, font } from "../theme";

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", padding: "9px 0", borderTop: `1px dashed ${color.line}`, fontSize: 15 }}>
      <span style={{ color: color.muted }}>{label}</span>
      <span style={{ fontFamily: font.mono }}>{value}</span>
    </div>
  );
}

function Slip({ children }: { children: ReactNode }) {
  return (
    <div style={{ position: "relative", width: 250, borderRadius: 14, border: `1px solid ${color.line}`, background: "#fff", overflow: "hidden" }}>
      <div style={{ padding: "16px 18px", background: color.successSoft, color: color.success, fontSize: 15, fontWeight: 600 }}>
        Transfer successful
      </div>
      <div style={{ padding: "16px 18px 20px" }}>
        <p style={{ margin: 0, fontFamily: font.display, fontSize: 36, color: color.ink }}>฿35.00</p>
        <Row label="To" value="AU-Van" />
        <Row label="From" value="x-1234" />
        <Row label="Date" value="20 Oct 06:58" />
      </div>
      {children}
    </div>
  );
}

export function SlipReview({ pressed, approvedAt }: { pressed: boolean; approvedAt: number | null }) {
  return (
    <div style={{ display: "flex", gap: 28, padding: 24, borderRadius: 20, border: `1px solid ${color.line}`, background: color.card }}>
      <Slip>
        {approvedAt !== null && (
          <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center" }}>
            <Stamp from={approvedAt} label="Approved" size={22} tone={color.success} />
          </div>
        )}
      </Slip>
      <div style={{ flex: 1, display: "flex", flexDirection: "column" }}>
        <p style={{ margin: 0, fontFamily: font.mono, fontSize: 13, letterSpacing: "0.16em", color: color.muted }}>
          PAYMENT SLIP
        </p>
        <p style={{ margin: "8px 0 0", fontFamily: font.mono, fontSize: 15, color: color.navyMid }}>AUV-261020-7KQ2M4XR</p>
        <p style={{ margin: "6px 0 0", fontSize: 22, fontWeight: 500 }}>Ploy S. · Seat B2</p>
        <div style={{ marginTop: 14 }}>
          <Row label="Fare" value="฿35" />
          <Row label="Departs" value="20 Oct 07:30" />
        </div>
        <div style={{ display: "flex", gap: 12, marginTop: "auto" }}>
          <span style={{ flex: 1, height: 50, borderRadius: 25, display: "grid", placeItems: "center", border: `1px solid ${color.line}`, fontSize: 16, fontWeight: 600 }}>
            Send back
          </span>
          <span
            style={{
              flex: 1,
              height: 50,
              borderRadius: 25,
              display: "grid",
              placeItems: "center",
              background: pressed ? color.navy : color.brand,
              color: "#fff",
              fontSize: 16,
              fontWeight: 600,
              transform: pressed ? "scale(0.97)" : "none",
            }}
          >
            Approve
          </span>
        </div>
      </div>
    </div>
  );
}
