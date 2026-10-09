import { EmptyState } from "./ui/EmptyState";

export function SignInAgain() {
  return (
    <main className="mx-auto max-w-xl px-5 py-16" role="alert">
      <EmptyState
        detail="Your sign-in has expired. Close and reopen AU-Van from LINE to carry on."
        title="Sign in again"
      />
    </main>
  );
}
