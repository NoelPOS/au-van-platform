import { execFileSync } from "node:child_process";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { request, type FullConfig } from "@playwright/test";

/**
 * The one administrator the suite uses. A fixed subject, because it is promoted
 * once and the promotion has to survive between runs against the same volume;
 * every student subject is unique per test instead.
 */
export const adminSubject = "e2e-admin";

const here = dirname(fileURLToPath(import.meta.url));

export function compose(...args: string[]): string {
  return execFileSync(
    "docker",
    ["compose", "-f", "../../compose.yaml", "-f", "../../compose.e2e.yaml", ...args],
    { cwd: here, encoding: "utf8" },
  );
}

/**
 * Fails the whole run, once and legibly, when the web container is serving a
 * bundle built without `VITE_E2E_AUTH`.
 *
 * <p>The E2E web service differs from `compose.yaml`'s only in a build
 * argument, so the two produce the same image tag and `up` without `--build`
 * happily reuses a production image. Every spec then times out looking for a
 * sign-in control that is not in the page, which is an expensive way to be told
 * nothing. This is the mirror of the `Container checks` grep: that one proves
 * the marker is absent from a production bundle, this one proves it is present
 * in the one the suite is about to drive.
 */
async function assertTheServedBundleCarriesTheSignIn(
  api: Awaited<ReturnType<typeof request.newContext>>,
  baseURL: string,
): Promise<void> {
  const html = await (await api.get("/")).text();
  const asset = /\/assets\/[A-Za-z0-9._-]+\.js/.exec(html)?.[0];
  const bundle = asset ? await (await api.get(asset)).text() : "";
  if (!bundle.includes("auvan-e2e-sign-in")) {
    throw new Error(
      `The bundle served at ${baseURL} has no end-to-end sign-in control in it, so the ` +
        "web image was built without VITE_E2E_AUTH. Bring the stack up with --build:\n" +
        "  docker compose -f compose.yaml -f compose.e2e.yaml up -d --wait --build",
    );
  }
}

/**
 * Creates the administrator, which is more work than it sounds.
 *
 * <p>`bootstrapAdmin` is a separate Gradle `BootRun` main class, not a startup
 * hook, and the runtime image has no Gradle in it — so setting
 * `ADMIN_BOOTSTRAP_LINE_SUBJECT` would do nothing at all here. There is also no
 * API that promotes anybody, deliberately: `AdminBootstrapService`'s own comment
 * says never to expose one.
 *
 * <p>So: sign in once, which is what creates the `app_users` row; promote that
 * row with one statement against the database; then sign in again. The second
 * sign-in is not ceremony — the first token was minted before the promotion and
 * carries `role: STUDENT` forever, because ADR-005's tokens are short-lived
 * snapshots and nothing refreshes them.
 */
export default async function globalSetup(config: FullConfig) {
  const baseURL =
    (config.projects[0]?.use?.baseURL as string | undefined) ??
    "http://localhost:8081";
  const api = await request.newContext({ baseURL });

  try {
    await assertTheServedBundleCarriesTheSignIn(api, baseURL);

    const created = await api.post("/api/v1/auth/line/exchange", {
      data: { idToken: adminSubject },
    });
    if (!created.ok()) {
      throw new Error(
        `The end-to-end sign-in is not working: ${created.status()} ${await created.text()}. ` +
          "Is the stack up with compose.e2e.yaml, so the API is pointed at the verification double?",
      );
    }

    const user = process.env.POSTGRES_USER ?? "au_van";
    const database = process.env.POSTGRES_DB ?? "au_van";
    compose(
      "exec", "-T", "postgres",
      "psql", "-v", "ON_ERROR_STOP=1", "-U", user, "-d", database,
      "-c", `UPDATE app_users SET role = 'ADMIN', updated_at = now() WHERE line_subject = '${adminSubject}'`,
    );

    const promoted = await api.post("/api/v1/auth/line/exchange", {
      data: { idToken: adminSubject },
    });
    const session = (await promoted.json()) as { user: { role: string } };
    if (session.user.role !== "ADMIN") {
      throw new Error(
        `The administrator was not promoted: the exchange still answers ${session.user.role}.`,
      );
    }
  } finally {
    await api.dispose();
  }
}
