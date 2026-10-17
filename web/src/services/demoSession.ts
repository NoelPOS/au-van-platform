import type { AuthSession } from "../types/auth";
import { signInAsDemoStaff, signInAsDemoStudent } from "./authService";

const studentKey = "auvan.demoStudentId";
const sessionKey = "auvan.demoSession";

type SavedSession = { session: AuthSession; expiresAt: number };

// Storage throws in private modes and when the browser blocks it; the demo works without it.
function read(storage: () => Storage, key: string): string | null {
  try {
    return storage().getItem(key);
  } catch {
    return null;
  }
}

function write(storage: () => Storage, key: string, value: string | null) {
  try {
    if (value === null) storage().removeItem(key);
    else storage().setItem(key, value);
  } catch {
    return;
  }
}

function keep(session: AuthSession): AuthSession {
  const demo = { ...session, demo: true };
  const expiresAt = Date.now() + session.expiresIn * 1000;
  write(
    () => sessionStorage,
    sessionKey,
    JSON.stringify({ session: demo, expiresAt }),
  );
  return demo;
}

export async function startDemoStudent(): Promise<AuthSession> {
  const knownId = read(() => localStorage, studentKey) ?? undefined;
  const session = await signInAsDemoStudent(knownId);
  write(() => localStorage, studentKey, session.user.id);
  return keep(session);
}

export async function startDemoStaff(passcode: string): Promise<AuthSession> {
  return keep(await signInAsDemoStaff(passcode));
}

export function savedDemoSession(): AuthSession | null {
  try {
    const saved = JSON.parse(
      read(() => sessionStorage, sessionKey) ?? "null",
    ) as SavedSession | null;
    return saved && saved.expiresAt > Date.now() ? saved.session : null;
  } catch {
    return null;
  }
}

export function forgetDemo() {
  write(() => sessionStorage, sessionKey, null);
  write(() => localStorage, studentKey, null);
}
