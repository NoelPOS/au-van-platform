export function configuredLiffId(): string | null {
  const liffId = import.meta.env.VITE_LIFF_ID?.trim();
  return liffId ? liffId : null;
}

export function officialAccountUrl(): string | null {
  const basicId = import.meta.env.VITE_LINE_OA_ID?.trim();
  return basicId
    ? `https://line.me/R/ti/p/${encodeURIComponent(basicId)}`
    : null;
}

// Must stay a module-level constant, or the bundler keeps the E2E sign-in in other builds.
export const e2eAuthEnabled = import.meta.env.VITE_E2E_AUTH === "true";

export const demoLoginEnabled = import.meta.env.VITE_DEMO_LOGIN === "true";

export function liffUrl(): string | null {
  const liffId = configuredLiffId();
  return liffId ? `https://liff.line.me/${liffId}` : null;
}

export function insideLineApp(): boolean {
  return navigator.userAgent.includes("Line/");
}

export function returningFromLine(search: string): boolean {
  const params = new URLSearchParams(search);
  return ["code", "liffClientId", "liff.state"].some((name) => params.has(name));
}
