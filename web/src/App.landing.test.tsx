import { act, cleanup, fireEvent, screen } from "@testing-library/react";
import { focusManager } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resumeLiffSession } from "./services/liffService";
import { booking } from "./test/bookingFixtures";
import { openInsideLine, renderApp } from "./test/renderApp";

vi.mock("./services/liffService", () => ({
  createLiffSession: vi.fn(),
  resumeLiffSession: vi.fn().mockResolvedValue(null),
  isOfficialAccountFriend: vi.fn().mockResolvedValue(false),
}));

vi.mock("./services/liffConfig", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./services/liffConfig")>()),
  demoLoginEnabled: true,
}));

const landingTitle = { level: 1, name: /without leaving LINE/ } as const;

function demoUser(role: string) {
  return {
    accessToken: `${role}-token-${Math.random()}`,
    expiresIn: 900,
    user: { id: `${role}-demo-id`, role, displayName: "Demo Visitor" },
  };
}

type Answer = () => Response | Promise<Response>;

function stubApi({
  trips = () => Response.json([]),
  bookings = () => Response.json([]),
  student = () => Response.json(demoUser("STUDENT")),
}: { trips?: Answer; bookings?: Answer; student?: Answer } = {}) {
  const fetcher = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith("/auth/demo/student")) return student();
    if (url.endsWith("/auth/demo/staff")) return Response.json(demoUser("ADMIN"));
    if (url === "/api/v1/trips") return trips();
    if (url === "/api/v1/bookings") return bookings();
    return Response.json([]);
  });
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

function demoBodies(fetcher: ReturnType<typeof stubApi>) {
  return (fetcher.mock.calls as unknown as [string, RequestInit][])
    .filter(([url]) => url.endsWith("/auth/demo/student"))
    .map(([, init]) => init.body);
}

async function tryStudentApp() {
  fireEvent.click(await screen.findByRole("button", { name: "Try the student app" }));
  await screen.findByText("Catch the next van");
}

describe("App landing and demo sign-in", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.stubEnv("VITE_LIFF_ID", "1234567890-abcdefgh");
    vi.stubEnv("VITE_LINE_OA_ID", "@auvan");
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    vi.clearAllMocks();
    vi.restoreAllMocks();
    focusManager.setFocused(undefined);
  });

  it("shows the landing page to a signed-out visitor outside LINE", async () => {
    stubApi();

    renderApp("/");

    expect(await screen.findByRole("heading", landingTitle)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sign in with LINE" })).toBeNull();
    expect(resumeLiffSession).not.toHaveBeenCalled();
  });

  it("keeps the LINE sign-in for a visitor inside the LINE app", async () => {
    openInsideLine();
    stubApi();

    renderApp("/");

    expect(await screen.findByRole("button", { name: "Sign in with LINE" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", landingTitle)).toBeNull();
  });

  it("lets a LINE login that returns to an outside browser finish", async () => {
    stubApi();

    renderApp("/?code=abc&state=xyz&liffClientId=1234567890");

    expect(await screen.findByRole("button", { name: "Sign in with LINE" })).toBeInTheDocument();
    expect(resumeLiffSession).toHaveBeenCalledOnce();
  });

  it("marks a demo student's app as a demo and leaves out the LINE friend prompt", async () => {
    stubApi();
    renderApp("/");

    await tryStudentApp();
    await act(() => new Promise((settle) => setTimeout(settle, 50)));

    expect(screen.getByText("Demo")).toBeInTheDocument();
    expect(screen.queryByText("Turn on LINE updates")).toBeNull();
  });

  it("keeps a demo student signed in across a page reload", async () => {
    stubApi();
    renderApp("/");
    await tryStudentApp();
    cleanup();

    renderApp("/tickets");

    expect(await screen.findByRole("button", { name: "Leave demo" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", landingTitle)).toBeNull();
  });

  it("returns to the landing page and forgets the demo on leaving", async () => {
    const fetcher = stubApi();
    renderApp("/");
    await tryStudentApp();

    fireEvent.click(screen.getByRole("button", { name: "Leave demo" }));

    expect(await screen.findByRole("heading", landingTitle)).toBeInTheDocument();
    await tryStudentApp();
    expect(demoBodies(fetcher)).toEqual(["{}", "{}"]);
  });

  it("shows the next demo student nothing of the last one's tickets", async () => {
    const fetcher = stubApi({
      bookings: () =>
        demoBodies(fetcher).length === 1
          ? Response.json([booking])
          : new Promise<Response>(() => {}),
    });
    const router = renderApp("/");
    await tryStudentApp();
    await act(() => router.navigate("/tickets"));
    expect(await screen.findByText(new RegExp(booking.reference))).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Leave demo" }));
    await tryStudentApp();
    await act(() => router.navigate("/tickets"));

    expect(screen.queryByText(new RegExp(booking.reference))).toBeNull();
  });

  it("signs a demo student back in as the same student when the token runs out", async () => {
    let calls = 0;
    const fetcher = stubApi({
      trips: () => {
        calls += 1;
        return calls === 1 ? new Response(null, { status: 401 }) : Response.json([]);
      },
    });
    renderApp("/");

    fireEvent.click(await screen.findByRole("button", { name: "Try the student app" }));

    await vi.waitFor(() =>
      expect(demoBodies(fetcher)).toEqual([
        "{}",
        JSON.stringify({ studentId: "STUDENT-demo-id" }),
      ]),
    );
    expect(await screen.findByText("Catch the next van")).toBeInTheDocument();
    expect(screen.queryByText("Sign in again")).toBeNull();
  });

  it("renews a demo student once, even when the expired screens had data", async () => {
    let calls = 0;
    const fetcher = stubApi({
      trips: () => {
        calls += 1;
        return calls === 2 ? new Response(null, { status: 401 }) : Response.json([]);
      },
    });
    renderApp("/");
    await tryStudentApp();

    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });

    await vi.waitFor(() => expect(demoBodies(fetcher)).toHaveLength(2));
    expect(await screen.findByText("Catch the next van")).toBeInTheDocument();
    await act(() => new Promise((settle) => setTimeout(settle, 100)));
    expect(demoBodies(fetcher)).toHaveLength(2);
  });

  it("offers a fresh start when the demo sign-in cannot be renewed", async () => {
    let signIns = 0;
    stubApi({
      trips: () => new Response(null, { status: 401 }),
      student: () => {
        signIns += 1;
        return signIns === 1
          ? Response.json(demoUser("STUDENT"))
          : new Response(null, { status: 500 });
      },
    });
    renderApp("/");

    fireEvent.click(await screen.findByRole("button", { name: "Try the student app" }));

    fireEvent.click(await screen.findByRole("button", { name: "Start the demo again" }));
    expect(await screen.findByRole("heading", landingTitle)).toBeInTheDocument();
  });

  it("offers the staff passcode under the LINE sign-in at /admin", async () => {
    stubApi();
    renderApp("/admin");

    expect(await screen.findByRole("button", { name: "Sign in with LINE" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Demo staff passcode"), {
      target: { value: "open-sesame" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Open staff demo" }));

    expect(await screen.findByRole("heading", { name: "Overview" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Leave demo" }).length).toBeGreaterThan(0);
  });
});
