from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from PIL import Image, ImageDraw

from scan2pdf.core import (
    CanvasSize,
    compute_uniform_scale,
    fit_with_padding,
    iter_image_files,
    iter_pdf_files,
    natural_sort_key,
    normalize_skew_angle,
    page_size_to_pixels,
    scale_dimensions,
    should_rotate_for_orientation,
)
from scan2pdf.cli import (
    command_exists,
    detect_content_box,
    merge_pdfs,
    parse_args,
    render_page,
    run_tesseract_ocr,
    validate_args,
)


class NaturalSortTests(unittest.TestCase):
    def test_natural_sort_key_orders_numeric_suffixes(self) -> None:
        values = ["page10.png", "page2.png", "page1.png"]
        self.assertEqual(sorted(values, key=natural_sort_key), ["page1.png", "page2.png", "page10.png"])

    def test_iter_image_files_filters_and_sorts_supported_files(self) -> None:
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ["page10.jpg", "page2.jpg", "notes.txt", "page1.png"]:
                (root / name).write_bytes(b"sample")

            ordered = [path.name for path in iter_image_files(root)]
            self.assertEqual(ordered, ["page1.png", "page2.jpg", "page10.jpg"])

    def test_iter_pdf_files_filters_and_sorts_supported_files(self) -> None:
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ["part10.pdf", "part2.pdf", "notes.txt", "part1.pdf"]:
                (root / name).write_bytes(b"sample")

            ordered = [path.name for path in iter_pdf_files(root)]
            self.assertEqual(ordered, ["part1.pdf", "part2.pdf", "part10.pdf"])

class GeometryTests(unittest.TestCase):
    def test_normalize_skew_angle_flattens_min_area_rect_values(self) -> None:
        self.assertAlmostEqual(normalize_skew_angle(-88.0), 2.0)
        self.assertAlmostEqual(normalize_skew_angle(87.0), -3.0)
        self.assertAlmostEqual(normalize_skew_angle(-7.5), -7.5)

    def test_orientation_rotation_rule(self) -> None:
        self.assertTrue(should_rotate_for_orientation(1600, 1200, "portrait"))
        self.assertTrue(should_rotate_for_orientation(1200, 1600, "landscape"))
        self.assertFalse(should_rotate_for_orientation(1200, 1600, "portrait"))
        self.assertFalse(should_rotate_for_orientation(1200, 1200, "portrait"))
        self.assertFalse(should_rotate_for_orientation(1600, 1200, "preserve"))

    def test_fit_with_padding_preserves_aspect_ratio(self) -> None:
        fitted = fit_with_padding((3000, 2000), CanvasSize(width=1000, height=1000))
        self.assertEqual(fitted, (1000, 667))

    def test_page_size_to_pixels_respects_orientation(self) -> None:
        portrait = page_size_to_pixels("A4", dpi=300, orientation="portrait")
        landscape = page_size_to_pixels("A4", dpi=300, orientation="landscape")

        self.assertEqual(portrait, CanvasSize(width=2481, height=3507))
        self.assertEqual(landscape, CanvasSize(width=3507, height=2481))

    def test_original_page_size_returns_none(self) -> None:
        self.assertIsNone(page_size_to_pixels("ORIGINAL", dpi=300, orientation="portrait"))

    def test_compute_uniform_scale_uses_largest_cropped_page(self) -> None:
        scale = compute_uniform_scale(
            [(1000, 800), (900, 700), (950, 780)],
            CanvasSize(width=1200, height=1600),
        )
        self.assertAlmostEqual(scale, 1.2)

    def test_scale_dimensions_applies_shared_scale(self) -> None:
        self.assertEqual(scale_dimensions((900, 700), 1.2), (1080, 840))

    def test_detect_content_box_ignores_edge_noise(self) -> None:
        image = Image.new("RGB", (200, 300), "white")
        draw = ImageDraw.Draw(image)
        draw.rectangle((40, 60, 160, 240), fill="black")
        draw.line((0, 0, 199, 0), fill="black", width=1)
        draw.line((0, 299, 199, 299), fill="black", width=1)

        self.assertEqual(
            detect_content_box(image, background_threshold=245),
            (40, 60, 161, 241),
        )

    def test_detect_content_box_ignores_small_corner_speckles(self) -> None:
        image = Image.new("RGB", (240, 320), "white")
        draw = ImageDraw.Draw(image)
        draw.rectangle((50, 70, 190, 260), fill="black")
        draw.rectangle((0, 0, 2, 2), fill="black")
        draw.rectangle((236, 316, 239, 319), fill="black")
        draw.rectangle((3, 150, 4, 151), fill="black")

        self.assertEqual(
            detect_content_box(image, background_threshold=245),
            (50, 70, 191, 261),
        )

    def test_render_page_supports_top_center_alignment(self) -> None:
        image = Image.new("RGB", (50, 50), "black")
        canvas = CanvasSize(width=100, height=200)

        centered = render_page(
            image,
            canvas_size=canvas,
            grayscale=False,
            shared_scale=None,
            content_align="center",
        )
        top_centered = render_page(
            image,
            canvas_size=canvas,
            grayscale=False,
            shared_scale=None,
            content_align="top-center",
        )

        self.assertEqual(centered.getpixel((10, 0)), (255, 255, 255))
        self.assertEqual(top_centered.getpixel((10, 0)), (0, 0, 0))


