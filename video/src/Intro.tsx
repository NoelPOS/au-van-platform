import { linearTiming, TransitionSeries } from "@remotion/transitions";
import { slide } from "@remotion/transitions/slide";
import { Easing } from "remotion";
import { Board } from "./scenes/Board";
import { EndCard } from "./scenes/EndCard";
import { StaffReview } from "./scenes/StaffReview";
import { StudentFlow } from "./scenes/StudentFlow";
import { Title } from "./scenes/Title";
import { UnderTheHood } from "./scenes/UnderTheHood";
import { tear } from "./transitions/tear";

const scene = { title: 135, board: 165, student: 465, staff: 225, hood: 315, end: 150 };
const tearFrames = 26;
const slideFrames = 20;

export const introFrames =
  Object.values(scene).reduce((total, frames) => total + frames, 0) -
  2 * tearFrames -
  3 * slideFrames;

const tearCut = {
  presentation: tear(),
  timing: linearTiming({ durationInFrames: tearFrames, easing: Easing.bezier(0.5, 0, 0.7, 0.4) }),
};

function slideCut(direction: "from-right" | "from-bottom") {
  return {
    presentation: slide({ direction }),
    timing: linearTiming({ durationInFrames: slideFrames, easing: Easing.bezier(0.2, 0.7, 0.2, 1) }),
  };
}

export function Intro() {
  return (
    <TransitionSeries>
      <TransitionSeries.Sequence durationInFrames={scene.title}>
        <Title />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition {...tearCut} />
      <TransitionSeries.Sequence durationInFrames={scene.board}>
        <Board />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition {...slideCut("from-right")} />
      <TransitionSeries.Sequence durationInFrames={scene.student}>
        <StudentFlow screenshots />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition {...slideCut("from-right")} />
      <TransitionSeries.Sequence durationInFrames={scene.staff}>
        <StaffReview screenshots />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition {...slideCut("from-bottom")} />
      <TransitionSeries.Sequence durationInFrames={scene.hood}>
        <UnderTheHood />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition {...tearCut} />
      <TransitionSeries.Sequence durationInFrames={scene.end}>
        <EndCard />
      </TransitionSeries.Sequence>
    </TransitionSeries>
  );
}
