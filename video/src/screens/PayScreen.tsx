import { useCurrentFrame } from "remotion";
import { QrMock } from "../components/QrMock";
import { Stamp } from "../components/Stamp";
import { progress } from "../motion";
import { color, font } from "../theme";
import { AppScreen, kicker } from "./AppChrome";

const uploadAt = 52;

export function PayScreen() {
  const frame = useCurrentFrame();
  const sent = progress(frame, uploadAt, 14);
  return (
    <AppScreen tabs={false}>
      <p style={kicker}>Seat B2 · 07:30 Mega Bangna</p>
      <p style={{ margin: "6px 0 18px", fontFamily: font.display, fontSize: 34, color: color.navy }}>
        Pay for your seat
      </p>
      <div style={{ padding: 22, borderRadius: 20, border: `1px solid ${color.line}`, background: color.card }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
          <span style={kicker}>How to pay</span>
          <span style={{ fontSize: 16, fontWeight: 600, color: color.navy }}>PromptPay</span>
        </div>
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", marginTop: 16 }}>
          <div style={{ padding: 12, borderRadius: 14, border: `1px solid ${color.line}`, background: "#fff" }}>
            <QrMock size={200} />
          </div>
          <span style={{ marginTop: 14, fontFamily: font.display, fontSize: 42, lineHeight: 1, color: color.navy }}>
            ฿35
          </span>
          <span style={{ marginTop: 4, fontSize: 15, color: color.muted }}>to AU-Van</span>
        </div>
      </div>
      <div
        style={{
          position: "relative",
          marginTop: 18,
          height: 112,
          borderRadius: 20,
          border: `2px dashed ${sent ? color.line : "rgba(48,65,161,0.45)"}`,
          background: color.card,
        }}
      >
        <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center", opacity: 1 - sent }}>
          <span style={{ fontSize: 18, fontWeight: 600, color: color.brand }}>Upload payment slip</span>
        </div>
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "flex",
            alignItems: "center",
            gap: 16,
            padding: "0 18px",
            opacity: sent,
          }}
        >
          <span
            style={{
              width: 56,
              height: 76,
              borderRadius: 8,
              border: `1px solid ${color.line}`,
              background: `linear-gradient(${color.successSoft} 0 34%, #fff 34%)`,
            }}
          />
          <span style={{ flex: 1 }}>
            <span style={{ display: "block", fontSize: 17, fontWeight: 500 }}>Slip sent</span>
            <span style={{ display: "block", marginTop: 4, fontSize: 14, color: color.muted }}>Staff check it next</span>
          </span>
          {frame >= uploadAt + 10 && <Stamp from={uploadAt + 10} label="In review" size={14} tone={color.brand} />}
        </div>
      </div>
      <p
        style={{
          margin: "18px 0 0",
          padding: "12px 14px",
          borderRadius: 14,
          border: "1px solid rgba(217,154,43,0.5)",
          background: color.warningSoft,
          fontSize: 14,
          lineHeight: 1.4,
          color: color.warning,
        }}
      >
        Sample QR for the demo. It pays no one, so don’t transfer real money.
      </p>
    </AppScreen>
  );
}
