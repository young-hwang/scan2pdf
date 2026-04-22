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

## Recommended A5 Scan Profile

For scanned book pages that need `deskew` and `crop`, and where you want to avoid text appearing larger because the crop area shrank to only the detected content bounds, use this profile:

```bash
./gradlew :cli:run --args="/absolute/path/to/images \
  --output /absolute/path/to/output/book-a5-dpi300-q75-deskew-crop.pdf \
  --page-size A5 \
  --dpi 300 \
  --image-compression JPEG \
  --jpeg-quality 75 \
  --deskew \
  --crop \
  --crop-to-page-size \
  --deskew-temp-dir /absolute/path/to/.img2pdf-temp/book-a5"
```

Why this combination is useful:

- `--page-size A5`: Forces a fixed A5 output canvas.
- `--dpi 300`: Sets the target pixel window used for fixed-size crop and embedding.
- `--image-compression JPEG --jpeg-quality 75`: Keeps color output with moderate file size.
- `--deskew`: Straightens scanned pages before embedding.
- `--crop`: Removes empty outer margins.
- `--crop-to-page-size`: Prevents overly aggressive content-only cropping from making text appear larger on the final page by constraining crop to the target page window.
- `--deskew-temp-dir`: Stores intermediate deskew/crop images in a predictable location.

When using `./gradlew :cli:run`, prefer absolute input and output paths because the task executes from the `cli` module directory.
