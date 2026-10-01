import liff from "@line/liff";
import { configuredLiffId } from "./liffConfig";
import type { AuthSession } from "../types/auth";
import { exchangeLineIdToken, LineTokenRejected } from "./authService";

const reloginMarker = "auvan.lineRelogin";
const expiryMarginSeconds = 60;

export const lineSignInExpired =
  "Your LINE sign-in has expired. Sign in again.";

export async function resumeLiffSession(): Promise<AuthSession | null> {
  const liffId = configuredLiffId();
  if (!liffId) {
    throw new Error("VITE_LIFF_ID is not configured.");
  }

  // No withLoginOnExternalBrowser: this runs on load and must not send a
  // signed-out visitor to LINE before they ask.
  await liff.init({ liffId });
  return liff.isLoggedIn() ? exchangeCurrentToken() : null;
}

export async function createLiffSession(): Promise<AuthSession> {
  return (await resumeLiffSession()) ?? redirectToLineLogin();
}

async function exchangeCurrentToken(): Promise<AuthSession> {
  const idToken = liff.getIDToken();
  if (!idToken) {
    throw new Error(
      "LINE did not provide an identity token. Check the openid scope.",
    );
  }
  const expiresAt = liff.getDecodedIDToken()?.exp ?? 0;
  if (expiresAt - expiryMarginSeconds <= Date.now() / 1000) return startOver();

  try {
    const session = await exchangeLineIdToken(idToken);
    sessionStorage.removeItem(reloginMarker);
    return session;
  } catch (error) {
    if (error instanceof LineTokenRejected) return startOver();
    throw error;
  }
}

// LIFF keeps an expired ID token in localStorage and still reports a login, so
// discard it and log in afresh, but only once per tab so a rejection cannot loop.
function startOver(): Promise<never> {
  liff.logout();
  if (sessionStorage.getItem(reloginMarker)) {
    sessionStorage.removeItem(reloginMarker);
    throw new Error(lineSignInExpired);
  }
  sessionStorage.setItem(reloginMarker, "true");
  return redirectToLineLogin();
}

function redirectToLineLogin(): Promise<never> {
  // Path only: a query from an earlier LINE callback would carry a spent code.
  liff.login({
    redirectUri: window.location.origin + window.location.pathname,
  });
  // liff.login() navigates away; never settling keeps the page in its loading state.
  return new Promise(() => {});
}
