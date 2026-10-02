#!/usr/bin/env bash
set -euo pipefail

source_script=$(cd "$(dirname "$0")" && pwd)/deploy.sh
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unset SSH_ORIGINAL_COMMAND

mkdir -p "$work/bin" "$work/origin/deploy"
printf '#!/bin/sh\necho "$*" >> "%s/docker.log"\n' "$work" > "$work/bin/docker"
chmod +x "$work/bin/docker"
export PATH="$work/bin:$PATH"

git init --quiet -b main "$work/origin"
cp "$source_script" "$work/origin/deploy/"
git -C "$work/origin" add deploy
commit() { git -C "$work/origin" -c user.name=test -c user.email=test@example.com commit --quiet --allow-empty -m "$1"; }
commit first
first=$(git -C "$work/origin" rev-parse HEAD)
git clone --quiet --single-branch --branch main "$work/origin" "$work/server"
commit second
second=$(git -C "$work/origin" rev-parse HEAD)
deploy=$work/server/deploy/deploy.sh

fail() { echo "FAIL: $1" >&2; exit 1; }
head_is() { [[ $(git -C "$work/server" rev-parse HEAD) == "$1" ]]; }

"$deploy" >/dev/null 2>&1 && fail "deployed without a .env"
[[ ! -e $work/docker.log ]] || fail "called docker without a .env"

touch "$work/server/.env"
SSH_ORIGINAL_COMMAND=${first:0:7} "$deploy" >/dev/null 2>&1 && fail "accepted an abbreviated sha"
[[ ! -e $work/docker.log ]] || fail "called docker for an abbreviated sha"

SSH_ORIGINAL_COMMAND=$first "$deploy" >/dev/null
head_is "$first" || fail "did not check out the sha passed through SSH"
grep -qx 'compose -f compose.yaml -f compose.prod.yaml up -d --build --remove-orphans --wait --wait-timeout 300' \
  "$work/docker.log" || fail "did not restart the production stack"
grep -qx 'image prune -f' "$work/docker.log" || fail "did not prune dangling images"

"$deploy" >/dev/null
head_is "$second" || fail "did not fetch and check out the tip of main by default"

echo "deploy.sh: all checks passed"
