import { HowItWorks } from "../components/HowItWorks";
import { IntroVideo } from "../components/IntroVideo";
import { githubUrl, LandingHeader } from "../components/LandingHeader";
import { LandingHero } from "../components/LandingHero";
import { SampleDepartures } from "../components/SampleDepartures";
import { Screenshots } from "../components/Screenshots";
import { StaffPortal } from "../components/StaffPortal";
import { UnderTheHood } from "../components/UnderTheHood";
import type { AuthSession } from "../types/auth";

export function LandingPage({
  onSignedIn,
}: {
  onSignedIn: (session: AuthSession) => void;
}) {
  return (
    <div className="min-h-svh overflow-x-clip bg-paper">
      <LandingHeader />
      <main>
        <LandingHero onSignedIn={onSignedIn} />
        <SampleDepartures />
        <HowItWorks />
        <Screenshots />
        <IntroVideo />
        <UnderTheHood />
        <StaffPortal onSignedIn={onSignedIn} />
      </main>
      <footer className="border-t border-line">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 px-5 py-10 text-sm text-muted sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <p>A portfolio project. Payments are samples; nothing is charged.</p>
          <a
            className="inline-flex min-h-11 items-center font-medium text-ink underline-offset-4 hover:underline"
            href={githubUrl}
          >
            Source on GitHub
          </a>
        </div>
      </footer>
    </div>
  );
}
