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
