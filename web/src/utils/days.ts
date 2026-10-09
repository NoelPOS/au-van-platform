const dayKeyFormat = new Intl.DateTimeFormat("en-CA", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});
const timeFormat = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
});
const weekdayFormat = new Intl.DateTimeFormat("en-GB", { weekday: "short" });
const monthFormat = new Intl.DateTimeFormat("en-GB", { month: "short" });
const shortDateFormat = new Intl.DateTimeFormat("en-GB", {
  weekday: "short",
  day: "numeric",
  month: "short",
});
const longDateFormat = new Intl.DateTimeFormat("en-GB", {
  weekday: "long",
  day: "numeric",
  month: "long",
});

export type Day = {
  key: string;
  weekday: string;
  dayNumber: number;
  month: string;
  relative: "Today" | "Tomorrow" | null;
};

export function dayKey(value: string | Date): string {
  return dayKeyFormat.format(new Date(value));
}

export function formatTime(value: string): string {
  return timeFormat.format(new Date(value));
}

export function formatShortDate(value: string): string {
  return shortDateFormat.format(new Date(value));
}

export function formatLongDate(value: string): string {
  return longDateFormat.format(new Date(value));
}

export function dayOf(key: string, now = new Date()): Day {
  const date = new Date(`${key}T12:00:00`);
  const tomorrow = new Date(now);
  tomorrow.setDate(now.getDate() + 1);
  const relative =
    key === dayKey(now) ? "Today" : key === dayKey(tomorrow) ? "Tomorrow" : null;
  return {
    key,
    weekday: weekdayFormat.format(date),
    dayNumber: date.getDate(),
    month: monthFormat.format(date),
    relative,
  };
}

export function groupByDay<T>(
  items: T[],
  instantOf: (item: T) => string,
): Map<string, T[]> {
  const sorted = [...items].sort(
    (left, right) =>
      Date.parse(instantOf(left)) - Date.parse(instantOf(right)),
  );
  const days = new Map<string, T[]>();
  for (const item of sorted) {
    const key = dayKey(instantOf(item));
    days.set(key, [...(days.get(key) ?? []), item]);
  }
  return days;
}

export function deadlinePhrase(value: string, now = new Date()): string {
  const time = formatTime(value);
  const relative = dayOf(dayKey(value), now).relative;
  if (relative === "Today") return `today at ${time}`;
  if (relative === "Tomorrow") return `tomorrow at ${time}`;
  return `${formatShortDate(value)} at ${time}`;
}
