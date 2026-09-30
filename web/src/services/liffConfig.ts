export function configuredLiffId(): string | null {
  const liffId = import.meta.env.VITE_LIFF_ID?.trim();
  return liffId ? liffId : null;
}

// Must stay a module-level constant: only then does the bundler drop the E2E
// sign-in from other builds. Only "true" enables it; the Dockerfile passes "".
export const e2eAuthEnabled = import.meta.env.VITE_E2E_AUTH === "true";
