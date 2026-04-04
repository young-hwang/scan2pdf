package io.img2pdf.adapter.pdfbox;

import io.img2pdf.application.outbound.ImagePreProcessorPort;
import io.img2pdf.domain.model.PdfOptions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class DeskewImagePreProcessor implements ImagePreProcessorPort {
    private static final Path DEFAULT_DESKEW_TEMP_DIR = Path.of(".img2pdf-temp");
    private static final double MAX_SKEW_ANGLE = 10.0;
    private static final double COARSE_ANGLE_STEP = 1.0;
    private static final double MEDIUM_ANGLE_STEP = 0.25;
    private static final double FINE_ANGLE_STEP = 0.05;
    private static final double MINIMUM_CORRECTION_ANGLE = 0.1;
    private static final int MAX_ANALYSIS_WIDTH = 1000;
    private static final int CONTENT_PADDING = 12;
    private static final double MINIMUM_CONTENT_HEIGHT_RATIO = 0.18;

    @Override
    public Path preprocess(Path imagePath, PdfOptions options) {
        if (!options.deskew()) {
            return imagePath;
        }

        try {
            BufferedImage originalImage = ImageIO.read(imagePath.toFile());
            if (originalImage == null) {
                return imagePath;
            }

            double correctionAngle = detectSkewAngle(originalImage);
            Path tempDirectory = resolveDeskewTempDir(options);
            Files.createDirectories(tempDirectory);
            Path outputFile = createDeskewFile(tempDirectory);
            BufferedImage processedImage = Math.abs(correctionAngle) < MINIMUM_CORRECTION_ANGLE
                    ? originalImage
                    : rotateImage(originalImage, correctionAngle);
            ImageIO.write(processedImage, "png", outputFile.toFile());
            return outputFile;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deskew image: " + imagePath, e);
        }
    }

    double detectSkewAngle(BufferedImage image) {
        BufferedImage analysisImage = downscaleForAnalysis(toBinaryImage(image));
        ContentBounds contentBounds = findContentBounds(analysisImage);
        if (!hasEnoughContentForDeskew(contentBounds, analysisImage.getHeight())) {
            return 0.0;
        }

        BufferedImage contentImage = cropToContentBounds(analysisImage, contentBounds);
        double coarseAngle = findBestAngle(contentImage, -MAX_SKEW_ANGLE, MAX_SKEW_ANGLE, COARSE_ANGLE_STEP);
        double mediumAngle = findBestAngle(
                contentImage,
                coarseAngle - COARSE_ANGLE_STEP,
                coarseAngle + COARSE_ANGLE_STEP,
                MEDIUM_ANGLE_STEP
        );
        return findBestAngle(
                contentImage,
                mediumAngle - MEDIUM_ANGLE_STEP,
                mediumAngle + MEDIUM_ANGLE_STEP,
                FINE_ANGLE_STEP
        );
    }

    private BufferedImage toBinaryImage(BufferedImage source) {
        BufferedImage binary = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        int threshold = determineOtsuThreshold(source);

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                Color color = new Color(source.getRGB(x, y));
                int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                binary.setRGB(x, y, gray < threshold ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return binary;
    }

    private int determineOtsuThreshold(BufferedImage source) {
        int[] histogram = new int[256];
        long weightedSum = 0L;
        long pixelCount = (long) source.getWidth() * source.getHeight();

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                Color color = new Color(source.getRGB(x, y));
                int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                histogram[gray]++;
                weightedSum += gray;
            }
        }

        long backgroundWeight = 0L;
        long backgroundWeightedSum = 0L;
        double bestVariance = Double.NEGATIVE_INFINITY;
        int threshold = 220;

        for (int value = 0; value < histogram.length; value++) {
            backgroundWeight += histogram[value];
            if (backgroundWeight == 0L) {
                continue;
            }

            long foregroundWeight = pixelCount - backgroundWeight;
            if (foregroundWeight == 0L) {
                break;
            }

            backgroundWeightedSum += (long) value * histogram[value];
            double backgroundMean = (double) backgroundWeightedSum / backgroundWeight;
            double foregroundMean = (double) (weightedSum - backgroundWeightedSum) / foregroundWeight;
            double variance = backgroundWeight * (double) foregroundWeight
                    * (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean);

            if (variance > bestVariance) {
                bestVariance = variance;
                threshold = value;
            }
        }

        return Math.min(240, threshold + 15);
    }

    private BufferedImage downscaleForAnalysis(BufferedImage source) {
        if (source.getWidth() <= MAX_ANALYSIS_WIDTH) {
            return source;
        }

        int targetWidth = MAX_ANALYSIS_WIDTH;
        int targetHeight = (int) Math.round((double) source.getHeight() * targetWidth / source.getWidth());
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, source.getType());
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return scaled;
    }

    private double findBestAngle(BufferedImage image, double startAngle, double endAngle, double step) {
        double clampedStart = Math.max(-MAX_SKEW_ANGLE, startAngle);
        double clampedEnd = Math.min(MAX_SKEW_ANGLE, endAngle);
        double bestAngle = 0.0;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (double angle = clampedStart; angle <= clampedEnd + (step / 2.0); angle += step) {
            BufferedImage rotated = rotateImage(image, angle);
            double score = projectionScore(rotated);
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }

        return bestAngle;
    }

    private BufferedImage cropToContentBounds(BufferedImage source) {
        return cropToContentBounds(source, findContentBounds(source));
    }

    private BufferedImage cropToContentBounds(BufferedImage source, ContentBounds bounds) {
        if (bounds.isEmpty()) {
            return source;
        }

        int left = Math.max(0, bounds.minX - CONTENT_PADDING);
        int top = Math.max(0, bounds.minY - CONTENT_PADDING);
        int right = Math.min(source.getWidth() - 1, bounds.maxX + CONTENT_PADDING);
        int bottom = Math.min(source.getHeight() - 1, bounds.maxY + CONTENT_PADDING);

        return source.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    private ContentBounds findContentBounds(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) & 0x00FFFFFF) == 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        return new ContentBounds(minX, minY, maxX, maxY);
    }

    private boolean hasEnoughContentForDeskew(ContentBounds bounds, int imageHeight) {
        if (bounds.isEmpty()) {
            return false;
        }

        return bounds.height() >= Math.max(1.0, imageHeight * MINIMUM_CONTENT_HEIGHT_RATIO);
    }

    private double projectionScore(BufferedImage image) {
        BufferedImage contentImage = cropToContentBounds(image);
        int verticalMargin = Math.max(1, contentImage.getHeight() / 40);
        int horizontalMargin = Math.max(1, contentImage.getWidth() / 40);
        int startY = Math.min(verticalMargin, contentImage.getHeight() - 1);
        int endY = Math.max(startY + 1, contentImage.getHeight() - verticalMargin);
        int startX = Math.min(horizontalMargin, contentImage.getWidth() - 1);
        int endX = Math.max(startX + 1, contentImage.getWidth() - horizontalMargin);

        double score = 0.0;
        int previousRowCount = 0;
        int inkPixels = 0;

        for (int y = startY; y < endY; y++) {
            int rowCount = 0;
            for (int x = startX; x < endX; x++) {
                if ((contentImage.getRGB(x, y) & 0x00FFFFFF) == 0) {
                    rowCount++;
                }
            }

            int difference = rowCount - previousRowCount;
            score += (double) difference * difference;
            previousRowCount = rowCount;
            inkPixels += rowCount;
        }

        if (inkPixels == 0) {
            return Double.NEGATIVE_INFINITY;
        }

        return score / inkPixels;
    }

    private BufferedImage rotateImage(BufferedImage source, double angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int width = source.getWidth();
        int height = source.getHeight();
        int rotatedWidth = (int) Math.floor(width * cos + height * sin);
        int rotatedHeight = (int) Math.floor(height * cos + width * sin);

        int imageType = source.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_RGB : source.getType();
        BufferedImage rotated = new BufferedImage(rotatedWidth, rotatedHeight, imageType);
        Graphics2D graphics = rotated.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, rotatedWidth, rotatedHeight);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform transform = new AffineTransform();
        transform.translate((rotatedWidth - width) / 2.0, (rotatedHeight - height) / 2.0);
        transform.rotate(radians, width / 2.0, height / 2.0);
        graphics.drawRenderedImage(source, transform);
        graphics.dispose();
        return rotated;
    }

    private Path resolveDeskewTempDir(PdfOptions options) {
        Path configuredDir = options.deskewTempDir();
        if (configuredDir != null) {
            return configuredDir.toAbsolutePath().normalize();
        }

        return DEFAULT_DESKEW_TEMP_DIR.toAbsolutePath().normalize();
    }

    private Path createDeskewFile(Path directory) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt < 10; attempt++) {
            Path candidate = directory.resolve("img2pdf-deskew-" + UUID.randomUUID() + ".png");
            try {
                return Files.createFile(candidate);
            } catch (IOException e) {
                lastException = e;
            }
        }

        throw new IOException("Failed to create deskew output file in " + directory, lastException);
    }

    private record ContentBounds(int minX, int minY, int maxX, int maxY) {
        boolean isEmpty() {
            return maxX < minX || maxY < minY;
        }

        int height() {
            return isEmpty() ? 0 : maxY - minY + 1;
        }
    }
}
