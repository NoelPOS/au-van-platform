"use strict";

// Stands in for LINE's id-token verify endpoint in the e2e stack only, echoing the id_token as `sub`.

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
      // An empty subject would put every caller on one app_users row.
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
