from __future__ import annotations

import argparse
import math
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from importlib.metadata import PackageNotFoundError, version as package_version
from pathlib import Path
from typing import Sequence

from . import __version__
from .core import (
    SUPPORTED_EXTENSIONS,
    CanvasSize,
    compute_uniform_scale,
    fit_with_padding,
    iter_image_files,
    iter_pdf_files,
    normalize_skew_angle,
    page_size_to_pixels,
    scale_dimensions,
    should_rotate_for_orientation,
)


def get_version() -> str:
    try:
        return package_version("scan2pdf")
    except PackageNotFoundError:
        return __version__


def require_pillow():
    try:
        from PIL import Image, ImageOps
    except ImportError as exc:  # pragma: no cover - exercised in runtime only
        raise RuntimeError(
            "Pillow is required. Install it with: pip install Pillow"
        ) from exc
    return Image, ImageOps


def require_cv2():
    try:
        import cv2  # type: ignore
    except ImportError as exc:  # pragma: no cover - exercised in runtime only
        raise RuntimeError(
            "Deskew requested but OpenCV is not installed. Install it with: "
            "pip install opencv-python"
        ) from exc
    return cv2


def require_pypdf():
    try:
        from pypdf import PdfReader, PdfWriter
    except ImportError as exc:  # pragma: no cover - exercised in runtime only
        raise RuntimeError(
            "OCR PDF export requires pypdf. Install it with: pip install pypdf"
        ) from exc
    return PdfReader, PdfWriter


def command_exists(command: str) -> bool:
    command_path = Path(command).expanduser()
    if command_path.parent != Path("."):
        return command_path.is_file()
    return shutil.which(command) is not None


def require_tesseract(command: str):
    if not command_exists(command):
        raise RuntimeError(
            "OCR requested but Tesseract is not installed or not on PATH. "
            "Install Tesseract and the desired language data, or pass "
            "--tesseract-cmd with the executable path."
        )
    return command


@dataclass
class PreparedPage:
    source_path: Path
    image: object
    cropped_size: tuple[int, int]


def _weighted_median(
    values: list[float],
    weights: list[float],
) -> float | None:
    if not values or not weights or len(values) != len(weights):
        return None

    ordered = sorted(zip(values, weights), key=lambda item: item[0])
    total_weight = sum(weight for _, weight in ordered)
    if total_weight <= 0:
        return None

    threshold = total_weight / 2
    cumulative = 0.0
    for value, weight in ordered:
        cumulative += weight
        if cumulative >= threshold:
            return value
    return ordered[-1][0]


def _component_bboxes(mask) -> list[tuple[int, int, int, int, int]]:
    import numpy as np  # type: ignore

    height, width = mask.shape
    visited = np.zeros((height, width), dtype=bool)
    components: list[tuple[int, int, int, int, int]] = []

    for start_y, start_x in np.argwhere(mask):
        if visited[start_y, start_x]:
            continue

        stack = [(int(start_y), int(start_x))]
        visited[start_y, start_x] = True
        min_x = max_x = int(start_x)
        min_y = max_y = int(start_y)
        area = 0

        while stack:
            y, x = stack.pop()
            area += 1
            min_x = min(min_x, x)
            max_x = max(max_x, x)
            min_y = min(min_y, y)
            max_y = max(max_y, y)

            for next_y in range(max(0, y - 1), min(height, y + 2)):
                for next_x in range(max(0, x - 1), min(width, x + 2)):
                    if visited[next_y, next_x] or not mask[next_y, next_x]:
                        continue
                    visited[next_y, next_x] = True
                    stack.append((next_y, next_x))

        components.append((min_x, min_y, max_x + 1, max_y + 1, area))

    return components


