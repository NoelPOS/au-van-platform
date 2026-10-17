import { AbsoluteFill, interpolate, useCurrentFrame } from "remotion";
import { BrowserFrame, PhoneFrame, Screenshot } from "../components/Devices";
import { progress, rise } from "../motion";
import { PassScreen } from "../screens/PassScreen";
import { PaymentsScreen } from "../screens/PaymentsScreen";
import { color, font } from "../theme";

const approveAt = 72;
const liveAt = 84;
const confirmAt = 108;

function Cursor() {
  const frame = useCurrentFrame();
  const move = progress(frame, 30, 36);
  const x = interpolate(move, [0, 1], [1060, 990]);
  const y = interpolate(move, [0, 1], [660, 482]);
  const press = frame >= approveAt - 4 && frame < approveAt + 3 ? 0.88 : 1;
  return (
    <svg
      height={34}
      style={{ position: "absolute", left: x, top: y, transform: `scale(${press})`, opacity: progress(frame, 20, 10) }}
      viewBox="0 0 24 34"
      width={24}
    >
      <path d="M2 2 L2 28 L9 21 L14 32 L19 30 L14 19 L23 19 Z" fill={color.ink} stroke="#fff" strokeWidth={2} />
    </svg>
  );
}

function LiveLink() {
  const frame = useCurrentFrame();
  const travel = progress(frame, liveAt, 22);
  const shown = frame >= liveAt - 6 ? 1 : 0;
  return (
    <div style={{ position: "relative", width: 150, opacity: shown }}>
      <p style={{ margin: "0 0 14px", textAlign: "center", fontFamily: font.mono, fontSize: 15, letterSpacing: "0.2em", color: color.amber }}>
        LIVE
      </p>
      <div style={{ borderTop: `2px dashed ${color.line}` }} />
      <span
        style={{
          position: "absolute",
          bottom: -8,
          left: `calc(${travel * 100}% - 7px)`,
          width: 14,
          height: 14,
          borderRadius: 7,
          background: color.amber,
          opacity: travel < 1 ? 1 : 0,
        }}
      />
    </div>
  );
}

export function StaffReview({ screenshots }: { screenshots: boolean }) {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{ padding: "64px 84px", background: color.paper, fontFamily: font.sans }}>
      <p style={{ margin: 0, fontFamily: font.display, fontSize: 60, letterSpacing: "-0.02em", color: color.navy, ...rise(frame, 10) }}>
        Staff approve the slip.{" "}
        <em style={{ color: color.brand }}>The student’s ticket updates live.</em>
      </p>
      <div style={{ display: "flex", alignItems: "center", gap: 34, marginTop: 44 }}>
        <div style={{ position: "relative", ...rise(frame, 4, 40) }}>
          <BrowserFrame address="auvan.duckdns.org/admin/payments" width={1120}>
            {screenshots ? <Screenshot file="admin-payments.webp" /> : <PaymentsScreen approveAt={approveAt} />}
          </BrowserFrame>
          {!screenshots && <Cursor />}
        </div>
        <LiveLink />
        <div style={{ width: 368, height: 776, ...rise(frame, 10, 40) }}>
          <div style={{ transform: "scale(0.8)", transformOrigin: "top left" }}>
            <PhoneFrame>
              <PassScreen confirmAt={confirmAt} />
            </PhoneFrame>
          </div>
        </div>
      </div>
    </AbsoluteFill>
  );
}
