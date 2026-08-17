#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

OUTPUT="${ROOT_DIR}/function.zip"
LAMBDA_FILE="${ROOT_DIR}/lambda_function.py"

if [ ! -f "$LAMBDA_FILE" ]; then
  echo "Error: $LAMBDA_FILE not found"
  exit 1
fi

rm -f "$OUTPUT"

cd "$ROOT_DIR"
zip "$OUTPUT" "lambda_function.py"

echo "Created $OUTPUT"