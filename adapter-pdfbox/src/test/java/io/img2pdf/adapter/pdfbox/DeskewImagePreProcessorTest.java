package io.img2pdf.adapter.pdfbox;

import io.img2pdf.domain.model.ImageCompression;
import io.img2pdf.domain.model.PageSize;
import io.img2pdf.domain.model.PdfOptions;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeskewImagePreProcessorTest {
    @Test
    void detectSkewAngleFindsCorrectionForSimpleDocumentShape() {
        DeskewImagePreProcessor preProcessor = new DeskewImagePreProcessor();
        BufferedImage source = createDocumentLikeImage();
        BufferedImage skewed = rotate(source, 3.0);

        double correctionAngle = preProcessor.detectSkewAngle(skewed);

        assertEquals(-3.0, correctionAngle, 0.2);
    }

    @Test
    void detectSkewAngleRemainsAccurateWithWideMarginsAndNoise() {
        DeskewImagePreProcessor preProcessor = new DeskewImagePreProcessor();
        BufferedImage source = createDocumentLikeImageWithMarginsAndNoise();
        BufferedImage skewed = rotate(source, 2.37);

        double correctionAngle = preProcessor.detectSkewAngle(skewed);

        assertEquals(-2.37, correctionAngle, 0.15);
    }

    @Test
    void detectSkewAngleSkipsVerySparseStraightTitlePages() throws IOException {
        DeskewImagePreProcessor preProcessor = new DeskewImagePreProcessor();
        BufferedImage source = createStraightSparseTitlePageImage();

        double correctionAngle = preProcessor.detectSkewAngle(source);

        assertEquals(0.0, correctionAngle, 0.1);
    }

    @Test
    void preprocessStillCreatesOutputFileWhenSkewCorrectionIsSkipped() throws IOException {
        DeskewImagePreProcessor preProcessor = new DeskewImagePreProcessor();
        Path sourceImage = writeImageToTempFile(createStraightSparseTitlePageImage(), "sparse-title-page");
        Path outputDir = createTestDirectory("sparse");
        PdfOptions options = new PdfOptions(PageSize.A5, true, true, true, outputDir, null, ImageCompression.JPEG, 75);

        Path outputFile = preProcessor.preprocess(sourceImage, options);

        try {
            assertNotEquals(sourceImage.toAbsolutePath().normalize(), outputFile.toAbsolutePath().normalize());
            assertTrue(Files.exists(outputFile));
            assertEquals(outputDir.toAbsolutePath().normalize(), outputFile.getParent().toAbsolutePath().normalize());
            assertTrue(outputFile.getFileName().toString().startsWith("img2pdf-deskew-"));
            assertTrue(outputFile.getFileName().toString().endsWith(".jpg"));
            BufferedImage cropped = javax.imageio.ImageIO.read(outputFile.toFile());
            assertTrue(cropped.getWidth() < 1000);
            assertTrue(cropped.getHeight() < 1400);
        } finally {
            Files.deleteIfExists(outputFile);
            Files.deleteIfExists(sourceImage);
            Files.deleteIfExists(outputDir);
        }
    }

    @Test
    void detectSkewAngleFindsCorrectionForRealScan100() throws IOException {
        DeskewImagePreProcessor preProcessor = new DeskewImagePreProcessor();
        BufferedImage source = javax.imageio.ImageIO.read(resolveSampleImagePath().toFile());

        double correctionAngle = preProcessor.detectSkewAngle(source);

        assertEquals(-9.92, correctionAngle, 0.5, "Expected Java deskew to match the Python correction range for Scan 100.jpeg");
    }

    @Test
    void preprocessCreatesDeskewedOutputFileInConfiguredDirectory() throws IOException {
        DeskewImagePreProcessor preProcessor = new DeskewImagePreProcessor();
        Path sourceImage = resolveSampleImagePath();
        Path outputDir = createTestDirectory("configured");
        PdfOptions options = new PdfOptions(PageSize.A5, true, true, true, outputDir, null, ImageCompression.JPEG, 75);

        Path outputFile = preProcessor.preprocess(sourceImage, options);

        try {
            assertNotEquals(sourceImage.toAbsolutePath().normalize(), outputFile.toAbsolutePath().normalize());
            assertTrue(Files.exists(outputFile));
            assertEquals(outputDir.toAbsolutePath().normalize(), outputFile.getParent().toAbsolutePath().normalize());
            assertTrue(outputFile.getFileName().toString().startsWith("img2pdf-deskew-"));
            assertTrue(outputFile.getFileName().toString().endsWith(".jpg"));
            BufferedImage original = javax.imageio.ImageIO.read(sourceImage.toFile());
            BufferedImage processed = javax.imageio.ImageIO.read(outputFile.toFile());
            assertTrue(processed.getWidth() < original.getWidth());
            assertTrue(processed.getHeight() < original.getHeight());
        } finally {
            if (!outputFile.toAbsolutePath().normalize().equals(sourceImage.toAbsolutePath().normalize())) {
                Files.deleteIfExists(outputFile);
            }
            Files.deleteIfExists(outputDir);
        }
    }

    @Test
    void preprocessUsesDefaultDeskewOutputDirectoryWhenNotConfigured() throws IOException {
        DeskewImagePreProcessor preProcessor = new DeskewImagePreProcessor();
        Path sourceImage = resolveSampleImagePath();
        Path defaultOutputDir = Path.of(".img2pdf-temp").toAbsolutePath().normalize();
        PdfOptions options = new PdfOptions(PageSize.A5, true, true, true, null, null, ImageCompression.JPEG, 75);

        Path outputFile = preProcessor.preprocess(sourceImage, options);

        try {
            assertTrue(Files.exists(outputFile));
            assertEquals(defaultOutputDir, outputFile.getParent().toAbsolutePath().normalize());
            assertTrue(outputFile.getFileName().toString().startsWith("img2pdf-deskew-"));
            assertTrue(outputFile.getFileName().toString().endsWith(".jpg"));
        } finally {
            if (!outputFile.toAbsolutePath().normalize().equals(sourceImage.toAbsolutePath().normalize())) {
                Files.deleteIfExists(outputFile);
            }
            Files.deleteIfExists(defaultOutputDir);
        }
    }

    @Test
    void preprocessCanDisableCroppingWhileKeepingDeskewOutput() throws IOException {
        DeskewImagePreProcessor preProcessor = new DeskewImagePreProcessor();
        Path sourceImage = writeImageToTempFile(createStraightSparseTitlePageImage(), "no-crop");
        Path outputDir = createTestDirectory("no-crop");
        PdfOptions options = new PdfOptions(PageSize.A5, true, false, false, outputDir, null, ImageCompression.JPEG, 75);

        Path outputFile = preProcessor.preprocess(sourceImage, options);

        try {
            assertEquals(sourceImage.toAbsolutePath().normalize(), outputFile.toAbsolutePath().normalize());
        } finally {
            Files.deleteIfExists(sourceImage);
            Files.deleteIfExists(outputDir);
        }
    }

    @Test
    void preprocessCanCropWithoutDeskew() throws IOException {
        DeskewImagePreProcessor preProcessor = new DeskewImagePreProcessor();
        Path sourceImage = writeImageToTempFile(createStraightSparseTitlePageImage(), "crop-only");
        Path outputDir = createTestDirectory("crop-only");
        PdfOptions options = new PdfOptions(PageSize.A5, true, false, true, outputDir, null, ImageCompression.JPEG, 75);

        Path outputFile = preProcessor.preprocess(sourceImage, options);

        try {
            BufferedImage processed = javax.imageio.ImageIO.read(outputFile.toFile());
            assertTrue(processed.getWidth() < 1000);
            assertTrue(processed.getHeight() < 1400);
        } finally {
            Files.deleteIfExists(outputFile);
            Files.deleteIfExists(sourceImage);
            Files.deleteIfExists(outputDir);
        }
    }

    private BufferedImage createDocumentLikeImage() {
        BufferedImage image = new BufferedImage(1000, 1400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);

        for (int y = 120; y < 1250; y += 70) {
            graphics.fillRect(140, y, 620, 10);
        }

        graphics.dispose();
        return image;
    }

    private BufferedImage createDocumentLikeImageWithMarginsAndNoise() {
        BufferedImage image = new BufferedImage(1800, 2400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);

        for (int y = 260; y < 1980; y += 75) {
            graphics.fillRect(280, y, 860, 9);
        }

        graphics.fillRect(280, 2100, 540, 12);
        graphics.fillRect(1320, 220, 18, 18);
        graphics.fillRect(1440, 2080, 22, 22);
        graphics.fillRect(120, 1800, 14, 14);
        graphics.dispose();
        return image;
    }

    private BufferedImage createSparseTitlePageImage() {
        return createTitlePageImage(-4.8);
    }

    private BufferedImage createStraightSparseTitlePageImage() {
        return createTitlePageImage(0.0);
    }

    private BufferedImage createTitlePageImage(double angleDegrees) {
        BufferedImage image = new BufferedImage(1000, 1400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(248, 248, 248));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(80, 80, 80));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(graphics.getFont().deriveFont(36f));
        graphics.rotate(Math.toRadians(angleDegrees), image.getWidth() / 2.0, image.getHeight() / 5.0);
        graphics.drawString("Generative AI System Design", 290, 210);
        graphics.setFont(graphics.getFont().deriveFont(26f));
        graphics.drawString("Interview", 425, 260);
        graphics.dispose();
        return image;
    }

    private BufferedImage rotate(BufferedImage source, double angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int width = source.getWidth();
        int height = source.getHeight();
        int rotatedWidth = (int) Math.floor(width * cos + height * sin);
        int rotatedHeight = (int) Math.floor(height * cos + width * sin);

        BufferedImage rotated = new BufferedImage(rotatedWidth, rotatedHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rotated.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, rotatedWidth, rotatedHeight);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        AffineTransform transform = new AffineTransform();
        transform.translate((rotatedWidth - width) / 2.0, (rotatedHeight - height) / 2.0);
        transform.rotate(radians, width / 2.0, height / 2.0);
        graphics.drawRenderedImage(source, transform);
        graphics.dispose();
        return rotated;
    }

    private Path resolveSampleImagePath() {
        Path[] candidates = new Path[] {
                Path.of("..", "scans", "Scan 100.jpeg").normalize(),
                Path.of("scans", "Scan 100.jpeg"),
                Path.of("..", "images", "Scan 100.jpeg").normalize(),
                Path.of("images", "Scan 100.jpeg")
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Sample image not found for preprocess test.");
    }

    private Path writeImageToTempFile(BufferedImage image, String name) throws IOException {
        Path path = Path.of("build", "test-output", name + "-" + UUID.randomUUID() + ".png")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(path.getParent());
        javax.imageio.ImageIO.write(image, "png", path.toFile());
        return path;
    }

    private Path createTestDirectory(String name) throws IOException {
        Path directory = Path.of("build", "test-output", "deskew-" + name + "-" + UUID.randomUUID())
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(directory);
        return directory;
    }
}
