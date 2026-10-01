import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createLiffSession,
  lineSignInExpired,
  resumeLiffSession,
} from "../services/liffService";
import type { AuthSession } from "../types/auth";
import { SignInPage } from "./SignInPage";

vi.mock("../services/liffService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/liffService")>()),
  createLiffSession: vi.fn(),
  resumeLiffSession: vi.fn(),
}));

const session: AuthSession = {
  accessToken: "au-van-token",
  expiresIn: 900,
  user: { id: "student-id", role: "STUDENT", displayName: "Somchai" },
};

function renderSignIn() {
  const onSignedIn = vi.fn();
  render(
    <StrictMode>
      <SignInPage onSignedIn={onSignedIn} />
    </StrictMode>,
  );
  return onSignedIn;
}

describe("sign-in page", () => {
  beforeEach(() => {
    vi.stubEnv("VITE_LIFF_ID", "1234567890-abcdefgh");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("shows that it is connecting to LINE, with no button to click yet", async () => {
    vi.mocked(resumeLiffSession).mockReturnValue(new Promise(() => {}));

    renderSignIn();

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Connecting to LINE…",
    );
    expect(
      screen.queryByRole("button", { name: "Sign in with LINE" }),
    ).toBeNull();
  });

  it("signs a returning LINE user in once, without a click", async () => {
    vi.mocked(resumeLiffSession).mockResolvedValue(session);

    const onSignedIn = renderSignIn();

    await vi.waitFor(() => expect(onSignedIn).toHaveBeenCalledWith(session));
    expect(resumeLiffSession).toHaveBeenCalledOnce();
    expect(createLiffSession).not.toHaveBeenCalled();
  });

  it("offers the LINE button once LINE reports nobody signed in", async () => {
    vi.mocked(resumeLiffSession).mockResolvedValue(null);
    vi.mocked(createLiffSession).mockResolvedValue(session);

    const onSignedIn = renderSignIn();
    fireEvent.click(
      await screen.findByRole("button", { name: "Sign in with LINE" }),
    );

    await vi.waitFor(() => expect(onSignedIn).toHaveBeenCalledWith(session));
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("says the LINE sign-in expired when the fresh login is rejected too", async () => {
    vi.mocked(resumeLiffSession).mockRejectedValue(
      new Error(lineSignInExpired),
    );

    const onSignedIn = renderSignIn();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      lineSignInExpired,
    );
    expect(
      screen.getByRole("button", { name: "Sign in with LINE" }),
    ).toBeEnabled();
    expect(onSignedIn).not.toHaveBeenCalled();
  });

  it("reports a failed click and lets the visitor try again", async () => {
    vi.mocked(resumeLiffSession).mockResolvedValue(null);
    vi.mocked(createLiffSession).mockRejectedValue(
      new Error("LINE authentication could not be completed."),
    );

    renderSignIn();
    fireEvent.click(
      await screen.findByRole("button", { name: "Sign in with LINE" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "LINE authentication could not be completed.",
    );
    expect(
      screen.getByRole("button", { name: "Sign in with LINE" }),
    ).toBeEnabled();
  });
});
