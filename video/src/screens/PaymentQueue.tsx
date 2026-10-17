import { color, font } from "../theme";

const proofs = [
  { fare: 35, reference: "AUV-261020-7KQ2M4XR", name: "Ploy S.", to: "Mega Bangna", time: "07:30", sent: "just now" },
  { fare: 60, reference: "AUV-261020-3HD8P2WQ", name: "Nattapong K.", to: "Siam Paragon", time: "09:00", sent: "2 min ago" },
  { fare: 30, reference: "AUV-261020-9TR4X6LB", name: "Mina T.", to: "BTS Udom Suk", time: "13:15", sent: "4 min ago" },
  { fare: 70, reference: "AUV-261020-5MW7C3NJ", name: "Arthit P.", to: "Future Park", time: "17:45", sent: "9 min ago" },
];

function Notch({ edge }: { edge: "top" | "bottom" }) {
  return (
    <span
      style={{
        position: "absolute",
        left: 96,
        [edge]: -9,
        width: 18,
        height: 18,
        marginLeft: -9,
        borderRadius: 9,
        border: `1px solid ${color.line}`,
        background: color.paper,
      }}
    />
  );
}

export function PaymentQueue({ approved }: { approved: number }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
      {proofs.map((proof, index) => {
        const first = index === 0;
        const gone = first ? approved : 0;
        return (
          <div
            key={proof.reference}
            style={{
              position: "relative",
              display: "flex",
              height: 128 * (1 - gone),
              marginBottom: first ? -14 * gone : 0,
              opacity: 1 - gone,
              borderRadius: 18,
              border: `1px solid ${color.line}`,
              background: color.card,
              boxShadow: first ? `0 0 0 2px ${color.brandBright}` : "none",
            }}
          >
            <div style={{ width: 96, display: "flex", flexDirection: "column", justifyContent: "center", paddingLeft: 20 }}>
              <span style={{ fontFamily: font.display, fontSize: 32, fontWeight: 300, lineHeight: 1, color: color.navy }}>
                {proof.fare}
              </span>
              <span style={{ marginTop: 8, fontSize: 11, fontWeight: 600, letterSpacing: "0.14em", color: color.muted }}>
                THB
              </span>
            </div>
            <Notch edge="top" />
            <Notch edge="bottom" />
            <div style={{ flex: 1, padding: "18px 20px", borderLeft: `1px dashed ${color.line}` }}>
              <p style={{ margin: 0, fontFamily: font.mono, fontSize: 14, fontWeight: 500, color: color.navyMid }}>
                {proof.reference}
              </p>
              <p style={{ margin: "6px 0 0", fontSize: 17, fontWeight: 500 }}>{proof.name}</p>
              <p style={{ margin: "5px 0 0", fontSize: 14, color: color.muted }}>
                {`Assumption University → ${proof.to}`}
              </p>
              <p style={{ margin: "6px 0 0", fontSize: 13, color: color.muted }}>
                {`20 Oct ${proof.time} · Sent ${proof.sent}`}
              </p>
            </div>
          </div>
        );
      })}
    </div>
  );
}
