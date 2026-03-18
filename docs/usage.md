# scan2pdf Usage Guide

## Overview

`scan2pdf` is a Python CLI for normalizing scanned page images and exporting them as a single PDF.
It is designed for scanned documents that need consistent page orientation, margin cleanup, sizing, and deterministic page ordering.

## Install

See [Install Guide](install.md).

## Basic Usage

```bash
scan2pdf ./scans ./output/book.pdf
```

## Common Options

```bash
scan2pdf ./scans ./output/book.pdf \
  --page-size LETTER \
  --dpi 300 \
  --jpeg-quality 75 \
  --orientation portrait \
  --grayscale \
  --deskew \
  --save-normalized-dir ./output/normalized
```

## Margin Trimming

```bash
scan2pdf ./scans ./output/book.pdf \
  --trim-margins \
  --background-threshold 245 \
  --global-scale
```

## Without OpenCV Deskew

```bash
scan2pdf ./scans ./output/book.pdf --no-deskew
```

## Configuration

See [Options Reference](options.md).

## Recommended Starting Point

```bash
scan2pdf ./scans ./output/book.pdf \
  --page-size A4 \
  --dpi 200 \
  --jpeg-quality 80 \
  --orientation portrait \
  --trim-margins \
  --background-threshold 245 \
  --global-scale
```

## Tests

```bash
python3 -m unittest discover -s tests
```