class CliTests(unittest.TestCase):
    def test_parse_args_uses_default_jpeg_quality(self) -> None:
        args = parse_args(["./scans", "./output/book.pdf"])
        self.assertEqual(args.jpeg_quality, 85)

    def test_parse_args_accepts_custom_jpeg_quality(self) -> None:
        args = parse_args(
            ["./scans", "./output/book.pdf", "--jpeg-quality", "72"]
        )
        self.assertEqual(args.jpeg_quality, 72)

    def test_parse_args_uses_default_ocr_language(self) -> None:
        args = parse_args(["./scans", "./output/book.pdf", "--ocr"])
        self.assertTrue(args.ocr)
        self.assertEqual(args.ocr_lang, "kor+eng")

    def test_parse_args_accepts_custom_tesseract_command(self) -> None:
        args = parse_args(
            [
                "./scans",
                "./output/book.pdf",
                "--ocr",
                "--ocr-lang",
                "eng",
                "--tesseract-cmd",
                "/opt/homebrew/bin/tesseract",
            ]
        )
        self.assertEqual(args.ocr_lang, "eng")
        self.assertEqual(args.tesseract_cmd, "/opt/homebrew/bin/tesseract")

    def test_parse_args_uses_top_center_content_alignment_by_default(self) -> None:
        args = parse_args(["./scans", "./output/book.pdf"])
        self.assertEqual(args.content_align, "top-center")

    def test_parse_args_supports_merge_subcommand(self) -> None:
        args = parse_args(["merge", "./pdfs", "./merged.pdf"])
        self.assertEqual(args.command, "merge")
        self.assertEqual(args.input_dir, Path("./pdfs"))
        self.assertEqual(args.output_pdf, Path("./merged.pdf"))

    def test_validate_args_rejects_empty_ocr_language(self) -> None:
        with TemporaryDirectory() as tmp:
            input_dir = Path(tmp) / "scans"
            input_dir.mkdir()
            args = parse_args(
                [str(input_dir), "./output/book.pdf", "--ocr-lang", " "]
            )
            with self.assertRaises(SystemExit) as exc:
                validate_args(args)
            self.assertEqual(str(exc.exception), "--ocr-lang must not be empty.")

    @patch("scan2pdf.cli.require_pillow")
    @patch("scan2pdf.cli.require_cv2")
    def test_validate_args_allows_non_ocr_without_tesseract(
        self,
        _require_cv2,
        _require_pillow,
    ) -> None:
        with TemporaryDirectory() as tmp:
            input_dir = Path(tmp) / "scans"
            input_dir.mkdir()
            args = parse_args([str(input_dir), "./output/book.pdf"])
            validate_args(args)

    @patch("scan2pdf.cli.require_pillow")
    @patch("scan2pdf.cli.require_cv2")
    def test_validate_args_requires_tesseract_for_ocr(
        self,
        _require_cv2,
        _require_pillow,
    ) -> None:
        with TemporaryDirectory() as tmp:
            input_dir = Path(tmp) / "scans"
            input_dir.mkdir()
            args = parse_args([str(input_dir), "./output/book.pdf", "--ocr"])
            with patch("scan2pdf.cli.command_exists", return_value=False):
                with self.assertRaises(SystemExit) as exc:
                    validate_args(args)
            self.assertIn("Tesseract", str(exc.exception))

    @patch("scan2pdf.cli.require_pypdf")
    def test_validate_args_requires_existing_merge_inputs(
        self,
        mock_require_pypdf,
    ) -> None:
        mock_require_pypdf.return_value = object(), object()
        args = parse_args(["merge", "./missing-pdfs", "./out.pdf"])
        with self.assertRaises(SystemExit) as exc:
            validate_args(args)
        self.assertIn("Input directory does not exist", str(exc.exception))

    @patch("scan2pdf.cli.require_pypdf")
    def test_validate_args_requires_at_least_two_merge_pdfs(
        self,
        mock_require_pypdf,
    ) -> None:
        mock_require_pypdf.return_value = object(), object()
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "only.pdf").write_bytes(b"%PDF-1.4\n")

            args = parse_args(["merge", str(root), str(root / "out.pdf")])
            with self.assertRaises(SystemExit) as exc:
                validate_args(args)
            self.assertIn("At least two PDF files are required", str(exc.exception))

    @patch("scan2pdf.cli.require_pypdf")
    def test_validate_args_rejects_non_pdf_merge_output(
        self,
        mock_require_pypdf,
    ) -> None:
        mock_require_pypdf.return_value = object(), object()
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            pdf_dir = root / "pdfs"
            pdf_dir.mkdir()
            for name in ["a.pdf", "b.pdf"]:
                (pdf_dir / name).write_bytes(b"%PDF-1.4\n")

            args = parse_args(
                [
                    "merge",
                    str(pdf_dir),
                    str(root / "merged.txt"),
                ]
            )
            with self.assertRaises(SystemExit) as exc:
                validate_args(args)
            self.assertIn("must end with .pdf", str(exc.exception))


