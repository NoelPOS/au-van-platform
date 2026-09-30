import liff from "@line/liff";
import { configuredLiffId } from "./liffConfig";
import type { AuthSession } from "../types/auth";
import { exchangeLineIdToken } from "./authService";

export async function createLiffSession(): Promise<AuthSession> {
  const liffId = configuredLiffId();
  if (!liffId) {
    throw new Error("VITE_LIFF_ID is not configured.");
  }

  await liff.init({ liffId, withLoginOnExternalBrowser: true });

  if (!liff.isLoggedIn()) {
    liff.login();
    throw new Error("Redirecting to LINE sign-in.");
  }

  const idToken = liff.getIDToken();
  if (!idToken) {
    throw new Error(
      "LINE did not provide an identity token. Check the openid scope.",
    );
  }

  return exchangeLineIdToken(idToken);
}
