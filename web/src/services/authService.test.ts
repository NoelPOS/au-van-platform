import { describe, expect, it, vi } from "vitest";
import {
  authenticatedFetch,
  DemoSignInUnavailable,
  exchangeLineIdToken,
  LineTokenRejected,
  signInAsDemoStaff,
  signInAsDemoStudent,
  WrongPasscode,
} from "./authService";

describe("LIFF session boundary", () => {
  it("exchanges only the LINE ID token for an AU-Van session", async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        accessToken: "au-van-token",
        expiresIn: 900,
        user: { id: "user-id", role: "STUDENT", displayName: "Noel" },
      }),
    });

    await expect(
      exchangeLineIdToken("line-id-token", fetcher),
    ).resolves.toMatchObject({
      accessToken: "au-van-token",
      user: { role: "STUDENT" },
    });
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/auth/line/exchange",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ idToken: "line-id-token" }),
      }),
    );
  });

  it("adds the AU-Van JWT to authenticated API requests", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response());
    await authenticatedFetch(
      {
        accessToken: "au-van-token",
        expiresIn: 900,
        user: { id: "user-id", role: "STUDENT", displayName: null },
      },
      "/api/v1/auth/me",
      {},
      fetcher,
    );

    const options = fetcher.mock.calls[0][1] as RequestInit;
    expect(new Headers(options.headers).get("Authorization")).toBe(
      "Bearer au-van-token",
    );
  });

  it("reports a failed token exchange without persisting the LINE token", async () => {
    await expect(
      exchangeLineIdToken("invalid", vi.fn().mockResolvedValue({ ok: false })),
    ).rejects.toThrow("LINE authentication could not be completed.");
  });

  it("marks a token the API rejects as unauthorised apart from other failures", async () => {
    const answering = (status: number) =>
      vi.fn().mockResolvedValue(new Response(null, { status }));

    await expect(
      exchangeLineIdToken("expired", answering(401)),
    ).rejects.toBeInstanceOf(LineTokenRejected);
    await expect(
      exchangeLineIdToken("line-id-token", answering(500)),
    ).rejects.not.toBeInstanceOf(LineTokenRejected);
  });
});

describe("demo sign-in", () => {
  const demoSession = {
    accessToken: "demo-token",
    expiresIn: 900,
    user: { id: "demo-id", role: "STUDENT", displayName: "Demo Student" },
  };
  const answering = (status: number) =>
    vi.fn().mockResolvedValue(new Response(null, { status }));

  it("asks for the same demo student back when it knows one", async () => {
    const fetcher = vi.fn().mockResolvedValue(Response.json(demoSession));

    await expect(signInAsDemoStudent("demo-id", fetcher)).resolves.toEqual(
      demoSession,
    );
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/auth/demo/student",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ studentId: "demo-id" }),
      }),
    );
  });

  it("asks for a fresh demo student when it knows none", async () => {
    const fetcher = vi.fn().mockResolvedValue(Response.json(demoSession));

    await signInAsDemoStudent(undefined, fetcher);

    expect(fetcher.mock.calls[0][1]).toMatchObject({ body: "{}" });
  });

  it("sends the staff passcode and returns the staff session", async () => {
    const fetcher = vi.fn().mockResolvedValue(Response.json(demoSession));

    await expect(signInAsDemoStaff("open-sesame", fetcher)).resolves.toEqual(
      demoSession,
    );
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/auth/demo/staff",
      expect.objectContaining({
        body: JSON.stringify({ passcode: "open-sesame" }),
      }),
    );
  });

  it("tells a wrong passcode apart from a demo that is switched off", async () => {
    await expect(
      signInAsDemoStaff("guess", answering(401)),
    ).rejects.toBeInstanceOf(WrongPasscode);
    await expect(
      signInAsDemoStaff("guess", answering(404)),
    ).rejects.toBeInstanceOf(DemoSignInUnavailable);
    await expect(
      signInAsDemoStudent(undefined, answering(404)),
    ).rejects.toBeInstanceOf(DemoSignInUnavailable);
  });

  it("reports any other failure as a plain error", async () => {
    const failure = signInAsDemoStudent(undefined, answering(500));

    await expect(failure).rejects.toThrow("The demo sign-in did not finish");
    await expect(failure).rejects.not.toBeInstanceOf(DemoSignInUnavailable);
  });
});
