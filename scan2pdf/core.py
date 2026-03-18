from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


SUPPORTED_EXTENSIONS = {
    ".jpg",
    ".jpeg",
    ".png",
    ".tif",
    ".tiff",
    ".bmp",
    ".webp",
}

PAGE_SIZE_INCHES = {
    "A4": (8.27, 11.69),
    "A5": (5.83, 8.27),
    "LETTER": (8.5, 11.0),
}


@dataclass(frozen=True)
class CanvasSize:
    width: int
    height: int


def natural_sort_key(value: str) -> list[object]:
    return [
        int(part) if part.isdigit() else part.lower()
        for part in re.split(r"(\d+)", value)
        if part
    ]


def iter_image_files(input_dir: Path) -> list[Path]:
    files = [
        path
        for path in input_dir.iterdir()
        if path.is_file() and path.suffix.lower() in SUPPORTED_EXTENSIONS
    ]
    return sorted(files, key=lambda path: natural_sort_key(path.name))


def normalize_skew_angle(raw_angle: float) -> float:
    angle = raw_angle
    if angle < -45.0:
        angle += 90.0
    elif angle > 45.0:
        angle -= 90.0
    return angle


def should_rotate_for_orientation(
    width: int,
    height: int,
    target_orientation: str,
) -> bool:
    if target_orientation == "preserve":
        return False
    if width == height:
        return False
    is_landscape = width > height
    return (
        (target_orientation == "portrait" and is_landscape)
        or (target_orientation == "landscape" and not is_landscape)
    )


def page_size_to_pixels(
    page_size: str,
    dpi: int,
    orientation: str,
) -> CanvasSize | None:
    if page_size == "ORIGINAL":
        return None

    width_in, height_in = PAGE_SIZE_INCHES[page_size]
    if orientation == "landscape":
        width_in, height_in = max(width_in, height_in), min(width_in, height_in)
    else:
        width_in, height_in = min(width_in, height_in), max(width_in, height_in)

    return CanvasSize(
        width=max(1, int(round(width_in * dpi))),
        height=max(1, int(round(height_in * dpi))),
    )


def fit_with_padding(
    image_size: tuple[int, int],
    canvas_size: CanvasSize,
) -> tuple[int, int]:
    width, height = image_size
    scale = min(canvas_size.width / width, canvas_size.height / height)
    resized_width = max(1, int(round(width * scale)))
    resized_height = max(1, int(round(height * scale)))
    return resized_width, resized_height


def compute_uniform_scale(
    content_sizes: list[tuple[int, int]],
    canvas_size: CanvasSize,
) -> float:
    if not content_sizes:
        raise ValueError("content_sizes must not be empty.")

    max_width = max(width for width, _ in content_sizes)
    max_height = max(height for _, height in content_sizes)
    return min(canvas_size.width / max_width, canvas_size.height / max_height)


def scale_dimensions(
    image_size: tuple[int, int],
    scale: float,
) -> tuple[int, int]:
    width, height = image_size
    return (
        max(1, int(round(width * scale))),
        max(1, int(round(height * scale))),
    )

