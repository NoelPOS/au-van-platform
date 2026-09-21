#!/bin/sh
# Points this clone's git hooks at the tracked .githooks directory.
# Run once after cloning: scripts/setup-hooks.sh
set -e
cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
echo "core.hooksPath -> $(git config --get core.hooksPath)"
