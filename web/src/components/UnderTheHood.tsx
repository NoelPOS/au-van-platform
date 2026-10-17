import { LandingSection } from "./LandingSection";

const stack = [
  ["API", "Spring Boot 4 · Java 21"],
  ["Web", "React 19 · Vite · Tailwind"],
  ["Database", "PostgreSQL 17"],
  ["Tests", "JUnit · Testcontainers · Vitest · Playwright"],
];

const guarantees = [
  {
    title: "No double booking",
    detail:
      "A PostgreSQL unique constraint on each seat claim decides who gets a seat. A test races 8 students for one seat, and exactly one ends up holding it.",
  },
  {
    title: "Safe retries",
    detail:
      "Booking requests carry an idempotency key, so a double tap or a dropped connection never books twice.",
  },
  {
    title: "Messages that arrive",
    detail:
      "LINE messages go into a transactional outbox in the same commit as the booking, then send with retries and backoff.",
  },
  {
    title: "Live screens",
    detail:
      "Student and staff screens update over server-sent events, fanned out across API instances with PostgreSQL LISTEN/NOTIFY.",
  },
  {
    title: "Shipping",
    detail:
      "CI runs unit, integration and Playwright end-to-end tests. Every green merge to main deploys.",
  },
];

export function UnderTheHood() {
  return (
    <LandingSection
      dark
      id="under-the-hood"
      intro="A modular monolith built to keep seat counts right when many students tap at once."
      kicker="Under the hood"
      title="Built for the rush before the 07:30 van"
    >
      <dl className="max-w-2xl font-mono text-sm">
        {stack.map(([label, value]) => (
          <div className="flex items-baseline gap-3 py-2" key={label}>
            <dt className="text-[11px] tracking-[0.16em] text-accent uppercase">
              {label}
            </dt>
            <span
              aria-hidden
              className="min-w-4 flex-1 border-b border-dotted border-brand-100/30"
            />
            <dd className="text-right text-paper">{value}</dd>
          </div>
        ))}
      </dl>
      <ol className="mt-14 grid gap-x-12 gap-y-10 md:grid-cols-2">
        {guarantees.map((item, index) => (
          <li className="border-t border-dashed border-white/20 pt-5" key={item.title}>
            <p className="font-mono text-[11px] tracking-[0.16em] text-brand-100/60 uppercase">
              {`${String(index + 1).padStart(2, "0")} · ${item.title}`}
            </p>
            <p className="mt-3 text-[15px] leading-7 text-brand-100">
              {item.detail}
            </p>
          </li>
        ))}
      </ol>
    </LandingSection>
  );
}
