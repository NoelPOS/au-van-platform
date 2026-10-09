import {
  act,
  cleanup,
  fireEvent,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resumeLiffSession } from "./services/liffService";
import { renderApp } from "./test/renderApp";
import type { ApplicationRole } from "./types/auth";

vi.mock("./services/liffService", () => ({
  createLiffSession: vi.fn(),
  resumeLiffSession: vi.fn(),
  isOfficialAccountFriend: vi.fn().mockResolvedValue(null),
}));

function openSignedIn(path: string, role: ApplicationRole) {
  vi.mocked(resumeLiffSession).mockResolvedValue({
    accessToken: `${role}-token`,
    expiresIn: 900,
    user: { id: `${role}-id`, role, displayName: "Somchai" },
  });
  return renderApp(path);
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
    vi.mocked(resumeLiffSession).mockResolvedValue(null);
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

  it("sends an administrator at the old /admin/inventory address on to trips", async () => {
    const router = openSignedIn("/admin/inventory", "ADMIN");

    expect(
      await screen.findByRole("heading", { name: "Trips", level: 1 }),
    ).toBeInTheDocument();
    await expectLocation(router, "/admin/trips");
  });

  it.each([
    ["/admin/trips", "Trips"],
    ["/admin/routes", "Routes"],
    ["/admin/vans", "Vans"],
    ["/admin/seat-layouts", "Seat layouts"],
  ])(
    "opens %s for an administrator who signs in there",
    async (path, title) => {
      const router = openSignedIn(path, "ADMIN");

      expect(
        await screen.findByRole("heading", { name: title, level: 1 }),
      ).toBeInTheDocument();
      expect(locationOf(router)).toBe(path);
    },
  );

  it("opens payment review for an administrator who signs in at /admin/payments", async () => {
    const router = openSignedIn("/admin/payments", "ADMIN");

    expect(await screen.findByText("Nothing to review")).toBeInTheDocument();
    expect(locationOf(router)).toBe("/admin/payments");
  });

  it("opens operations for an administrator who signs in at /admin/operations", async () => {
    const router = openSignedIn("/admin/operations", "ADMIN");

    expect(
      await screen.findByRole("heading", { name: "Operations" }),
    ).toBeInTheDocument();
    expect(locationOf(router)).toBe("/admin/operations");
  });

  it("opens the overview for an administrator at /admin", async () => {
    const router = openSignedIn("/admin", "ADMIN");

    expect(
      await screen.findByRole("heading", { name: "Overview" }),
    ).toBeInTheDocument();
    expect(locationOf(router)).toBe("/admin");
  });

  it("takes an administrator who signs in at / to the overview", async () => {
    const router = openSignedIn("/", "ADMIN");

    expect(
      await screen.findByRole("heading", { name: "Overview" }),
    ).toBeInTheDocument();
    await expectLocation(router, "/admin");
  });

  it("moves between administration destinations with links that mark the current one", async () => {
    const router = openSignedIn("/admin/trips", "ADMIN");
    await screen.findByRole("heading", { name: "Trips", level: 1 });

    expect(screen.getByRole("link", { name: "Trips" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "Overview" })).not.toHaveAttribute(
      "aria-current",
    );
    expect(screen.getAllByText("Somchai").length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("link", { name: "Operations" }));

    expect(
      await screen.findByRole("heading", { name: "Operations" }),
    ).toBeInTheDocument();
    expect(locationOf(router)).toBe("/admin/operations");
    expect(screen.getByRole("link", { name: "Operations" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "Trips" })).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("reaches the tucked-away destinations from the More sheet", async () => {
    const router = openSignedIn("/admin", "ADMIN");
    await screen.findByRole("heading", { name: "Overview" });

    fireEvent.click(screen.getByRole("button", { name: "More" }));
    const sheet = within(screen.getByRole("dialog", { name: "More" }));
    fireEvent.click(sheet.getByRole("link", { name: "Vans" }));

    expect(
      await screen.findByRole("heading", { name: "Vans", level: 1 }),
    ).toBeInTheDocument();
    expect(locationOf(router)).toBe("/admin/vans");
    expect(screen.queryByRole("dialog", { name: "More" })).toBeNull();
  });

  it("sends a student who opens /admin/payments to the departures", async () => {
    const router = openSignedIn("/admin/payments", "STUDENT");

    expect(await screen.findByText("Catch the next van")).toBeInTheDocument();
    await expectLocation(router, "/");
    expect(
      screen.queryByRole("navigation", { name: "Administration" }),
    ).toBeNull();
  });

  it("sends a student who opens /admin to the departures", async () => {
    const router = openSignedIn("/admin", "STUDENT");

    expect(await screen.findByText("Catch the next van")).toBeInTheDocument();
    await expectLocation(router, "/");
  });

  it("shows only the sign-in screen to a signed-out visitor at /admin/payments", async () => {
    const router = renderApp("/admin/payments");

    expect(await screen.findByText("Available")).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "Sign in with LINE" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Payment review" }),
    ).toBeNull();
    expect(
      screen.queryByRole("navigation", { name: "Administration" }),
    ).toBeNull();
    expect(locationOf(router)).toBe("/admin/payments");
  });

  it("keeps a signed-out student on a ticket link while they sign in", async () => {
    const router = renderApp("/tickets/booking-1");

    expect(
      await screen.findByRole("button", { name: "Sign in with LINE" }),
    ).toBeInTheDocument();
    expect(locationOf(router)).toBe("/tickets/booking-1");
  });

  it("redirects a signed-out visitor on an unknown path to /", async () => {
    const router = renderApp("/nowhere");

    expect(
      await screen.findByRole("button", { name: "Sign in with LINE" }),
    ).toBeInTheDocument();
    await expectLocation(router, "/");
  });

  it("sends a signed-in user on an unknown path to their own area", async () => {
    const admin = openSignedIn("/", "ADMIN");
    await screen.findByRole("heading", { name: "Overview" });
    await act(() => admin.navigate("/nowhere"));
    await expectLocation(admin, "/admin");
    cleanup();

    const student = openSignedIn("/", "STUDENT");
    await screen.findByText("Catch the next van");
    await act(() => student.navigate("/nowhere"));
    await expectLocation(student, "/");
    expect(screen.getByText("Catch the next van")).toBeInTheDocument();
  });

  it("keeps the query string when it redirects an unknown path", async () => {
    const router = renderApp("/liff/callback?code=abc&state=xyz");

    await expectLocation(router, "/?code=abc&state=xyz");
  });
});
