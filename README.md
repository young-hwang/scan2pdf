# img2pdf

Convert scanned images into a cleaned PDF with optional deskew and OCR.

## Quick Start

Build the CLI:

```bash
./gradlew :cli:installDist
```

Run the generated launcher:

```bash
./cli/build/install/cli/bin/img2pdf-cli ./images --output ./output/book.pdf
```

For ad hoc execution during development, you can also run through Gradle:

```bash
./gradlew :cli:run --args="./images --output ./output/book.pdf"
```

## What You Get

- natural filename ordering
- fixed page-size output with optional aspect-ratio preservation
- optional deskew before PDF generation
- optional crop of empty scan margins
- cropped scans are scaled from detected content bounds so mixed A4/A5 inputs keep more consistent text size
- optional page-sized crop window for larger-bed scans when `--page-size` and `--dpi` are fixed
- optional Tesseract OCR
- configurable JPEG or lossless image embedding
- optional OCR text export

## Common Commands

Basic conversion:

```bash
./cli/build/install/cli/bin/img2pdf-cli ./images --output ./output/book.pdf
```

OCR-enabled conversion:

```bash
./cli/build/install/cli/bin/img2pdf-cli ./images \
  --output ./output/book.pdf \
  --ocr \
  --lang kor+eng
```

Letter-sized output at 300 DPI:

```bash
./cli/build/install/cli/bin/img2pdf-cli ./images \
  --output ./output/book.pdf \
  --page-size LETTER \
  --dpi 300
```

Deskew and crop margins while keeping intermediate files under a fixed directory:

```bash
./cli/build/install/cli/bin/img2pdf-cli ./images \
  --output ./output/book.pdf \
  --deskew \
  --crop \
  --deskew-temp-dir ./.img2pdf-temp
```

Crop A4-bed scans down to an A5 page window before PDF generation:

```bash
./cli/build/install/cli/bin/img2pdf-cli ./images \
  --output ./output/book.pdf \
  --page-size A5 \
  --dpi 300 \
  --crop \
  --crop-to-page-size
```

Use lossless image embedding:

```bash
./cli/build/install/cli/bin/img2pdf-cli ./images \
  --output ./output/book.pdf \
  --image-compression LOSSLESS
```

## Development

Run tests:

```bash
./gradlew test
```

Check the CLI help:

```bash
./gradlew :cli:run --args="--help"
```

## Documentation

- [Install Guide](docs/install.md)
- [Usage Guide](docs/usage.md)
- [Options Reference](docs/options.md)