def detect_skew_angle(image: Image.Image, max_angle: float = 10.0) -> float | None:
    cv2 = require_cv2()
    import numpy as np  # type: ignore

    grayscale = np.array(image.convert("L"))
    blurred = cv2.GaussianBlur(grayscale, (5, 5), 0)
    _, binary = cv2.threshold(
        blurred, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )

    horizontal_kernel_width = max(15, image.width // 30)
    horizontal_kernel = cv2.getStructuringElement(
        cv2.MORPH_RECT,
        (horizontal_kernel_width, 3),
    )
    connected = cv2.morphologyEx(binary, cv2.MORPH_CLOSE, horizontal_kernel)
    edges = cv2.Canny(connected, 50, 150, apertureSize=3)
    min_line_length = max(30, image.width // 12)
    lines = cv2.HoughLinesP(
        edges,
        rho=1,
        theta=np.pi / 180,
        threshold=max(30, image.width // 20),
        minLineLength=min_line_length,
        maxLineGap=max(10, image.width // 80),
    )

    if lines is not None:
        angles: list[float] = []
        weights: list[float] = []
        for line in lines[:, 0]:
            x1, y1, x2, y2 = (int(value) for value in line)
            dx = x2 - x1
            dy = y2 - y1
            if dx == 0 and dy == 0:
                continue
            angle = math.degrees(math.atan2(dy, dx))
            while angle <= -90:
                angle += 180
            while angle > 90:
                angle -= 180
            if abs(angle) > max_angle:
                continue
            length = math.hypot(dx, dy)
            if length < min_line_length:
                continue
            angles.append(angle)
            weights.append(length)

        weighted_angle = _weighted_median(angles, weights)
        if weighted_angle is not None and not math.isnan(weighted_angle):
            return weighted_angle

    coords = cv2.findNonZero(connected)
    if coords is None or len(coords) < 200:
        return None

    angle = cv2.minAreaRect(coords)[-1]
    angle = normalize_skew_angle(float(angle))
    if math.isnan(angle) or abs(angle) > max_angle:
        return None
    return angle


def deskew_image(image: Image.Image, max_angle: float = 10.0) -> Image.Image:
    Image, _ = require_pillow()
    angle = detect_skew_angle(image, max_angle=max_angle)
    if angle is None or abs(angle) < 0.1:
        return image
    return image.rotate(
        angle,
        resample=Image.Resampling.BICUBIC,
        expand=True,
        fillcolor="white",
    )


def prepare_image(
    image: Image.Image,
    *,
    orientation: str,
    deskew: bool,
) -> Image.Image:
    Image, ImageOps = require_pillow()
    normalized = ImageOps.exif_transpose(image)

    if should_rotate_for_orientation(
        normalized.width, normalized.height, orientation
    ):
        normalized = normalized.rotate(
            90, expand=True, resample=Image.Resampling.BICUBIC, fillcolor="white"
        )

    if deskew:
        normalized = deskew_image(normalized)

    return normalized.convert("RGB")


def detect_content_box(
    image: Image.Image,
    *,
    background_threshold: int,
) -> tuple[int, int, int, int] | None:
    try:
        import numpy as np  # type: ignore
    except ImportError:
        grayscale = image.convert("L")
        mask = grayscale.point(
            lambda value: 255 if value < background_threshold else 0,
            mode="1",
        )
        return mask.getbbox()

    grayscale = image.convert("L")
    values = np.array(grayscale)
    mask = values < background_threshold
    if not mask.any():
        return None

    height, width = mask.shape
    edge_margin_x = max(10, width // 100)
    edge_margin_y = max(10, height // 100)
    min_component_area = max(24, (width * height) // 20000)
    min_component_width = max(3, width // 200)
    min_component_height = max(3, height // 200)

    filtered_mask = np.zeros_like(mask, dtype=bool)
    components = _component_bboxes(mask)
    for left, top, right, bottom, area in components:
        component_width = right - left
        component_height = bottom - top
        touches_edge = (
            left <= edge_margin_x
            or top <= edge_margin_y
            or right >= width - edge_margin_x
            or bottom >= height - edge_margin_y
        )
        is_tiny = (
            area < min_component_area
            or component_width < min_component_width
            or component_height < min_component_height
        )
        if touches_edge and is_tiny:
            continue
        if is_tiny and area < min_component_area // 2:
            continue
        filtered_mask[top:bottom, left:right] |= mask[top:bottom, left:right]

    if not filtered_mask.any():
        filtered_mask = mask

    row_threshold = max(4, width // 150)
    col_threshold = max(4, height // 150)
    active_rows = np.flatnonzero(filtered_mask.sum(axis=1) >= row_threshold)
    active_cols = np.flatnonzero(filtered_mask.sum(axis=0) >= col_threshold)

    if len(active_rows) == 0 or len(active_cols) == 0:
        ys, xs = np.nonzero(filtered_mask)
        if len(xs) == 0 or len(ys) == 0:
            return None
        return (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)

    left = int(active_cols[0])
    right = int(active_cols[-1]) + 1
    top = int(active_rows[0])
    bottom = int(active_rows[-1]) + 1
    return left, top, right, bottom


def crop_to_content(
    image: Image.Image,
    *,
    trim_margins: bool,
    background_threshold: int,
) -> Image.Image:
    if not trim_margins:
        return image.copy()

    content_box = detect_content_box(
        image,
        background_threshold=background_threshold,
    )
    if content_box is None:
        return image.copy()
    return image.crop(content_box)


def render_page(
    image: Image.Image,
    *,
    canvas_size: CanvasSize | None,
    grayscale: bool,
    shared_scale: float | None,
    content_align: str,
) -> Image.Image:
    Image, _ = require_pillow()

    if grayscale:
        renderable = image.convert("L")
    else:
        renderable = image.convert("RGB")

    if canvas_size is None:
        return renderable

    if shared_scale is None:
        resized_size = fit_with_padding(renderable.size, canvas_size)
    else:
        resized_size = scale_dimensions(renderable.size, shared_scale)
        if (
            resized_size[0] > canvas_size.width
            or resized_size[1] > canvas_size.height
        ):
            resized_size = fit_with_padding(renderable.size, canvas_size)

    resized = renderable.resize(resized_size, Image.Resampling.LANCZOS)

    background_color = 255 if resized.mode == "L" else (255, 255, 255)
    canvas = Image.new(
        resized.mode,
        (canvas_size.width, canvas_size.height),
        background_color,
    )
    offset_x = (canvas_size.width - resized.width) // 2
    if content_align == "top-center":
        offset_y = 0
    else:
        offset_y = (canvas_size.height - resized.height) // 2
    offset = (offset_x, offset_y)
    canvas.paste(resized, offset)
    return canvas


def save_pdf(
    images: Sequence[Image.Image],
    output_path: Path,
    dpi: int,
    jpeg_quality: int,
) -> None:
    if not images:
        raise ValueError("No images were provided for PDF export.")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    normalized_images = [
        image if image.mode in {"1", "L", "RGB"} else image.convert("RGB")
        for image in images
    ]
    first, *rest = normalized_images
    first.save(
        output_path,
        save_all=True,
        append_images=rest,
        resolution=dpi,
        quality=jpeg_quality,
    )


def run_tesseract_ocr(
    image_path: Path,
    output_base: Path,
    *,
    language: str,
    tesseract_cmd: str,
) -> Path:
    command = [
        tesseract_cmd,
        str(image_path),
        str(output_base),
        "-l",
        language,
        "pdf",
    ]
    try:
        subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        stderr = exc.stderr.strip() if exc.stderr else "Unknown Tesseract error."
        raise RuntimeError(
            f"Tesseract OCR failed for {image_path.name}: {stderr}"
        ) from exc

    pdf_path = output_base.with_suffix(".pdf")
    if not pdf_path.exists():
        raise RuntimeError(
            f"Tesseract OCR did not produce a PDF for {image_path.name}."
        )
    return pdf_path


def save_pdf_with_ocr(
    images: Sequence[Image.Image],
    output_path: Path,
    *,
    language: str,
    tesseract_cmd: str,
) -> None:
    if not images:
        raise ValueError("No images were provided for OCR PDF export.")

    PdfReader, PdfWriter = require_pypdf()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="scan2pdf-ocr-") as tmp:
        temp_dir = Path(tmp)
        writer = PdfWriter()

        for index, image in enumerate(images, start=1):
            image_path = temp_dir / f"page-{index:04d}.png"
            output_base = temp_dir / f"page-{index:04d}"
            image.save(image_path, format="PNG")
            page_pdf = run_tesseract_ocr(
                image_path,
                output_base,
                language=language,
                tesseract_cmd=tesseract_cmd,
            )
            reader = PdfReader(str(page_pdf))
            for page in reader.pages:
                writer.add_page(page)

        with output_path.open("wb") as handle:
            writer.write(handle)


def merge_pdfs(
    input_pdfs: Sequence[Path],
    output_path: Path,
) -> None:
    if len(input_pdfs) < 2:
        raise ValueError("At least two PDF files are required for merge.")

    PdfReader, PdfWriter = require_pypdf()
    writer = PdfWriter()

    for input_pdf in input_pdfs:
        reader = PdfReader(str(input_pdf))
        for page in reader.pages:
            writer.add_page(page)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("wb") as handle:
        writer.write(handle)


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    if argv and argv[0] == "merge":
        return parse_merge_args(argv[1:])

    return parse_convert_args(argv)


def parse_convert_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="scan2pdf",
        description="Normalize scanned images and export them as a single PDF.",
    )
    parser.add_argument(
        "--version",
        action="version",
        version=f"%(prog)s {get_version()}",
    )
    parser.add_argument(
        "input_dir",
        type=Path,
        help="Directory containing scanned images.",
    )
    parser.add_argument("output_pdf", type=Path, help="Destination PDF path.")
    parser.add_argument(
        "--page-size",
        default="A4",
        choices=["A4", "A5", "B5", "LETTER", "ORIGINAL"],
        help="Output canvas preset. Defaults to A4.",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=300,
        help="Target DPI used for page-size presets. Defaults to 300.",
    )
    parser.add_argument(
        "--jpeg-quality",
        type=int,
        default=85,
        help="JPEG quality used when embedding PDF page images. Defaults to 85.",
    )
    parser.add_argument(
        "--orientation",
        default="portrait",
        choices=["portrait", "landscape", "preserve"],
        help="Coarse page orientation normalization. Defaults to portrait.",
    )
    parser.add_argument(
        "--grayscale",
        action="store_true",
        help="Convert output pages to grayscale before PDF export.",
    )
    parser.add_argument(
        "--deskew",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Enable or disable OpenCV-based text skew correction.",
    )
    parser.add_argument(
        "--trim-margins",
        action="store_true",
        help="Crop outer white scan margins after rotation and deskew.",
    )
    parser.add_argument(
        "--background-threshold",
        type=int,
        default=245,
        help="Grayscale threshold used to detect white background when trimming margins.",
    )
    parser.add_argument(
        "--global-scale",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Use one shared scale factor for all trimmed pages. Defaults to enabled.",
    )
    parser.add_argument(
        "--save-normalized-dir",
        type=Path,
        help="Optional directory to save normalized pages for inspection.",
    )
    parser.add_argument(
        "--content-align",
        default="top-center",
        choices=["top-center", "center"],
        help="How cropped content is positioned on the output page. Defaults to top-center.",
    )
    parser.add_argument(
        "--ocr",
        action="store_true",
        help="Run Tesseract OCR and generate a searchable PDF with a text layer.",
    )
    parser.add_argument(
        "--ocr-lang",
        default="kor+eng",
        help="Tesseract OCR language(s). Defaults to kor+eng.",
    )
    parser.add_argument(
        "--tesseract-cmd",
        default="tesseract",
        help="Tesseract executable name or path. Defaults to tesseract.",
    )
    args = parser.parse_args(argv)
    args.command = "convert"
    return args


def parse_merge_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="scan2pdf merge",
        description="Merge PDF files from a directory into one PDF.",
    )
    parser.add_argument(
        "input_dir",
        type=Path,
        help="Directory containing PDF files to merge.",
    )
    parser.add_argument("output_pdf", type=Path, help="Destination PDF path.")
    args = parser.parse_args(argv)
    args.command = "merge"
    return args


def validate_args(args: argparse.Namespace) -> None:
    if args.command == "merge":
        validate_merge_args(args)
        return

    if not args.input_dir.exists() or not args.input_dir.is_dir():
        raise SystemExit(f"Input directory does not exist: {args.input_dir}")

    require_pillow()
    if args.dpi <= 0:
        raise SystemExit("--dpi must be a positive integer.")
    if args.jpeg_quality < 1 or args.jpeg_quality > 100:
        raise SystemExit("--jpeg-quality must be between 1 and 100.")
    if args.background_threshold < 1 or args.background_threshold > 255:
        raise SystemExit("--background-threshold must be between 1 and 255.")
    if not args.ocr_lang.strip():
        raise SystemExit("--ocr-lang must not be empty.")
    if args.deskew:
        try:
            require_cv2()
        except RuntimeError as exc:
            raise SystemExit(str(exc)) from exc
    if args.ocr:
        try:
            require_tesseract(args.tesseract_cmd)
            require_pypdf()
        except RuntimeError as exc:
            raise SystemExit(str(exc)) from exc


def validate_merge_args(args: argparse.Namespace) -> None:
    try:
        require_pypdf()
    except RuntimeError as exc:
        raise SystemExit(str(exc)) from exc

    if not args.input_dir.exists() or not args.input_dir.is_dir():
        raise SystemExit(f"Input directory does not exist: {args.input_dir}")

    input_pdfs = iter_pdf_files(args.input_dir)
    if len(input_pdfs) < 2:
        raise SystemExit(
            f"At least two PDF files are required in {args.input_dir}."
        )

    if args.output_pdf.suffix.lower() != ".pdf":
        raise SystemExit("--output_pdf for merge must end with .pdf.")


def process_directory(args: argparse.Namespace) -> list[Image.Image]:
    Image, _ = require_pillow()
    files = iter_image_files(args.input_dir)
    if not files:
        supported = ", ".join(sorted(SUPPORTED_EXTENSIONS))
        raise SystemExit(
            f"No supported image files found in {args.input_dir} "
            f"(supported: {supported})."
        )

    canvas_size = page_size_to_pixels(args.page_size, args.dpi, args.orientation)
    prepared_pages: list[PreparedPage] = []

    for file_path in files:
        with Image.open(file_path) as image:
            prepared = prepare_image(
                image,
                orientation=args.orientation,
                deskew=args.deskew,
            )
            cropped = crop_to_content(
                prepared,
                trim_margins=args.trim_margins,
                background_threshold=args.background_threshold,
            )
            prepared_pages.append(
                PreparedPage(
                    source_path=file_path,
                    image=cropped,
                    cropped_size=cropped.size,
                )
            )

    shared_scale: float | None = None
    if args.global_scale and args.trim_margins and canvas_size is not None:
        shared_scale = compute_uniform_scale(
            [page.cropped_size for page in prepared_pages],
            canvas_size,
        )

    normalized_pages: list[Image.Image] = []
    for page in prepared_pages:
        rendered = render_page(
            page.image,
            canvas_size=canvas_size,
            grayscale=args.grayscale,
            shared_scale=shared_scale,
            content_align=args.content_align,
        )
        normalized_pages.append(rendered)

    if args.save_normalized_dir is not None:
        args.save_normalized_dir.mkdir(parents=True, exist_ok=True)
        for index, (page, normalized) in enumerate(
            zip(prepared_pages, normalized_pages),
            start=1,
        ):
            suffix = page.source_path.suffix.lower() or ".png"
            output_path = args.save_normalized_dir / f"{index:04d}{suffix}"
            normalized.save(output_path)

    return normalized_pages


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    validate_args(args)
    if args.command == "merge":
        merge_pdfs(iter_pdf_files(args.input_dir), args.output_pdf)
        return 0

    images = process_directory(args)
    if args.ocr:
        save_pdf_with_ocr(
            images,
            args.output_pdf,
            language=args.ocr_lang,
            tesseract_cmd=args.tesseract_cmd,
        )
    else:
        save_pdf(images, args.output_pdf, args.dpi, args.jpeg_quality)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
