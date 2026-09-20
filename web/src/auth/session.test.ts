import { describe, expect, it, vi } from "vitest";
import { authenticatedFetch, exchangeLineIdToken } from "./session";

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
});
