import { cleanup, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { startDemoStaff, startDemoStudent } from "./services/demoSession";
import { renderApp } from "./test/renderApp";

vi.mock("./services/liffService", () => ({
  createLiffSession: vi.fn(),
  resumeLiffSession: vi.fn().mockResolvedValue(null),
  isOfficialAccountFriend: vi.fn().mockResolvedValue(false),
}));

vi.mock("./services/liffConfig", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./services/liffConfig")>()),
  demoLoginEnabled: true,
}));

function demoUser(role: string, expiresIn: number) {
  return {
    accessToken: `${role}-token-${expiresIn}`,
    expiresIn,
    user: { id: `${role}-demo-id`, role, displayName: "Demo Visitor" },
  };
}

function stubApi(student: () => Response) {
  const fetcher = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith("/auth/demo/student")) return student();
    if (url.endsWith("/auth/demo/staff"))
      return Response.json(demoUser("ADMIN", 0));
    return Response.json([]);
  });
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

function studentSignIns(fetcher: ReturnType<typeof stubApi>) {
  return (fetcher.mock.calls as unknown as [string, RequestInit][])
    .filter(([url]) => url.endsWith("/auth/demo/student"))
    .map(([, init]) => init.body);
}

describe("App after a demo sign-in ran out", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.stubEnv("VITE_LIFF_ID", "1234567890-abcdefgh");
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    vi.clearAllMocks();
  });

  it("signs a returning demo student back in and reopens the same screen", async () => {
    stubApi(() => Response.json(demoUser("STUDENT", 0)));
    await startDemoStudent();
    const fetcher = stubApi(() => Response.json(demoUser("STUDENT", 900)));

    renderApp("/tickets");

    expect(
      screen.getByText("Picking up where you left off"),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "Leave demo" }),
    ).toBeInTheDocument();
    expect(studentSignIns(fetcher)).toEqual([
      JSON.stringify({ studentId: "STUDENT-demo-id" }),
    ]);
  });

  it("shows the landing page when the demo student cannot be signed back in", async () => {
    stubApi(() => Response.json(demoUser("STUDENT", 0)));
    await startDemoStudent();
    stubApi(() => new Response(null, { status: 500 }));

    renderApp("/tickets");

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: /without leaving LINE/,
      }),
    ).toBeInTheDocument();
  });

  it("sends a demo staff member back to the sign-in", async () => {
    stubApi(() => Response.json(demoUser("STUDENT", 900)));
    await startDemoStudent();
    await startDemoStaff("open-sesame");
    const fetcher = stubApi(() => Response.json(demoUser("STUDENT", 900)));

    renderApp("/admin");

    expect(
      await screen.findByLabelText("Demo staff passcode"),
    ).toBeInTheDocument();
    expect(studentSignIns(fetcher)).toEqual([]);
  });
});
