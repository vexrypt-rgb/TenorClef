#!/usr/bin/env bash
# Ostinato: TenorClef should reach Ostinato only through baritone.api.*. This gate fails when a file
# outside the allowlist imports a Baritone internal (baritone.Baritone, baritone.pathing.*, baritone.utils.*,
# baritone.process.*, baritone.altoclef.*, ...), including //$$ preprocessor imports and fully-qualified
# "baritone.<internal>." class references. The allowlist only shrinks: remove a file once it is ported.
set -euo pipefail
cd "$(dirname "$0")/.."
allow=scripts/baritone-internal-imports.allow
found=$(grep -rlP '^\s*(//\$\$\s*)?import\s+(static\s+)?baritone\.(?!api\.)[A-Za-z]|(?<![\w.])baritone\.(Baritone\b|pathing\.|utils\.|process\.|altoclef\.|behavior\.|movement\.|swarm\.)' src --include=*.java | sort || true)
expected=$(grep -v '^\s*#' "$allow" | sed '/^\s*$/d' | sort)
new=$(comm -23 <(echo "$found") <(echo "$expected") | sed '/^$/d')
gone=$(comm -13 <(echo "$found") <(echo "$expected") | sed '/^$/d')
if [ -n "$new" ]; then
  echo "::error::These files use Baritone/Ostinato internals; use baritone.api.* (see Ostinato docs/DRIVER_API.md):"
  echo "$new"
  exit 1
fi
if [ -n "$gone" ]; then
  echo "::error::These files no longer use Baritone internals; delete them from $allow:"
  echo "$gone"
  exit 1
fi
echo "baritone import gate: ok ($(echo "$found" | sed '/^$/d' | wc -l) allowlisted files still use internals)"
