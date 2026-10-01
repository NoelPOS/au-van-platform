import { toBangkokInputs } from "./dates";

const dayMs = 86_400_000;

function format(options: Intl.DateTimeFormatOptions) {
  const formatter = new Intl.DateTimeFormat("en-GB", {
    timeZone: "UTC",
    ...options,
  });
  return (day: string) => formatter.format(new Date(`${day}T00:00:00Z`));
}

const weekdayAndDate = format({ weekday: "short", day: "numeric" });
export const shortDay = format({ weekday: "short", day: "numeric", month: "short" });
const withYear = format({
  weekday: "short",
  day: "numeric",
  month: "short",
  year: "numeric",
});
export const longDay = format({ weekday: "long", day: "numeric", month: "long" });
const monthAndYear = format({ month: "long", year: "numeric" });

export function monthTitle(month: string): string {
  return monthAndYear(`${month}-01`);
}

export function bangkokToday(): string {
  return toBangkokInputs(new Date().toISOString()).date;
}

export function addDays(day: string, days: number): string {
  return new Date(Date.parse(`${day}T00:00:00Z`) + days * dayMs)
    .toISOString()
    .slice(0, 10);
}

export function nextDays(from: string, count: number): string[] {
  return Array.from({ length: count }, (_, index) => addDays(from, index));
}

export function chipLabel(day: string, today: string): string {
  if (day === today) return "Today";
  if (day === addDays(today, 1)) return "Tomorrow";
  if (day > today && day < addDays(today, 14)) return weekdayAndDate(day);
  return shortDay(day);
}

export function dayHeading(day: string, today: string): string {
  const date = day.slice(0, 4) === today.slice(0, 4) ? shortDay(day) : withYear(day);
  if (day === today) return `Today · ${date}`;
  if (day === addDays(today, 1)) return `Tomorrow · ${date}`;
  return date;
}

export function addMonths(month: string, months: number): string {
  const date = new Date(`${month}-01T00:00:00Z`);
  date.setUTCMonth(date.getUTCMonth() + months);
  return date.toISOString().slice(0, 7);
}

export function monthGrid(month: string): (string | null)[] {
  const first = new Date(`${month}-01T00:00:00Z`);
  const leading = (first.getUTCDay() + 6) % 7;
  const length = new Date(
    Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0),
  ).getUTCDate();
  return [
    ...Array<null>(leading).fill(null),
    ...Array.from(
      { length },
      (_, index) => `${month}-${String(index + 1).padStart(2, "0")}`,
    ),
  ];
}
