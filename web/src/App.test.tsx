import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createLiffSession } from "./services/liffService";
import { renderApp } from "./test/renderApp";

vi.mock("./services/liffService", () => ({ createLiffSession: vi.fn() }));

describe("App", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    vi.clearAllMocks();
  });

  it("shows that the API is available when the health check succeeds", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));

    renderApp();

    expect(await screen.findByText("Available")).toBeInTheDocument();
  });

  it("offers no end-to-end sign-in in a build that did not ask for one", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));

    renderApp();

    expect(await screen.findByText("Available")).toBeInTheDocument();
    expect(screen.queryByTestId("auvan-e2e-sign-in")).toBeNull();
  });

  it("takes a signed-in student straight into the booking flow", async () => {
    vi.stubEnv("VITE_LIFF_ID", "1234567890-abcdefgh");
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith("/actuator/health")
          ? new Response(null, { status: 200 })
          : new Response("[]", {
              headers: { "Content-Type": "application/json" },
            }),
      ),
    );
    vi.mocked(createLiffSession).mockResolvedValue({
      accessToken: "student-token",
      expiresIn: 900,
      user: { id: "student-id", role: "STUDENT", displayName: "Somchai" },
    });

    renderApp();
    fireEvent.click(screen.getByRole("button", { name: "Sign in with LINE" }));

    expect(await screen.findByText("Book a seat")).toBeInTheDocument();
    expect(screen.getByText("Upcoming trips")).toBeInTheDocument();
  });

  it("lets a signed-in administrator reach the payment review screen", async () => {
    vi.stubEnv("VITE_LIFF_ID", "1234567890-abcdefgh");
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith("/actuator/health")
          ? new Response(null, { status: 200 })
          : new Response("[]", {
              headers: { "Content-Type": "application/json" },
            }),
      ),
    );
    vi.mocked(createLiffSession).mockResolvedValue({
      accessToken: "admin-token",
      expiresIn: 900,
      user: { id: "admin-id", role: "ADMIN", displayName: "Noel" },
    });

    renderApp();
    fireEvent.click(screen.getByRole("button", { name: "Sign in with LINE" }));

    expect(
      await screen.findByRole("heading", { name: "Transport inventory" }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("link", { name: "Payment review" }));

    expect(await screen.findByText("Nothing to review")).toBeInTheDocument();
  });
});
