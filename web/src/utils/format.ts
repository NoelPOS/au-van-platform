export function formatDeparture(value: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function formatFare(amount: number): string {
  return `${amount.toFixed(2)} THB`;
}

export function formatAgo(value: string, now = Date.now()): string {
  const minutes = Math.floor((now - new Date(value).getTime()) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return `${Math.floor(hours / 24)} d ago`;
}

export function seatCount(seats: number): string {
  return `${seats} ${seats === 1 ? "seat" : "seats"}`;
}

export function formatBaht(amount: number): string {
  return `฿${Number.isInteger(amount) ? amount : amount.toFixed(2)}`;
}
