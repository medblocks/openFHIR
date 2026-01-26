#!/usr/bin/env bash
set -euo pipefail

URL="http://localhost:8080/openfhir/toopenehr"
ROOT="${1:-.}"

tmp_resp="$(mktemp)"

cleanup() {
  rm -f "$tmp_resp"
}
trap cleanup EXIT

find "$ROOT" -type d -name kds -print0 | while IFS= read -r -d '' kds_dir; do
  find "$kds_dir" -type f -name "*.json" ! -name "Composition-*.json" -print0 \
  | while IFS= read -r -d '' f; do
      dir="$(dirname "$f")"
      base="$(basename "$f")"
      out_file="$dir/Composition-${base#*-}"

      echo "POST: $f"

      code="$(
        curl -sS \
          -o "$tmp_resp" \
          -w '%{http_code}' \
          -H 'Content-Type: application/json' \
          --data-binary "@$f" \
          "$URL" \
          || echo "000"
      )"

      if [[ "$code" == "200" ]]; then
        mv "$tmp_resp" "$out_file"
        echo "  ✔ wrote $out_file"
      else
        echo "  ✘ skipped (HTTP $code)"
      fi
    done
done
