# LINE rich menu

The API installs the rich menu itself. On startup it checks the Official
Account's default rich menu, and if its name is not `RichMenuSync.MENU_NAME`, it
creates the new menu, uploads `api/src/main/resources/line/rich-menu.png`, makes
it the default for every user, and then deletes every other rich menu. It uses
`LINE_CHANNEL_ACCESS_TOKEN` and `LINE_LIFF_URL`, and does nothing while either
is empty or `LINE_MESSAGING_ENABLED` is `false`.

A rich menu set this way wins over one made in LINE Official Account Manager,
so switch that one off there if it still shows.

## Changing the menu

The image is `rich-menu.html` rendered at 2500x1686. With `npm ci` done in
`web/` (for the fonts) and `tests/e2e/` (for Playwright):

```sh
cd tests/e2e
npx playwright screenshot --viewport-size "2500, 1686" --wait-for-timeout 500 \
  "file://$PWD/../../scripts/line/rich-menu.html" ../../brand/rich-menu.png
cp ../../brand/rich-menu.png ../../api/src/main/resources/line/rich-menu.png
```

Keep the tile edges where `RichMenu.java` puts its tap areas. Then bump
`MENU_NAME` in `RichMenuSync.java` (`au-van-menu-v1` to `au-van-menu-v2`), and
the next deploy installs the new menu.
