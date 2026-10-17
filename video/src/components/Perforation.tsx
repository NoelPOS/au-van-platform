import { color } from "../theme";

export function Perforation({
  notch = 24,
  background = color.paper,
}: {
  notch?: number;
  background?: string;
}) {
  const hole = {
    position: "absolute" as const,
    top: 0,
    width: notch,
    height: notch,
    borderRadius: "50%",
    border: `1px solid ${color.line}`,
    background,
  };
  return (
    <div style={{ position: "relative", height: notch }}>
      <span style={{ ...hole, left: -notch / 2 }} />
      <span
        style={{
          position: "absolute",
          left: notch,
          right: notch,
          top: notch / 2,
          borderTop: `2px dashed ${color.line}`,
        }}
      />
      <span style={{ ...hole, right: -notch / 2 }} />
    </div>
  );
}
