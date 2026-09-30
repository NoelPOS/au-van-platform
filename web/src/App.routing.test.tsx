import {
  act,
  cleanup,
  fireEvent,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createLiffSession } from "./services/liffService";
import { renderApp } from "./test/renderApp";
import type { ApplicationRole } from "./types/auth";

vi.mock("./services/liffService", () => ({ createLiffSession: vi.fn() }));

function signInAs(role: ApplicationRole) {
  vi.mocked(createLiffSession).mockResolvedValue({
    accessToken: `${role}-token`,
    expiresIn: 900,
    user: { id: `${role}-id`, role, displayName: "Somchai" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Sign in with LINE" }));
}

type TestRouter = ReturnType<typeof renderApp>;

function locationOf(router: TestRouter) {
  return router.state.location.pathname + router.state.location.search;
}

async function expectLocation(router: TestRouter, path: string) {
  await waitFor(() => expect(locationOf(router)).toBe(path));
}

describe("App routing", () => {
  beforeEach(() => {
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
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    vi.clearAllMocks();
  });

  it("opens the inventory for an administrator who signs in at /admin/inventory", async () => {
    const router = renderApp("/admin/inventory");
    signInAs("ADMIN");

    expect(
      await screen.findByRole("heading", { name: "Transport inventory" }),
    ).toBeInTheDocument();
    expect(locationOf(router)).toBe("/admin/inventory");
  });

  it("opens payment review for an administrator who signs in at /admin/payments", async () => {
    const router = renderApp("/admin/payments");
    signInAs("ADMIN");

    expect(await screen.findByText("Nothing to review")).toBeInTheDocument();
    expect(locationOf(router)).toBe("/admin/payments");
  });

  it("opens operations for an administrator who signs in at /admin/operations", async () => {
    const router = renderApp("/admin/operations");
    signInAs("ADMIN");

    expect(
      await screen.findByRole("heading", { name: "Operations" }),
    ).toBeInTheDocument();
    expect(locationOf(router)).toBe("/admin/operations");
  });

  it("sends an administrator at /admin on to the inventory", async () => {
    const router = renderApp("/admin");
    signInAs("ADMIN");

    expect(
      await screen.findByRole("heading", { name: "Transport inventory" }),
    ).toBeInTheDocument();
    await expectLocation(router, "/admin/inventory");
  });

  it("takes an administrator who signs in at / to the inventory", async () => {
    const router = renderApp("/");
    signInAs("ADMIN");

    expect(
      await screen.findByRole("heading", { name: "Transport inventory" }),
    ).toBeInTheDocument();
    await expectLocation(router, "/admin/inventory");
  });

  it("moves between administration destinations with links that mark the current one", async () => {
    const router = renderApp("/admin/inventory");
    signInAs("ADMIN");
    await screen.findByRole("heading", { name: "Transport inventory" });

    expect(
      screen.getByRole("link", { name: "Transport inventory" }),
    ).toHaveAttribute("aria-current", "page");
    fireEvent.click(screen.getByRole("link", { name: "Operations" }));

    expect(
      await screen.findByRole("heading", { name: "Operations" }),
    ).toBeInTheDocument();
    expect(locationOf(router)).toBe("/admin/operations");
    expect(screen.getByRole("link", { name: "Operations" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(
      screen.getByRole("link", { name: "Transport inventory" }),
    ).not.toHaveAttribute("aria-current");
  });

  it("sends a student who opens /admin/payments to the booking page", async () => {
    const router = renderApp("/admin/payments");
    signInAs("STUDENT");

    expect(await screen.findByText("Book a seat")).toBeInTheDocument();
    await expectLocation(router, "/");
    expect(
      screen.queryByRole("navigation", { name: "Administration" }),
    ).toBeNull();
  });

  it("sends a student who opens /admin to the booking page", async () => {
    const router = renderApp("/admin");
    signInAs("STUDENT");

    expect(await screen.findByText("Book a seat")).toBeInTheDocument();
    await expectLocation(router, "/");
  });

  it("shows only the sign-in screen to a signed-out visitor at /admin/payments", async () => {
    const router = renderApp("/admin/payments");

    expect(await screen.findByText("Available")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Sign in with LINE" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Payment review" }),
    ).toBeNull();
    expect(
      screen.queryByRole("navigation", { name: "Administration" }),
    ).toBeNull();
    expect(locationOf(router)).toBe("/admin/payments");
  });

  it("redirects a signed-out visitor on an unknown path to /", async () => {
    const router = renderApp("/nowhere");

    expect(
      await screen.findByRole("button", { name: "Sign in with LINE" }),
    ).toBeInTheDocument();
    await expectLocation(router, "/");
  });

  it("sends a signed-in user on an unknown path to their own area", async () => {
    const admin = renderApp("/");
    signInAs("ADMIN");
    await screen.findByRole("heading", { name: "Transport inventory" });
    await act(() => admin.navigate("/nowhere"));
    await expectLocation(admin, "/admin/inventory");
    cleanup();

    const student = renderApp("/");
    signInAs("STUDENT");
    await screen.findByText("Book a seat");
    await act(() => student.navigate("/nowhere"));
    await expectLocation(student, "/");
    expect(screen.getByText("Book a seat")).toBeInTheDocument();
  });

  it("keeps the query string when it redirects an unknown path", async () => {
    const router = renderApp("/liff/callback?code=abc&state=xyz");

    await expectLocation(router, "/?code=abc&state=xyz");
  });
});