class OcrTests(unittest.TestCase):
    def test_command_exists_detects_absolute_path(self) -> None:
        with TemporaryDirectory() as tmp:
            command_path = Path(tmp) / "tesseract"
            command_path.write_text("#!/bin/sh\n")
            self.assertTrue(command_exists(str(command_path)))

    @patch("scan2pdf.cli.subprocess.run")
    def test_run_tesseract_ocr_builds_expected_command(self, mock_run) -> None:
        with TemporaryDirectory() as tmp:
            temp_dir = Path(tmp)
            image_path = temp_dir / "page.png"
            image_path.write_bytes(b"png")
            output_base = temp_dir / "page"
            output_base.with_suffix(".pdf").write_bytes(b"%PDF-1.4\n")

            pdf_path = run_tesseract_ocr(
                image_path,
                output_base,
                language="kor+eng",
                tesseract_cmd="tesseract",
            )

            self.assertEqual(pdf_path, output_base.with_suffix(".pdf"))
            mock_run.assert_called_once_with(
                [
                    "tesseract",
                    str(image_path),
                    str(output_base),
                    "-l",
                    "kor+eng",
                    "pdf",
                ],
                check=True,
                capture_output=True,
                text=True,
            )


class MergeTests(unittest.TestCase):
    def test_merge_pdfs_combines_pages_from_each_input(self) -> None:
        try:
            from pypdf import PdfReader
        except ImportError:
            self.skipTest("pypdf is not installed")

        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            first_pdf = root / "first.pdf"
            second_pdf = root / "second.pdf"
            output_pdf = root / "merged.pdf"

            Image.new("RGB", (40, 60), "white").save(first_pdf, save_all=True)
            Image.new("RGB", (50, 70), "black").save(second_pdf, save_all=True)

            merge_pdfs([first_pdf, second_pdf], output_pdf)

            merged = PdfReader(str(output_pdf))
            self.assertEqual(len(merged.pages), 2)

if __name__ == "__main__":
    unittest.main()
