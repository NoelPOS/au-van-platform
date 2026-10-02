#!/usr/bin/env bash
set -euo pipefail

# Wrapped in a function so bash has read the whole script before git checkout rewrites it.
main() {
  cd "$(dirname "$0")/.."

  if [[ ! -f .env ]]; then
    echo "deploy: $(pwd)/.env is missing; fill it from .env.example first" >&2
    exit 1
  fi

  # SECURITY CONTROL: the deploy key's forced command passes its SSH argument through here.
  local sha=${1:-${SSH_ORIGINAL_COMMAND:-}}
  if [[ -n $sha && ! $sha =~ ^[0-9a-f]{40}$ ]]; then
    echo "deploy: expected a full 40-character commit sha" >&2
    exit 1
  fi

  git fetch --quiet origin main
  git checkout --quiet --detach "${sha:-origin/main}"
  echo "deploy: $(git log -1 --format='%h %s')"

  docker compose -f compose.yaml -f compose.prod.yaml \
    up -d --build --remove-orphans --wait --wait-timeout 300
  docker image prune -f
}

main "$@"
