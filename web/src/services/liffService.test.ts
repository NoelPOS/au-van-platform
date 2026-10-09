import liff from "@line/liff";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createLiffSession,
  isOfficialAccountFriend,
  lineSignInExpired,
  resumeLiffSession,
} from "./liffService";

vi.mock("@line/liff", () => ({
  default: {
    init: vi.fn(),
    isLoggedIn: vi.fn(),
    getIDToken: vi.fn(),
    getDecodedIDToken: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    getFriendship: vi.fn(),
  },
}));

const session = {
  accessToken: "au-van-token",
  expiresIn: 900,
  user: { id: "admin-id", role: "ADMIN", displayName: "Noel" },
};

function lineReports({ expiresInSeconds }: { expiresInSeconds: number }) {
  vi.mocked(liff.isLoggedIn).mockReturnValue(true);
  vi.mocked(liff.getIDToken).mockReturnValue("line-id-token");
  vi.mocked(liff.getDecodedIDToken).mockReturnValue({
    exp: Math.floor(Date.now() / 1000) + expiresInSeconds,
  } as ReturnType<typeof liff.getDecodedIDToken>);
}

function exchangeAnswers(status: number) {
  const fetcher = vi.fn(async () =>
    status === 200
      ? Response.json(session)
      : new Response(null, { status }),
  );
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

function settles(promise: Promise<unknown>) {
  return Promise.race([
    promise.then(
      () => true,
      () => true,
    ),
    new Promise((resolve) => setTimeout(() => resolve(false), 20)),
  ]);
}

describe("LIFF sign-in", () => {
  beforeEach(() => {
    vi.stubEnv("VITE_LIFF_ID", "1234567890-abcdefgh");
    window.history.replaceState(null, "", "/admin/trips");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it("exchanges a fresh LINE token on load without asking", async () => {
    lineReports({ expiresInSeconds: 3600 });
    const fetcher = exchangeAnswers(200);

    await expect(resumeLiffSession()).resolves.toEqual(session);
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/auth/line/exchange",
      expect.objectContaining({
        body: JSON.stringify({ idToken: "line-id-token" }),
      }),
    );
    expect(liff.logout).not.toHaveBeenCalled();
    expect(localStorage.length + sessionStorage.length).toBe(0);
  });

  it("does nothing for a visitor LINE has not signed in", async () => {
    vi.mocked(liff.isLoggedIn).mockReturnValue(false);
    const fetcher = exchangeAnswers(200);

    await expect(resumeLiffSession()).resolves.toBeNull();
    expect(fetcher).not.toHaveBeenCalled();
    expect(liff.login).not.toHaveBeenCalled();
  });

  it.each([
    ["has expired", -10],
    ["expires within the minute", 30],
  ])(
    "discards a cached LINE token that %s and logs in again at the same page",
    async (_, expiresInSeconds) => {
      lineReports({ expiresInSeconds });
      const fetcher = exchangeAnswers(200);

      expect(await settles(resumeLiffSession())).toBe(false);
      expect(fetcher).not.toHaveBeenCalled();
      expect(liff.logout).toHaveBeenCalledOnce();
      expect(liff.login).toHaveBeenCalledWith({
        redirectUri: `${window.location.origin}/admin/trips`,
      });
    },
  );

  it("treats a token LINE cannot decode as expired", async () => {
    lineReports({ expiresInSeconds: 3600 });
    vi.mocked(liff.getDecodedIDToken).mockReturnValue(null);
    const fetcher = exchangeAnswers(200);

    expect(await settles(resumeLiffSession())).toBe(false);
    expect(fetcher).not.toHaveBeenCalled();
    expect(liff.logout).toHaveBeenCalledOnce();
  });

  it("logs in again once when the API rejects the token, then stops", async () => {
    lineReports({ expiresInSeconds: 3600 });
    exchangeAnswers(401);

    expect(await settles(resumeLiffSession())).toBe(false);
    expect(liff.logout).toHaveBeenCalledOnce();
    expect(liff.login).toHaveBeenCalledOnce();

    await expect(resumeLiffSession()).rejects.toThrow(lineSignInExpired);
    expect(liff.logout).toHaveBeenCalledTimes(2);
    expect(liff.login).toHaveBeenCalledOnce();
  });

  it("offers a fresh relogin again once a sign-in has succeeded", async () => {
    lineReports({ expiresInSeconds: 3600 });
    exchangeAnswers(401);
    await settles(resumeLiffSession());
    exchangeAnswers(200);
    await resumeLiffSession();

    exchangeAnswers(401);
    expect(await settles(resumeLiffSession())).toBe(false);
    expect(liff.login).toHaveBeenCalledTimes(2);
  });

  it("reports an exchange that fails for another reason without logging out", async () => {
    lineReports({ expiresInSeconds: 3600 });
    exchangeAnswers(500);

    await expect(resumeLiffSession()).rejects.toThrow(
      "LINE authentication could not be completed.",
    );
    expect(liff.logout).not.toHaveBeenCalled();
    expect(liff.login).not.toHaveBeenCalled();
  });

  it("sends a signed-out visitor who asks to sign in to LINE and back to the same page", async () => {
    vi.mocked(liff.isLoggedIn).mockReturnValue(false);

    expect(await settles(createLiffSession())).toBe(false);
    expect(liff.login).toHaveBeenCalledWith({
      redirectUri: `${window.location.origin}/admin/trips`,
    });
  });
});

describe("isOfficialAccountFriend", () => {
  it("reports what LINE says about the friendship", async () => {
    vi.mocked(liff.isLoggedIn).mockReturnValue(true);
    vi.mocked(liff.getFriendship).mockResolvedValue({ friendFlag: false });

    expect(await isOfficialAccountFriend()).toBe(false);
  });

  it("does not guess outside a signed-in LIFF session", async () => {
    vi.mocked(liff.isLoggedIn).mockReturnValue(false);
    vi.mocked(liff.getFriendship).mockClear();

    expect(await isOfficialAccountFriend()).toBeNull();
    expect(liff.getFriendship).not.toHaveBeenCalled();
  });

  it("does not guess when LINE refuses to answer", async () => {
    vi.mocked(liff.isLoggedIn).mockReturnValue(true);
    vi.mocked(liff.getFriendship).mockRejectedValue(new Error("no bot link"));

    expect(await isOfficialAccountFriend()).toBeNull();
  });
});
