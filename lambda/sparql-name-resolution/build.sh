#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BUILD_DIR="${ROOT_DIR}/package"
ZIP_FILE="${ROOT_DIR}/function.zip"

rm -rf "${BUILD_DIR}" "${ZIP_FILE}"
mkdir -p "${BUILD_DIR}"

python3 -m pip install \
  --target "${BUILD_DIR}" \
  requests \
  python-dotenv

cd "${BUILD_DIR}"
zip -r "${ZIP_FILE}" .

cd "${ROOT_DIR}"
zip "${ZIP_FILE}" dummy_lambda.py .env

echo "Created ${ZIP_FILE}"