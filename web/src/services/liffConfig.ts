export function configuredLiffId(): string | null {
  const liffId = import.meta.env.VITE_LIFF_ID?.trim();
  return liffId ? liffId : null;
}

/**
 * Whether this build carries the Playwright suite's sign-in control (ADR-013).
 *
 * <p>**A constant, deliberately, and not a function like `configuredLiffId`
 * above.** Vite inlines `import.meta.env.VITE_E2E_AUTH` at build time either
 * way, but only a module-level constant is propagated across modules: written
 * as `e2eAuthEnabled()`, the call survives minification, its branch survives
 * with it, and the control's markup stays in the production bundle. That was
 * measured, not assumed — as a function the bundle still contained
 * `auvan-e2e-sign-in`; as this constant it does not, and `Container checks`
 * greps the served bundle to keep it that way.
 *
 * <p>Only `"true"` enables it. `web/Dockerfile` defaults the build argument to
 * an empty string, which is a value rather than an absence, so a truthiness
 * check would be the wrong test.
 */
export const e2eAuthEnabled = import.meta.env.VITE_E2E_AUTH === "true";
