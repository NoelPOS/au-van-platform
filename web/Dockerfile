# The web image. Vite produces static files; nginx serves them and proxies the
# API, so the browser sees one origin.
#
# Base images are pinned by tag *and* digest, and Node matches the version the
# `Web checks` job pins, so the image reproduces CI's build rather than a
# different one.

FROM node:20.20.2-alpine3.23@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293 AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# `vite build` auto-loads .env, .env.local and .env.production from the project
# root before any ARG is considered, and it inlines every VITE_* it finds into
# the bundle as a string literal. .dockerignore keeps those files out of the
# context; this asserts it, because the failure is silent otherwise.
RUN test ! -e .env && test ! -e .env.local && test ! -e .env.production

# VITE_API_BASE_URL is deliberately absent, and adding it here would be a bug.
# web/src/api-base-url.ts falls back to "", which makes every call relative and
# same-origin, and the serving layer routes /api and /actuator: the Vite dev
# proxy, the nginx configuration below, and ADR-007's CloudFront behaviour are
# three implementations of that one contract. An ARG for it would default to
# web/.env.example's http://localhost:8080, which points every visitor's
# browser at their own machine and fails with no server-side error anywhere.
#
# VITE_LIFF_ID is the one value that cannot be deferred: the LINE SDK needs it
# before any network call. It is a public identifier rather than a secret. An
# image built with one is specific to that LINE channel; a build without one
# degrades rather than crashes, because configuredLiffId() returns null for a
# blank value.
ARG VITE_LIFF_ID=""
ENV VITE_LIFF_ID=$VITE_LIFF_ID
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.30.5-alpine3.24@sha256:04a3275f25d766cff8926d2e57b2ff34a783d6b12a702dc98bb82226d2d9a508 AS runtime

# This image already runs its master process as the non-root uid 101 and
# listens on 8080. Stock nginx runs its master as root, and moving it off root
# means rewriting the pid path, the temp paths and the cache ownership by hand.

# The image's entrypoint runs envsubst over /etc/nginx/templates/*.template
# into /etc/nginx/conf.d before nginx starts, so API_UPSTREAM and DNS_RESOLVER
# below are read at container start rather than baked in.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

# Docker's embedded DNS server. Under ECS awsvpc this is the VPC resolver
# instead, which is why it is a variable.
ENV DNS_RESOLVER=127.0.0.11
ENV API_UPSTREAM=api:8080

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
