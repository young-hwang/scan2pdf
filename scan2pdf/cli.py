from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from .core import (
    SUPPORTED_EXTENSIONS,
    CanvasSize,
    compute_uniform_scale,
    fit_with_padding,
    iter_image_files,
    normalize_skew_angle,
    page_size_to_pixels,
    scale_dimensions,
    should_rotate_for_orientation,
)


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


@dataclass
class PreparedPage:
    source_path: Path
    image: object
    cropped_size: tuple[int, int]


def detect_skew_angle(image: Image.Image, max_angle: float = 10.0) -> float | None:
    cv2 = require_cv2()
    import numpy as np  # type: ignore

    grayscale = np.array(image.convert("L"))
    _, binary = cv2.threshold(
        grayscale, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )
    coords = cv2.findNonZero(binary)
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
    grayscale = image.convert("L")
    mask = grayscale.point(
        lambda value: 255 if value < background_threshold else 0,
        mode="1",
    )
    return mask.getbbox()


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
    offset = (
        (canvas_size.width - resized.width) // 2,
        (canvas_size.height - resized.height) // 2,
    )
    canvas.paste(resized, offset)
    return canvas


def save_pdf(images: Sequence[Image.Image], output_path: Path, dpi: int) -> None:
    if not images:
        raise ValueError("No images were provided for PDF export.")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    first, *rest = [image.convert("RGB") for image in images]
    first.save(
        output_path,
        save_all=True,
        append_images=rest,
        resolution=dpi,
    )


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Normalize scanned images and export them as a single PDF."
    )
    parser.add_argument("input_dir", type=Path, help="Directory containing scanned images.")
    parser.add_argument("output_pdf", type=Path, help="Destination PDF path.")
    parser.add_argument(
        "--page-size",
        default="A4",
        choices=["A4", "A5", "LETTER", "ORIGINAL"],
        help="Output canvas preset. Defaults to A4.",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=300,
        help="Target DPI used for page-size presets. Defaults to 300.",
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
    return parser.parse_args(argv)


def validate_args(args: argparse.Namespace) -> None:
    require_pillow()
    if not args.input_dir.exists() or not args.input_dir.is_dir():
        raise SystemExit(f"Input directory does not exist: {args.input_dir}")
    if args.dpi <= 0:
        raise SystemExit("--dpi must be a positive integer.")
    if args.background_threshold < 1 or args.background_threshold > 255:
        raise SystemExit("--background-threshold must be between 1 and 255.")
    if args.deskew:
        try:
            require_cv2()
        except RuntimeError as exc:
            raise SystemExit(str(exc)) from exc


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
    images = process_directory(args)
    save_pdf(images, args.output_pdf, args.dpi)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

