# scan2pdf Usage Guide

## Overview

`scan2pdf` is a Python CLI for normalizing scanned page images and exporting them as a single PDF.
It also supports merging multiple existing PDFs into one file.
It is designed for scanned documents that need consistent page orientation, margin cleanup, sizing, and deterministic page ordering.

## Install

See [Install Guide](install.md).

## Basic Usage

```bash
scan2pdf ./scans ./output/book.pdf
```

By default this produces an image-only PDF. Searchable text is added only when you pass `--ocr`.

## Merge Existing PDFs

```bash
scan2pdf merge ./pdfs ./combined.pdf
```

The merge command reads PDF files from the input folder in natural filename order and writes one combined PDF.

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

`--save-normalized-dir` writes processed page images for inspection, but it does not change the generated PDF into an OCR PDF.

## Margin Trimming

```bash
scan2pdf ./scans ./output/book.pdf \
  --trim-margins \
  --background-threshold 245 \
  --global-scale
```

## Top-Aligned Document Placement

`scan2pdf` now uses `--content-align top-center` by default so trimmed pages stay aligned to the top of the canvas instead of drifting vertically.

To restore classic centered placement:

```bash
scan2pdf ./scans ./output/book.pdf \
  --trim-margins \
  --content-align center
```

## Without OpenCV Deskew

```bash
scan2pdf ./scans ./output/book.pdf --no-deskew
```

## Searchable OCR PDF

```bash
scan2pdf ./scans ./output/book.pdf \
  --ocr \
  --ocr-lang kor+eng
```

To keep debug images while also producing an OCR PDF:

```bash
scan2pdf ./scans ./output/book-ocr.pdf \
  --trim-margins \
  --save-normalized-dir ./output/normalized \
  --ocr \
  --ocr-lang kor+eng
```

If Tesseract is not on your default `PATH`, specify it directly:

```bash
scan2pdf ./scans ./output/book.pdf \
  --ocr \
  --tesseract-cmd /opt/homebrew/bin/tesseract
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
