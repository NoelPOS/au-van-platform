import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { E2eSignIn } from "./E2eSignIn";

describe("end-to-end sign-in", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("exchanges the typed subject through the ordinary LINE exchange endpoint", async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        accessToken: "au-van-token",
        expiresIn: 900,
        user: { id: "user-id", role: "STUDENT", displayName: "e2e-student" },
      }),
    });
    vi.stubGlobal("fetch", fetcher);
    const onSignedIn = vi.fn();

    render(<E2eSignIn onSignedIn={onSignedIn} />);
    fireEvent.change(screen.getByLabelText("End-to-end sign-in subject"), {
      target: { value: "e2e-student" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign in as subject" }));

    expect(await screen.findByRole("button", { name: "Sign in as subject" }))
      .toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/auth/line/exchange",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ idToken: "e2e-student" }),
      }),
    );
    expect(onSignedIn).toHaveBeenCalledWith(
      expect.objectContaining({ accessToken: "au-van-token" }),
    );
  });

  it("reports a subject the API refuses and signs nobody in", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
    const onSignedIn = vi.fn();

    render(<E2eSignIn onSignedIn={onSignedIn} />);
    fireEvent.change(screen.getByLabelText("End-to-end sign-in subject"), {
      target: { value: "nobody" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign in as subject" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "LINE authentication could not be completed.",
    );
    expect(onSignedIn).not.toHaveBeenCalled();
  });
});
