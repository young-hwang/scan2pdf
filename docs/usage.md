# scan2pdf Usage Guide

## Overview

`scan2pdf` is a Python CLI for normalizing scanned page images and exporting them as a single PDF.
It is designed for scanned documents that need consistent page orientation, margin cleanup, sizing, and deterministic page ordering.

## Features

- natural filename ordering
- EXIF-aware rotation handling
- portrait or landscape coarse orientation normalization
- optional OpenCV-based deskew
- optional white-margin trimming after rotation correction
- shared scaling across trimmed pages for stable content size
- fixed page-size canvas with centered white padding
- grayscale export option
- deterministic multi-page PDF output

## Requirements

- See [Install Guide](install.md).

## Install

See [Install Guide](install.md).

## Basic Usage

```bash
python -m scan2pdf ./scans ./output/book.pdf
```

## Common Options

```bash
python -m scan2pdf ./scans ./output/book.pdf \
  --page-size LETTER \
  --dpi 300 \
  --orientation portrait \
  --grayscale \
  --deskew \
  --save-normalized-dir ./output/normalized
```

## Margin Trimming

```bash
python -m scan2pdf ./scans ./output/book.pdf \
  --trim-margins \
  --background-threshold 245 \
  --global-scale
```

## Without OpenCV Deskew

```bash
python -m scan2pdf ./scans ./output/book.pdf --no-deskew
```

## Configuration

See [Options Reference](options.md).

## Recommended Starting Point

```bash
python -m scan2pdf ./scans ./output/book.pdf \
  --page-size A4 \
  --dpi 300 \
  --orientation portrait \
  --trim-margins \
  --background-threshold 245 \
  --global-scale
```

## Tests

```bash
python -m unittest discover -s tests
```
