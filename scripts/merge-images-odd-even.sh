#!/usr/bin/env bash

set -euo pipefail

odd_dir="${1:-01}"
even_dir="${2:-02}"
out_dir="${3:-03}"

if [ ! -d "$odd_dir" ]; then
  echo "Missing directory: $odd_dir" >&2
  exit 1
fi

if [ ! -d "$even_dir" ]; then
  echo "Missing directory: $even_dir" >&2
  exit 1
fi

mkdir -p "$out_dir"

odd_files=()
while IFS= read -r file; do
  odd_files+=("$file")
done < <(find "$odd_dir" -maxdepth 1 -type f | sort -V)

even_files=()
while IFS= read -r file; do
  even_files+=("$file")
done < <(find "$even_dir" -maxdepth 1 -type f | sort -V)

if [ "${#odd_files[@]}" -gt "${#even_files[@]}" ]; then
  max="${#odd_files[@]}"
else
  max="${#even_files[@]}"
fi

idx=1
for ((i=0; i<max; i++)); do
  if [ "$i" -lt "${#odd_files[@]}" ]; then
    printf -v out_file "%s/%04d.jpeg" "$out_dir" "$idx"
    cp -- "${odd_files[$i]}" "$out_file"
    idx=$((idx + 1))
  fi

  if [ "$i" -lt "${#even_files[@]}" ]; then
    printf -v out_file "%s/%04d.jpeg" "$out_dir" "$idx"
    cp -- "${even_files[$i]}" "$out_file"
    idx=$((idx + 1))
  fi
done

echo "Created $((idx - 1)) images in $out_dir"
