# scan2pdf

`scan2pdf` is a reusable Python CLI for normalizing scanned page images and exporting a single PDF.

## Docs

- [Install Guide](docs/install.md)
- [Usage Guide](docs/usage.md)
- [Options Reference](docs/options.md)

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

## Usage

```bash
python -m scan2pdf ./scans ./output/book.pdf
```

## Tests

```bash
python -m unittest discover -s tests
```
