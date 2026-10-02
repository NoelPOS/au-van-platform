#!/usr/bin/env bash
set -euo pipefail

umask 077
cd "$(dirname "$0")/.."
mkdir -p backups

file="backups/au-van-$(date +%Y%m%d-%H%M%S).dump"
docker compose -f compose.yaml -f compose.prod.yaml exec -T postgres \
  sh -c 'pg_dump --format=custom -U "$POSTGRES_USER" "$POSTGRES_DB"' > "$file.partial"
mv "$file.partial" "$file"

find backups -name 'au-van-*.dump' | sort -r | tail -n +8 | xargs -r rm --
