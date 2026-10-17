import type { ReactNode } from "react";
import { color, font } from "../theme";
import { Perforation } from "./Perforation";

const small = {
  margin: 0,
  fontFamily: font.mono,
  fontSize: 11,
  letterSpacing: "0.16em",
  textTransform: "uppercase" as const,
  color: color.muted,
};

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p style={small}>{label}</p>
      <p style={{ margin: "5px 0 0", fontFamily: font.mono, fontSize: 17, fontWeight: 500 }}>{value}</p>
    </div>
  );
}

export function BoardingPass({ stamp }: { stamp: ReactNode }) {
  return (
    <div
      style={{
        overflow: "hidden",
        borderRadius: 24,
        border: `1px solid ${color.line}`,
        background: color.card,
        fontFamily: font.sans,
        color: color.ink,
      }}
    >
      <div style={{ padding: "22px 22px 0" }}>
        <div style={{ display: "flex", justifyContent: "space-between" }}>
          <p style={{ ...small, letterSpacing: "0.2em" }}>Boarding pass</p>
          <p style={{ ...small, letterSpacing: 0, fontSize: 12 }}>AUV-261020-7KQ2M4XR</p>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "14px 1fr", columnGap: 14, marginTop: 18 }}>
          <span style={{ display: "flex", flexDirection: "column", alignItems: "center", padding: "24px 0 20px" }}>
            <span style={{ width: 11, height: 11, borderRadius: 6, background: color.brandBright }} />
            <span style={{ flex: 1, borderLeft: "2px dotted rgba(63,83,201,0.5)" }} />
            <span style={{ width: 11, height: 11, borderRadius: 6, border: `2px solid ${color.brandBright}`, boxSizing: "border-box" }} />
          </span>
          <div>
            <p style={small}>From</p>
            <p style={{ margin: "3px 0 0", fontSize: 20 }}>Assumption University</p>
            <p style={{ ...small, marginTop: 18 }}>To</p>
            <p style={{ margin: "3px 0 0", fontFamily: font.display, fontSize: 34, lineHeight: 1.1, color: color.navy }}>
              Mega Bangna
            </p>
          </div>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", padding: "22px 0 18px" }}>
          <Detail label="Date" value="Tue 20 Oct" />
          <Detail label="Departs" value="07:30" />
          <Detail label="Fare" value="฿35" />
        </div>
      </div>
      <Perforation background={color.paper} notch={26} />
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", padding: "12px 22px 24px" }}>
        <div>
          <p style={small}>Seat</p>
          <p style={{ margin: "3px 0 0", fontFamily: font.display, fontSize: 46, lineHeight: 1, color: color.navy }}>B2</p>
          <p style={{ margin: "8px 0 0", fontSize: 15, color: color.muted }}>Ploy S.</p>
        </div>
        <div style={{ paddingBottom: 6 }}>{stamp}</div>
      </div>
    </div>
  );
}
