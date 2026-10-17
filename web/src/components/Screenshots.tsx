import { BrowserFrame, PhoneFrame, ShotImage } from "./DeviceFrames";
import { LandingSection } from "./LandingSection";

const phoneShots = [
  { src: "/landing/student-trips.webp", caption: "Today’s departures" },
  { src: "/landing/seat-picker.webp", caption: "Choosing a seat" },
  { src: "/landing/boarding-pass.webp", caption: "The boarding pass" },
];

const browserShots = [
  {
    src: "/landing/admin-payments.webp",
    caption: "Checking payment slips",
    address: "auvan.duckdns.org/admin/payments",
  },
  {
    src: "/landing/admin-trips.webp",
    caption: "The trip calendar",
    address: "auvan.duckdns.org/admin/trips",
  },
];

function Caption({ number, text }: { number: number; text: string }) {
  return (
    <figcaption className="mt-4 flex items-baseline gap-3 text-sm text-ink">
      <span className="font-mono text-[11px] text-muted">{`Fig. ${number}`}</span>
      {text}
    </figcaption>
  );
}

export function Screenshots() {
  return (
    <LandingSection
      id="screens"
      intro="Students book on their phones inside LINE. Staff work from a desktop browser."
      kicker="Screens"
      title="Two apps, one set of seats"
    >
      <div className="-mx-5 flex snap-x scroll-px-5 gap-6 overflow-x-auto px-5 pb-4 [scrollbar-width:none] sm:mx-0 sm:grid sm:grid-cols-3 sm:overflow-visible sm:px-0">
        {phoneShots.map((shot, index) => (
          <figure className="w-[15rem] shrink-0 snap-start sm:w-auto" key={shot.src}>
            <PhoneFrame>
              <ShotImage alt={shot.caption} fit="contain" src={shot.src} />
            </PhoneFrame>
            <Caption number={index + 1} text={shot.caption} />
          </figure>
        ))}
      </div>
      <div className="mt-16 grid gap-12 lg:grid-cols-2 lg:gap-8">
        {browserShots.map((shot, index) => (
          <figure key={shot.src}>
            <BrowserFrame address={shot.address}>
              <ShotImage alt={shot.caption} src={shot.src} />
            </BrowserFrame>
            <Caption number={phoneShots.length + index + 1} text={shot.caption} />
          </figure>
        ))}
      </div>
    </LandingSection>
  );
}
