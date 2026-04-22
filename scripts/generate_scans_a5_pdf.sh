#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
INPUT_DIR="${REPO_ROOT}/scans"
OUTPUT_PDF="${REPO_ROOT}/output/scans-a5-dpi300-q75-deskew-crop.pdf"
DESKEW_TEMP_DIR="${REPO_ROOT}/.img2pdf-temp/scans-a5"

cd "${REPO_ROOT}"

./gradlew :cli:run --args="${INPUT_DIR} \
  --output ${OUTPUT_PDF} \
  --page-size A5 \
  --dpi 300 \
  --image-compression JPEG \
  --jpeg-quality 75 \
  --deskew \
  --crop \
  --crop-to-page-size \
  --deskew-temp-dir ${DESKEW_TEMP_DIR}"
