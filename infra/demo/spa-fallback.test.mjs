import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const read = (name) => readFileSync(new URL(name, import.meta.url), "utf8");
const handler = new Function(`${read("spa-fallback.js")}\nreturn handler;`)();
const rewrite = (uri) => handler({ request: { uri } }).uri;

test("serves index.html for a client-side route", () => {
  assert.equal(rewrite("/admin/payments"), "/index.html");
  assert.equal(rewrite("/admin"), "/index.html");
});

test("leaves a request for a file alone", () => {
  assert.equal(rewrite("/favicon.svg"), "/favicon.svg");
  assert.equal(rewrite("/index.html"), "/index.html");
});

test("runs on the default behaviour and on no other", () => {
  const cdn = read("cdn.tf");
  const start = cdn.indexOf("default_cache_behavior {");
  const defaultBehaviour = cdn.slice(start, cdn.indexOf("\n  }\n", start));
  assert.equal(cdn.split("function_association").length - 1, 1);
  assert.match(defaultBehaviour, /function_association/);
});
