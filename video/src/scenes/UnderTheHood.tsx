import { AbsoluteFill, useCurrentFrame } from "remotion";
import { progress, rise } from "../motion";
import { color, font } from "../theme";

const specs = [
  ["PostgreSQL unique constraint", "8 students race, 1 seat, 1 winner"],
  ["Idempotency keys", "No double bookings on retry"],
  ["Transactional outbox", "LINE messages retried, never lost"],
  ["Server-sent events + LISTEN/NOTIFY", "Live updates without a refresh"],
  ["Stack", "Spring Boot 4 · Java 21 · React 19 · Playwright · CI/CD"],
];

const firstAt = 34;
const every = 44;
const typeSpeed = 1.6;

function Spec({ index, title, detail }: { index: number; title: string; detail: string }) {
  const frame = useCurrentFrame();
  const start = firstAt + index * every;
  const typed = Math.max(0, Math.floor((frame - start) * typeSpeed));
  const doneAt = start + Math.ceil(title.length / typeSpeed);
  const typing = typed < title.length;
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "76px auto 1fr auto",
        alignItems: "baseline",
        padding: "28px 0",
        borderTop: "1px dashed rgba(255,255,255,0.2)",
        opacity: frame >= start ? 1 : 0,
      }}
    >
      <span style={{ fontFamily: font.mono, fontSize: 20, color: "rgba(227,231,248,0.55)" }}>
        {String(index + 1).padStart(2, "0")}
      </span>
      <span style={{ fontFamily: font.mono, fontSize: 32, fontWeight: 500, color: color.paper }}>
        {title.slice(0, typed)}
        {typing && <span style={{ color: color.amber }}>▍</span>}
      </span>
      <span
        style={{
          margin: "0 24px",
          borderBottom: "2px dotted rgba(227,231,248,0.3)",
          opacity: progress(frame, doneAt - 4, 10),
        }}
      />
      <span style={{ fontSize: 30, color: color.brandSoft, ...rise(frame, doneAt, 14) }}>{detail}</span>
    </div>
  );
}

export function UnderTheHood() {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill
      style={{ justifyContent: "center", padding: "0 150px", background: color.navy, fontFamily: font.sans }}
    >
      <p style={{ margin: 0, fontFamily: font.mono, fontSize: 20, letterSpacing: "0.2em", color: color.amber, ...rise(frame, 4, 14) }}>
        UNDER THE HOOD
      </p>
      <p
        style={{
          margin: "22px 0 56px",
          fontFamily: font.display,
          fontSize: 76,
          letterSpacing: "-0.025em",
          color: color.paper,
          ...rise(frame, 10),
        }}
      >
        Built for the rush before the <em style={{ color: color.amber }}>07:30 van.</em>
      </p>
      {specs.map(([title, detail], index) => (
        <Spec detail={detail} index={index} key={title} title={title} />
      ))}
    </AbsoluteFill>
  );
}
