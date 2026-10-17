import { readFile } from "node:fs/promises";

const api = "https://api.line.me/v2/bot";
const dataApi = "https://api-data.line.me/v2/bot";
const imagePath = new URL("../../brand/rich-menu.png", import.meta.url);

const dryRun = process.argv.includes("--dry-run");
const token = process.env.LINE_CHANNEL_ACCESS_TOKEN;
const liffUrl = process.env.LIFF_URL?.replace(/\/+$/, "");
const siteUrl = (process.env.SITE_URL || "https://auvan.duckdns.org").replace(/\/+$/, "");

function fail(message) {
  console.error(message);
  process.exit(1);
}

if (!liffUrl) {
  fail("LIFF_URL is missing. Set it to https://liff.line.me/<liff-id>.");
}
if (!token && !dryRun) {
  fail("LINE_CHANNEL_ACCESS_TOKEN is missing. Copy it from the Messaging API channel in the LINE Developers console.");
}

function area(x, y, width, height, action) {
  return { bounds: { x, y, width, height }, action };
}

function link(label, uri) {
  return { type: "uri", label, uri };
}

const richMenu = {
  size: { width: 2500, height: 1686 },
  selected: true,
  name: "AU-Van main menu",
  chatBarText: "Menu",
  areas: [
    area(0, 0, 1875, 900, link("Book a seat", liffUrl)),
    area(1875, 0, 625, 900, link("My tickets", `${liffUrl}/tickets`)),
    area(0, 900, 625, 786, link("How to pay", `${liffUrl}/tickets`)),
    area(625, 900, 625, 786, link("Trip updates", `${liffUrl}/tickets`)),
    area(1250, 900, 625, 786, { type: "message", label: "Contact staff", text: "I need help from staff" }),
    area(1875, 900, 625, 786, link("About AU-Van", siteUrl)),
  ],
};

const image = await readFile(imagePath);
if (image.length > 1024 * 1024) {
  fail(`brand/rich-menu.png is ${image.length} bytes; LINE accepts at most 1 MB.`);
}

if (dryRun) {
  console.log(JSON.stringify(richMenu, null, 2));
  console.log(`Image: brand/rich-menu.png, ${Math.round(image.length / 1024)} KB`);
  process.exit(0);
}

async function line(method, url, body, contentType = "application/json") {
  const headers = { Authorization: `Bearer ${token}` };
  if (body) headers["Content-Type"] = contentType;
  const response = await fetch(url, { method, headers, body });
  const text = await response.text();
  if (!response.ok) fail(`${method} ${url} failed with ${response.status}: ${text}`);
  return text ? JSON.parse(text) : {};
}

console.log("Creating the rich menu...");
const { richMenuId } = await line("POST", `${api}/richmenu`, JSON.stringify(richMenu));
console.log(`Created ${richMenuId}`);

console.log("Uploading brand/rich-menu.png...");
await line("POST", `${dataApi}/richmenu/${richMenuId}/content`, image, "image/png");

console.log("Making it the default for every user...");
await line("POST", `${api}/user/all/richmenu/${richMenuId}`);

const { richmenus } = await line("GET", `${api}/richmenu/list`);
for (const old of richmenus.filter((menu) => menu.richMenuId !== richMenuId)) {
  console.log(`Deleting old rich menu ${old.richMenuId} (${old.name})...`);
  await line("DELETE", `${api}/richmenu/${old.richMenuId}`);
}

console.log("Done. Reopen the chat in LINE to see the new menu.");
