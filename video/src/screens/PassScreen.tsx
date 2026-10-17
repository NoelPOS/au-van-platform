import { useCurrentFrame } from "remotion";
import { BoardingPass } from "../components/BoardingPass";
import { Stamp } from "../components/Stamp";
import { progress } from "../motion";
import { color, font } from "../theme";
import { AppScreen, kicker } from "./AppChrome";

function Timeline({ confirmed }: { confirmed: boolean }) {
  const events = [
    ["07:12", "Seat B2 held"],
    ["07:14", "Slip sent"],
    ["07:16", confirmed ? "Payment approved" : "Waiting for staff"],
  ];
  return (
    <div style={{ marginTop: 22, display: "flex", flexDirection: "column", gap: 10 }}>
      {events.map(([time, text], index) => (
        <p key={time} style={{ margin: 0, display: "flex", alignItems: "center", gap: 12, fontSize: 15 }}>
          <span style={{ width: 8, height: 8, borderRadius: 4, background: index < 2 || confirmed ? color.success : color.line }} />
          <span style={{ fontFamily: font.mono, fontSize: 13, color: color.muted }}>{time}</span>
          {text}
        </p>
      ))}
    </div>
  );
}

export function PassScreen({ confirmAt = 26 }: { confirmAt?: number }) {
  const frame = useCurrentFrame();
  const messageAt = confirmAt + 28;
  const message = progress(frame, messageAt, 16);
  const shake = frame >= confirmAt + 2 && frame < confirmAt + 8 ? Math.sin(frame * 3) * 3 : 0;
  return (
    <AppScreen>
      <div
        style={{
          position: "absolute",
          bottom: 100,
          left: 14,
          right: 14,
          zIndex: 5,
          padding: "11px 15px",
          borderRadius: 20,
          background: "rgba(255,253,248,0.97)",
          border: `1px solid ${color.line}`,
          boxShadow: "0 18px 30px -18px rgba(20,23,43,0.45)",
          opacity: message,
          transform: `translateY(${(1 - message) * 40}px)`,
        }}
      >
        <p style={{ margin: 0, display: "flex", justifyContent: "space-between", fontSize: 13, color: color.muted }}>
          <span style={{ fontWeight: 600, color: color.success }}>LINE · AU-Van</span>
          <span>now</span>
        </p>
        <p style={{ margin: "4px 0 0", fontSize: 15, lineHeight: 1.3 }}>
          Your payment is approved and your booking is confirmed.
        </p>
      </div>
      <p style={kicker}>Tickets</p>
      <p style={{ margin: "6px 0 20px", fontFamily: font.display, fontSize: 34, color: color.navy }}>
        Your next ride
      </p>
      <div style={{ transform: `translateX(${shake}px)` }}>
        <BoardingPass
          stamp={
            frame >= confirmAt ? (
              <Stamp from={confirmAt} key="confirmed" label="Confirmed" size={16} tone={color.success} />
            ) : (
              <Stamp from={-30} key="review" label="In review" size={16} tone={color.brand} />
            )
          }
        />
      </div>
      <Timeline confirmed={frame >= confirmAt} />
    </AppScreen>
  );
}
