# scan2pdf

Turn a folder of scanned images into a cleaned, consistently sized PDF.

## Quick Start

Install directly from GitHub:

```bash
python3 -m pip install "git+https://github.com/young-hwang/scan2pdf.git"
scan2pdf ./scans ./output/book.pdf
```

If you prefer an isolated CLI install:

```bash
pipx install "git+https://github.com/young-hwang/scan2pdf.git"
```

If you want to clone the repository and install from source:

```bash
git clone https://github.com/young-hwang/scan2pdf.git
cd scan2pdf
python3 -m pip install .
scan2pdf ./scans ./output/book.pdf
```

If you want deskew support after cloning the repository:

```bash
python3 -m pip install ".[deskew]"
```

If you want deskew support from GitHub without cloning:

```bash
python3 -m pip install "scan2pdf[deskew] @ git+https://github.com/young-hwang/scan2pdf.git"
```

If you want searchable OCR PDFs, also install system Tesseract with the language data you need.
For Korean and English OCR, install `kor` and `eng` trained data for your Tesseract package.

## What You Get

- natural filename ordering
- EXIF-aware rotation handling
- portrait or landscape coarse orientation normalization
- optional OpenCV-based deskew
- optional Tesseract-based OCR for searchable PDFs
- optional white-margin trimming after rotation correction
- shared scaling across trimmed pages for stable content size
- fixed page-size canvas with centered white padding
- grayscale export option
- configurable JPEG quality for PDF page embedding
- deterministic multi-page PDF output

## Basic Usage

```bash
scan2pdf ./scans ./output/book.pdf
```

Input:

- `./scans`: directory containing page images

Output:

- `./output/book.pdf`: generated multi-page PDF

## Common Commands

Basic conversion:

```bash
scan2pdf ./scans ./output/book.pdf
```

Letter-sized grayscale output:

```bash
scan2pdf ./scans ./output/book.pdf \
  --page-size LETTER \
  --dpi 300 \
  --orientation portrait \
  --grayscale \
  --jpeg-quality 75
```

Trim white borders and keep a shared scale across pages:

```bash
scan2pdf ./scans ./output/book.pdf \
  --trim-margins \
  --background-threshold 245 \
  --global-scale
```

Save normalized page images for inspection:

```bash
scan2pdf ./scans ./output/book.pdf \
  --save-normalized-dir ./output/normalized
```

Generate a searchable OCR PDF:

```bash
scan2pdf ./scans ./output/book.pdf \
  --ocr \
  --ocr-lang kor+eng
```

## Development

Run tests:

```bash
python3 -m unittest discover -s tests
```

Check the installed CLI version:

```bash
scan2pdf --version
```

## Documentation

- [Install Guide](docs/install.md)
- [Usage Guide](docs/usage.md)
- [Options Reference](docs/options.md)
