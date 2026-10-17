import { Composition } from "remotion";
import "./fonts";
import { Intro, introFrames } from "./Intro";

export function Root() {
  return (
    <Composition
      component={Intro}
      durationInFrames={introFrames}
      fps={30}
      height={1080}
      id="Intro"
      width={1920}
    />
  );
}
