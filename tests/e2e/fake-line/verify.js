"use strict";

// A stand-in for LINE's id-token verification endpoint, for the Playwright
// stack and nothing else (ADR-013). `compose.e2e.yaml` is the only thing that
// points an API at it, through `auth.line.api-base-url`.
//
// It echoes the submitted id_token back as `sub`. That one line is what gives
// every test its own identity with no fixture table: a spec signs in as
// "e2e-student-1738..." and the API creates and finds exactly that app_users
// row. `name` is the same string, so the page says who it signed in as.
//
// It answers with LINE's real `iss` and the configured `aud` because the
// verifier demands both. That is the point: the verification the API performs
// is unchanged, and an answer this service could not produce -- from real LINE,
// about a made-up token -- is refused exactly as it is today.

const http = require("node:http");

const channelId = process.env.LINE_CHANNEL_ID || "e2e-login-channel";

function reply(response, status, body) {
  response.writeHead(status, { "content-type": "application/json" });
  response.end(JSON.stringify(body));
}

http
  .createServer((request, response) => {
    if (request.method === "GET" && request.url === "/health") {
      response.writeHead(204);
      response.end();
      return;
    }
    if (request.method !== "POST" || !request.url.startsWith("/oauth2/v2.1/verify")) {
      reply(response, 404, { error: "not_found" });
      return;
    }

    let form = "";
    request.on("data", (chunk) => (form += chunk));
    request.on("end", () => {
      const submitted = new URLSearchParams(form);
      const idToken = (submitted.get("id_token") || "").trim();
      // Refused rather than echoed: an empty subject would put every caller on
      // one app_users row, and the verifier's own blank-sub check should not be
      // the only thing standing between a test and that.
      if (idToken === "") {
        reply(response, 400, { error: "invalid_request", error_description: "id_token is required" });
        return;
      }
      if (submitted.get("client_id") !== channelId) {
        reply(response, 400, { error: "invalid_request", error_description: "client_id is not this channel" });
        return;
      }
      reply(response, 200, {
        iss: "https://access.line.me",
        sub: idToken,
        aud: channelId,
        name: idToken,
      });
    });
  })
  .listen(8080);
