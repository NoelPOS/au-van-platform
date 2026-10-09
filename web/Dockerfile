# The web image. Vite produces static files; nginx serves them and proxies the
# API, so the browser sees one origin.
#
# Base images are pinned by tag *and* digest, and Node matches the version the
# `Web checks` job pins, so the image reproduces CI's build rather than a
# different one.
#
# That match is checked, not just intended: `Web checks` holds the version in
# NODE_VERSION (.github/workflows/ci.yml) and its first step reads the FROM
# line below and fails when the two differ. Bumping Node means editing both,
# in one pull request -- the tag and the digest here, NODE_VERSION there.

FROM node:22.23.2-alpine3.23@sha256:72c5815a06aed9a2273aea5628d74d348af57843a7b547af2fe53dd3e4b95261 AS build

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
# web/src/services/apiBaseUrl.ts falls back to "", which makes every call relative and
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

# The Official Account's public basic ID (such as @123abcde), for the student
# app's "Add AU-Van on LINE" prompt. Public, like the LIFF ID; blank hides it.
ARG VITE_LINE_OA_ID=""
ENV VITE_LINE_OA_ID=$VITE_LINE_OA_ID

# Where students pay: a PromptPay mobile number or 13-digit ID, and the name the
# bank app should show. Public by design, since the QR carries it. Blank shows a
# sample QR that pays no one.
ARG VITE_PROMPTPAY_ID=""
ENV VITE_PROMPTPAY_ID=$VITE_PROMPTPAY_ID
ARG VITE_PROMPTPAY_NAME=""
ENV VITE_PROMPTPAY_NAME=$VITE_PROMPTPAY_NAME

# The Playwright suite's sign-in control, and the only build argument here that
# must never be set for a real image (ADR-013). compose.e2e.yaml sets it to
# "true"; nothing else does. The default is an empty string rather than absent
# so the build is identical either way, and web/src/services/liffConfig.ts compares
# for the exact string "true" for that reason.
#
# An image built without it contains none of that code: the flag is inlined, the
# module-level constant folds, and the control is tree-shaken. `Container
# checks` greps the served bundle for its marker, which is the assertion that
# actually keeps this honest -- a comment here would not.
ARG VITE_E2E_AUTH=""
ENV VITE_E2E_AUTH=$VITE_E2E_AUTH
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
