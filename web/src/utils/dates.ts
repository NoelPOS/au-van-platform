const zone = "Asia/Bangkok";
// Thailand keeps UTC+7 all year, so a fixed offset converts form input exactly.
const offsetMs = 7 * 60 * 60_000;

const day = new Intl.DateTimeFormat("en-GB", {
  timeZone: zone,
  weekday: "short",
  day: "numeric",
  month: "short",
});

const time = new Intl.DateTimeFormat("en-GB", {
  timeZone: zone,
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

export function bangkokDay(value: string | Date): string {
  return day.format(new Date(value));
}

export function bangkokTime(value: string | Date): string {
  return time.format(new Date(value));
}

export function bangkokDateTime(value: string | Date): string {
  return `${bangkokDay(value)} · ${bangkokTime(value)}`;
}

export function toBangkokInputs(value: string): { date: string; time: string } {
  const shifted = new Date(new Date(value).getTime() + offsetMs).toISOString();
  return { date: shifted.slice(0, 10), time: shifted.slice(11, 16) };
}

export function fromBangkokInputs(date: string, clock: string): string {
  return new Date(`${date}T${clock}:00+07:00`).toISOString();
}
