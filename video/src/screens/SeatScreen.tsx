import type { CSSProperties } from "react";
import { useCurrentFrame } from "remotion";
import { progress } from "../motion";
import { color, font } from "../theme";
import { AppScreen, kicker } from "./AppChrome";

const taken = ["A1", "A3", "C2", "D3"];
const heldByOthers = ["B3", "D1"];
const picked = "B2";
const pickAt = 20;
const holdAt = 44;

const looks: Record<string, CSSProperties> = {
  available: { border: "1px solid rgba(20,23,43,0.2)", background: color.card },
  selected: { background: color.brand, color: "#fff", boxShadow: `0 3px 0 ${color.navy}` },
  mine: { border: `2px solid ${color.brandBright}`, background: color.brandFaint, color: color.navyMid },
  held: {
    border: `1px solid ${color.line}`,
    color: "rgba(94,97,120,0.7)",
    background: `repeating-linear-gradient(135deg, ${color.line} 0 2px, transparent 2px 7px)`,
  },
  taken: { background: color.line, color: "rgba(94,97,120,0.7)" },
};

function lookOf(seat: string, frame: number) {
  if (seat === picked && frame >= holdAt) return looks.mine;
  if (seat === picked && frame >= pickAt) return looks.selected;
  if (taken.includes(seat)) return looks.taken;
  if (heldByOthers.includes(seat)) return looks.held;
  return looks.available;
}

function countdown(frame: number) {
  const left = 300 - Math.max(0, Math.floor((frame - holdAt) / 30));
  return `${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`;
}

function Van({ frame }: { frame: number }) {
  return (
    <div
      style={{
        position: "relative",
        width: 282,
        margin: "0 auto",
        padding: "112px 36px 30px",
        border: `2.5px solid ${color.navy}`,
        borderRadius: "70px 70px 22px 22px",
        background: color.card,
      }}
    >
      <span style={{ position: "absolute", top: 34, left: 28, right: 28, height: 18, borderRadius: "50% 50% 8px 8px", background: color.brandSoft }} />
      <span style={{ position: "absolute", top: 66, right: 36, width: 62, height: 34, borderRadius: 8, background: color.navy, color: color.card, fontSize: 9, fontWeight: 600, letterSpacing: "0.14em", display: "grid", placeItems: "center" }}>
        DRIVER
      </span>
      <span style={{ position: "absolute", left: 3, top: 112, width: 6, height: 136, borderRadius: 3, background: color.amber }} />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 62px)", gap: 12 }}>
        {["A", "B", "C", "D"].flatMap((row) =>
          [1, 2, 3].map((column) => {
            const seat = `${row}${column}`;
            return (
              <span
                key={seat}
                style={{
                  display: "grid",
                  placeItems: "center",
                  height: 62,
                  borderRadius: "14px 14px 7px 7px",
                  fontFamily: font.mono,
                  fontSize: 16,
                  fontWeight: 500,
                  ...lookOf(seat, frame),
                }}
              >
                {seat}
              </span>
            );
          }),
        )}
      </div>
    </div>
  );
}

function HoldBanner({ frame }: { frame: number }) {
  const held = progress(frame, holdAt, 14);
  return (
    <div
      style={{
        padding: "14px 18px",
        borderRadius: 18,
        border: "1px solid rgba(217,154,43,0.4)",
        background: color.warningSoft,
        opacity: held,
        transform: `translateY(${(1 - held) * 16}px)`,
      }}
    >
      <p style={{ margin: 0, fontSize: 17 }}>
        Seat B2 held for{" "}
        <span style={{ fontFamily: font.mono, fontWeight: 600 }}>{countdown(frame)}</span>
      </p>
      <div style={{ marginTop: 9, height: 5, borderRadius: 3, background: "rgba(217,154,43,0.2)" }}>
        <div
          style={{
            width: `${100 - Math.max(0, frame - holdAt) / 90}%`,
            height: "100%",
            borderRadius: 3,
            background: color.amber,
          }}
        />
      </div>
    </div>
  );
}

export function HoldOverlay() {
  const frame = useCurrentFrame();
  if (frame < holdAt) return null;
  return (
    <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 82, padding: "8px 16px", background: color.card, borderTop: `1px solid ${color.line}` }}>
      <HoldBanner frame={frame} />
    </div>
  );
}

export function SeatScreen() {
  const frame = useCurrentFrame();
  const pressed = frame >= holdAt - 4 && frame < holdAt;
  return (
    <AppScreen tabs={false}>
      <p style={kicker}>09:00 · Sun 18 Oct</p>
      <p style={{ margin: "6px 0 18px", fontFamily: font.display, fontSize: 34, color: color.navy }}>
        Siam Paragon
      </p>
      <Van frame={frame} />
      <div style={{ marginTop: 20 }}>
        <HoldBanner frame={frame} />
      </div>
      <div
        style={{
          position: "absolute",
          left: 26,
          right: 26,
          bottom: 30,
          height: 58,
          borderRadius: 29,
          display: "grid",
          placeItems: "center",
          fontSize: 18,
          fontWeight: 600,
          color: "#fff",
          background: pressed ? color.navy : color.brand,
          opacity: frame >= pickAt ? 1 : 0.4,
        }}
      >
        {frame >= holdAt ? "Continue to payment" : "Hold seat B2"}
      </div>
    </AppScreen>
  );
}
