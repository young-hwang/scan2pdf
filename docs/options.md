# scan2pdf Options Reference

Application settings are provided through CLI arguments.

- `--version`: Prints the installed CLI version and exits.
- `input_dir`: Source directory that contains scanned page images. Files are loaded in natural filename order.
- `output_pdf`: Destination path for the generated PDF file.
- `--page-size`: Output canvas preset. Use `A4`, `A5`, or `LETTER` for a fixed paper size, or `ORIGINAL` to keep each page at its processed image size.
- `--dpi`: DPI used when converting paper-size presets into pixel dimensions. Higher values create larger output pages and usually increase PDF size.
- `--jpeg-quality`: JPEG quality used when embedding page images in the PDF. Lower values reduce file size at the cost of more compression artifacts.
- `--orientation`: Coarse page orientation target. Use `portrait` or `landscape` to normalize all pages to one direction, or `preserve` to keep the source orientation.
- `--grayscale`: Converts output pages to grayscale before writing the PDF. This is useful for reducing size on text-heavy scans.
- `--deskew` and `--no-deskew`: Enables or disables OpenCV-based skew correction for slightly rotated text lines.
- `--trim-margins`: Removes outer white scan margins after EXIF correction, coarse rotation, and deskew.
- `--background-threshold`: Threshold used when detecting white background for margin trimming. Lower values are more conservative; higher values remove more light background areas.
- `--global-scale` and `--no-global-scale`: When trimming margins, keeps one shared scale factor across all pages so the visible content stays at a consistent size.
- `--save-normalized-dir`: Optional directory where normalized page images are written for inspection before or alongside the PDF result.
- `--content-align`: Controls how trimmed content is positioned on the final page. `top-center` is the default and keeps document tops aligned more consistently; `center` restores the classic centered layout.
- `--ocr`: Runs Tesseract OCR and writes a searchable PDF with a text layer instead of an image-only PDF.
- `--ocr-lang`: Tesseract language list. Defaults to `kor+eng`.
- `--tesseract-cmd`: Tesseract executable name or path. Use this when `tesseract` is not available on your default `PATH`.
