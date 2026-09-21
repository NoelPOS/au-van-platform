export function formatDeparture(value: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function formatFare(amount: number): string {
  return `${amount.toFixed(2)} THB`;
}
