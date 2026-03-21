# scan2pdf

Turn a folder of scanned images into a cleaned, consistently sized PDF.
It can also merge multiple existing PDFs into one file.

## Quick Start

Install directly from GitHub:

```bash
python3 -m pip install "git+https://github.com/young-hwang/scan2pdf.git"
scan2pdf ./scans ./output/book.pdf
```

This installs the required Python runtime dependencies declared by the package,
including `Pillow` and `pypdf`.

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

This adds the optional deskew dependencies such as `opencv-python`.

If you want searchable OCR PDFs, also install system Tesseract with the language data you need.
For Korean and English OCR, install `kor` and `eng` trained data for your Tesseract package.

## What You Get

- natural filename ordering
- EXIF-aware rotation handling
- portrait or landscape coarse orientation normalization
- optional OpenCV-based deskew
- optional Tesseract-based OCR for searchable PDFs
- optional white-margin trimming after rotation correction
- optional export of normalized page images for inspection
- top-centered page placement for more consistent document alignment
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

This default command writes an image-only PDF. Searchable text is added only when `--ocr` is explicitly enabled.

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

Force classic centered placement instead of the default top-centered layout:

```bash
scan2pdf ./scans ./output/book.pdf \
  --trim-margins \
  --content-align center
```

Save normalized page images for inspection:

```bash
scan2pdf ./scans ./output/book.pdf \
  --save-normalized-dir ./output/normalized
```

This option only saves the intermediate normalized page images. It does not change the PDF output mode.

Generate a searchable OCR PDF:

```bash
scan2pdf ./scans ./output/book.pdf \
  --ocr \
  --ocr-lang kor+eng
```

Combine trimming, normalized-page export, and OCR:

```bash
scan2pdf ./scans ./output/book-ocr.pdf \
  --trim-margins \
  --background-threshold 245 \
  --save-normalized-dir ./output/normalized \
  --ocr \
  --ocr-lang kor+eng
```

Merge all PDFs in a folder into one:

```bash
scan2pdf merge ./pdfs ./combined.pdf
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
