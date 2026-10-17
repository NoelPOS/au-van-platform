import "@fontsource-variable/fraunces/opsz.css";
import "@fontsource-variable/fraunces/opsz-italic.css";
import "@fontsource-variable/instrument-sans";
import "@fontsource-variable/jetbrains-mono";
import { continueRender, delayRender } from "remotion";

const faces = [
  '400 1em "Fraunces Variable"',
  'italic 400 1em "Fraunces Variable"',
  '400 1em "Instrument Sans Variable"',
  '600 1em "Instrument Sans Variable"',
  '400 1em "JetBrains Mono Variable"',
];

const handle = delayRender("Loading fonts");
Promise.all(faces.map((face) => document.fonts.load(face))).then(() =>
  continueRender(handle),
);
