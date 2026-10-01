export function formatDeparture(value: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function formatFare(amount: number): string {
  return `${amount.toFixed(2)} THB`;
}

const bangkok = "Asia/Bangkok";

export function formatTime(value: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: bangkok,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(new Date(value));
}

export function formatDay(value: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: bangkok,
    weekday: "short",
    day: "numeric",
    month: "short",
  }).format(new Date(value));
}

export function formatWhen(value: string): string {
  return `${formatDay(value)} · ${formatTime(value)}`;
}

export function formatAgo(value: string, now = Date.now()): string {
  const minutes = Math.floor((now - new Date(value).getTime()) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return `${Math.floor(hours / 24)} d ago`;
}
