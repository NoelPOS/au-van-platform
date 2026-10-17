# Intro video

The landing page walk-through, built with Remotion. It is a standalone project:
nothing in `web/` or CI depends on it.

```sh
npm install
npm run studio   # preview and scrub in the browser
npm run render   # out/intro.mp4, 1920x1080, 30 fps, H.264, no audio
npm run poster   # out/video-poster.webp
```

Copy both files into `web/public/landing/` to publish them.

## Real screenshots

The phone and browser screens are React mocks. To use the captured
screenshots instead, run `npm run assets` (copies `web/public/landing/*.webp`
into `public/`), then set `screenshots` to `true` on `StudentFlow` or
`StaffReview` in `src/Intro.tsx`.
