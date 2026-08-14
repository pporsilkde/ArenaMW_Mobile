#!/usr/bin/env bash
set -euo pipefail
exec "$(dirname "$0")/buildscripts/rebuild-ng-gl4es.sh" "$@"
