import { execFileSync } from "node:child_process";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { request, type FullConfig } from "@playwright/test";

export const adminSubject = "e2e-admin";

const here = dirname(fileURLToPath(import.meta.url));

export function compose(...args: string[]): string {
  return execFileSync(
    "docker",
    ["compose", "-f", "../../compose.yaml", "-f", "../../compose.e2e.yaml", ...args],
    { cwd: here, encoding: "utf8" },
  );
}

async function assertBundleHasE2eSignIn(
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

export default async function globalSetup(config: FullConfig) {
  const baseURL =
    (config.projects[0]?.use?.baseURL as string | undefined) ??
    "http://localhost:8081";
  const api = await request.newContext({ baseURL });

  try {
    await assertBundleHasE2eSignIn(api, baseURL);

    const created = await api.post("/api/v1/auth/line/exchange", {
      data: { idToken: adminSubject },
    });
    if (!created.ok()) {
      throw new Error(
        `The end-to-end sign-in is not working: ${created.status()} ${await created.text()}. ` +
          "Is the stack up with compose.e2e.yaml, so the API is pointed at the verification double?",
      );
    }

    // No API promotes a user and the runtime image has no Gradle, so promote with SQL.
    const user = process.env.POSTGRES_USER ?? "au_van";
    const database = process.env.POSTGRES_DB ?? "au_van";
    compose(
      "exec", "-T", "postgres",
      "psql", "-v", "ON_ERROR_STOP=1", "-U", user, "-d", database,
      "-c", `UPDATE app_users SET role = 'ADMIN', updated_at = now() WHERE line_subject = '${adminSubject}'`,
    );

    // Sign in again: the first token was minted before the promotion and still says STUDENT.
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
