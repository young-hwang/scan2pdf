# img2pdf Options Reference

Application settings are provided through CLI arguments.

- `input paths`: One or more input image files or directories.
- `-o, --output`: Required output PDF path.
- `--page-size`: Output canvas preset. Valid values are `ORIGINAL`, `A4`, `A5`, and `LETTER`.
- `--stretch`: Disable aspect-ratio preservation and stretch source images to the page bounds.
- `--deskew`: Run scan deskew before PDF generation or OCR.
- `--crop`: Crop empty outer margins after optional deskew. Use `--crop=false` to keep the full image bounds.
- `--crop-to-page-size`: When `--crop` is enabled for a fixed page size, crop larger scans down to the target paper window using `--dpi`.
- `--deskew-temp-dir`: Directory for intermediate deskew images. Defaults to `./.img2pdf-temp` inside the current working directory when needed.
- `--ocr`: Enable Tesseract OCR.
- `--lang`: OCR language list. Defaults to `eng`.
- `--tessdata`: Override the tessdata directory path.
- `--dpi`: Target DPI used for fixed page sizes and OCR input hints.
- `--image-compression`: Compression used for PDF image embedding. Valid values are `JPEG` and `LOSSLESS`.
- `--jpeg-quality`: JPEG quality from `1` to `100`. Defaults to `75`.
- `--psm`: Tesseract page segmentation mode.
- `--ocr-text-out`: Optional path to write extracted OCR text.
- `--help`: Show command help.
- `--version`: Show command version.
