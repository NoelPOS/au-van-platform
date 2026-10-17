import { useQuery } from "@tanstack/react-query";
import { BrowserFrame, ShotImage } from "./DeviceFrames";
import { LandingSection } from "./LandingSection";

const video = "/landing/intro.mp4";
const poster = "/landing/video-poster.webp";

async function videoExists(): Promise<boolean> {
  // A missing file still answers 200 with the app's index.html, so check the type.
  const response = await fetch(video, { method: "HEAD" });
  return response.headers.get("Content-Type")?.startsWith("video/") ?? false;
}

export function IntroVideo() {
  const exists = useQuery({
    queryKey: ["landing", "video"],
    queryFn: videoExists,
    staleTime: Infinity,
  });

  return (
    <LandingSection
      id="walk-through"
      intro="One booking, from a student’s phone to the transport desk and back to LINE."
      kicker="Walk-through"
      title="Watch a booking go through"
    >
      <BrowserFrame address="auvan.duckdns.org" aspect="aspect-video">
        {exists.data ? (
          <video
            className="size-full bg-ink"
            controls
            poster={poster}
            preload="none"
            src={video}
          />
        ) : (
          <ShotImage alt="Walk-through video" note="Video to come" src={poster} />
        )}
      </BrowserFrame>
    </LandingSection>
  );
}
