# LINE rich menu

`rich-menu.mjs` replaces the Official Account's rich menu with the AU-Van one:
it creates the menu, uploads `brand/rich-menu.png`, makes it the default for
every user, and only then deletes every other rich menu on the channel.

```sh
LINE_CHANNEL_ACCESS_TOKEN=... LIFF_URL=https://liff.line.me/<liff-id> node scripts/line/rich-menu.mjs
```

The token is in the LINE Developers console, under the Messaging API channel,
on the Messaging API tab: **Channel access token (long-lived)**. `SITE_URL`
sets the "About AU-Van" link and defaults to `https://auvan.duckdns.org`. Add
`--dry-run` to print the request body without calling LINE.

A rich menu set this way wins over one made in LINE Official Account Manager,
so switch that one off there if it still shows.

## Changing the image

The image is `rich-menu.html` rendered at 2500x1686. With `npm ci` done in
`web/` (for the fonts) and `tests/e2e/` (for Playwright):

```sh
cd tests/e2e
npx playwright screenshot --viewport-size "2500, 1686" --wait-for-timeout 500 \
  "file://$PWD/../../scripts/line/rich-menu.html" ../../brand/rich-menu.png
```

Keep the tile edges where `rich-menu.mjs` puts its tap areas.
