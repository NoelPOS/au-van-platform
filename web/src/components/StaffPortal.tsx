import { DemoStaffSignIn } from "./DemoStaffSignIn";
import { LandingSection } from "./LandingSection";
import { TextLink } from "./ui/TextLink";
import { TicketStub } from "./ui/TicketStub";
import { demoLoginEnabled } from "../services/liffConfig";
import type { AuthSession } from "../types/auth";

export function StaffPortal({
  onSignedIn,
}: {
  onSignedIn: (session: AuthSession) => void;
}) {
  return (
    <LandingSection
      id="staff"
      intro="Staff schedule trips, check payment slips and settle refunds from a desktop portal."
      kicker="Staff portal"
      title="The other side of the counter"
    >
      <TicketStub
        className="max-w-2xl"
        stub={
          <>
            <p className="font-mono text-[10px] tracking-[0.2em] text-muted uppercase">
              Admit
            </p>
            <p className="mt-1 font-display text-3xl leading-none text-brand-900">
              Staff
            </p>
          </>
        }
      >
        <p className="text-[15px] leading-7 text-ink">
          Staff sign in with LINE, just like students. An approved staff
          account opens the operations portal instead of the booking app.
        </p>
        <TextLink to="/admin">Staff sign-in</TextLink>
        {demoLoginEnabled && (
          <div className="mt-5 border-t border-dashed border-line pt-5">
            <p className="mb-4 text-sm text-muted">
              Reviewing the project? Use the demo passcode to look around as
              staff.
            </p>
            <DemoStaffSignIn onSignedIn={onSignedIn} />
          </div>
        )}
      </TicketStub>
    </LandingSection>
  );
}
