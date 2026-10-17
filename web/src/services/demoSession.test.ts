import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  canResumeDemoStudent,
  forgetDemo,
  savedDemoSession,
  startDemoStaff,
  startDemoStudent,
} from "./demoSession";

function answering(user: { id: string; role: string }) {
  return vi.fn(async () =>
    Response.json({
      accessToken: `${user.id}-token`,
      expiresIn: 900,
      user: { ...user, displayName: "Demo Student" },
    }),
  );
}

function bodyOf(fetcher: ReturnType<typeof answering>, call: number) {
  const calls = fetcher.mock.calls as unknown as [string, RequestInit][];
  return JSON.parse(String(calls[call][1].body)) as Record<string, string>;
}

describe("demo session", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("gives the same browser the same demo student back", async () => {
    const fetcher = answering({ id: "demo-1", role: "STUDENT" });
    vi.stubGlobal("fetch", fetcher);

    await startDemoStudent();
    await startDemoStudent();

    expect(bodyOf(fetcher, 0)).toEqual({});
    expect(bodyOf(fetcher, 1)).toEqual({ studentId: "demo-1" });
  });

  it("marks the session as a demo and keeps it across a reload", async () => {
    vi.stubGlobal("fetch", answering({ id: "demo-1", role: "STUDENT" }));

    const session = await startDemoStudent();

    expect(session.demo).toBe(true);
    expect(savedDemoSession()).toEqual(session);
  });

  it("does not bring back a session whose token has run out", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.stubGlobal("fetch", answering({ id: "demo-1", role: "STUDENT" }));
    await startDemoStudent();

    vi.setSystemTime(Date.now() + 900_000);

    expect(savedDemoSession()).toBeNull();
  });

  it("resumes a demo student only once the token has run out", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.stubGlobal("fetch", answering({ id: "demo-1", role: "STUDENT" }));
    await startDemoStudent();

    expect(canResumeDemoStudent()).toBe(false);
    vi.setSystemTime(Date.now() + 900_000);
    expect(canResumeDemoStudent()).toBe(true);
  });

  it("never resumes a demo staff session", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.stubGlobal("fetch", answering({ id: "demo-1", role: "STUDENT" }));
    await startDemoStudent();
    vi.stubGlobal("fetch", answering({ id: "staff-1", role: "ADMIN" }));
    await startDemoStaff("open-sesame");

    vi.setSystemTime(Date.now() + 900_000);

    expect(canResumeDemoStudent()).toBe(false);
  });

  it("does not resume a student whose demo id is gone", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.stubGlobal("fetch", answering({ id: "demo-1", role: "STUDENT" }));
    await startDemoStudent();
    localStorage.clear();

    vi.setSystemTime(Date.now() + 900_000);

    expect(canResumeDemoStudent()).toBe(false);
  });

  it("keeps a staff demo session without claiming a demo student", async () => {
    const fetcher = answering({ id: "staff-1", role: "ADMIN" });
    vi.stubGlobal("fetch", fetcher);

    const session = await startDemoStaff("open-sesame");

    expect(bodyOf(fetcher, 0)).toEqual({ passcode: "open-sesame" });
    expect(savedDemoSession()).toEqual(session);
    expect(localStorage.length).toBe(0);
  });

  it("forgets the demo student and the session on leaving", async () => {
    const fetcher = answering({ id: "demo-1", role: "STUDENT" });
    vi.stubGlobal("fetch", fetcher);
    await startDemoStudent();

    forgetDemo();

    expect(savedDemoSession()).toBeNull();
    await startDemoStudent();
    expect(bodyOf(fetcher, 1)).toEqual({});
  });

  it("still signs in when the browser refuses storage", async () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("blocked", "SecurityError");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("blocked", "SecurityError");
    });
    vi.stubGlobal("fetch", answering({ id: "demo-1", role: "STUDENT" }));

    await expect(startDemoStudent()).resolves.toMatchObject({ demo: true });
    expect(savedDemoSession()).toBeNull();
  });
});
