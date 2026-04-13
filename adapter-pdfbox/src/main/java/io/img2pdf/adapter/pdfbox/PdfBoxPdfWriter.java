package io.img2pdf.adapter.pdfbox;

import io.img2pdf.application.outbound.PdfWriterPort;
import io.img2pdf.domain.model.ImageCompression;
import io.img2pdf.domain.model.PageSize;
import io.img2pdf.domain.model.PdfOptions;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class PdfBoxPdfWriter implements PdfWriterPort {
    @Override
    public void write(List<Path> imagePaths, Path outputPdf, PdfOptions options) {
        try (PDDocument document = new PDDocument()) {
            for (Path imagePath : imagePaths) {
                BufferedImage image = ImageIO.read(imagePath.toFile());
                if (image == null) {
                    throw new IllegalArgumentException("Unsupported image file " + imagePath);
                }

                ProcessedImageLayoutRegistry.ProcessedImageLayout layout =
                        resolveLayout(imagePath, image);
                PDRectangle rectangle = resolvePageSize(options.pageSize(), image, layout);
                PDPage page = new PDPage(rectangle);
                document.addPage(page);

                ImagePlacement placement = resolvePlacement(rectangle, image, options, layout);
                BufferedImage embeddedImage = resizeForEmbedding(image, placement, options);
                PDImageXObject pdImage = toPdfImage(document, embeddedImage, options);

                try(PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.drawImage(pdImage, placement.x(), placement.y(), placement.drawWidth(), placement.drawHeight());
                }

                ProcessedImageLayoutRegistry.remove(imagePath);
            }

            document.save(outputPdf.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create PDF: " + outputPdf, e);
        }
    }

    private ProcessedImageLayoutRegistry.ProcessedImageLayout resolveLayout(Path imagePath, BufferedImage image) {
        ProcessedImageLayoutRegistry.ProcessedImageLayout layout = ProcessedImageLayoutRegistry.lookup(imagePath);
        if (layout != null) {
            return layout;
        }

        return new ProcessedImageLayoutRegistry.ProcessedImageLayout(image.getWidth(), image.getHeight(), 0, 0);
    }

    ImagePlacement resolvePlacement(
            PDRectangle rectangle,
            BufferedImage image,
            PdfOptions options,
            ProcessedImageLayoutRegistry.ProcessedImageLayout layout
    ) {
        float pageWidth = rectangle.getWidth();
        float pageHeight = rectangle.getHeight();
        float imageWidth = image.getWidth();
        float imageHeight = image.getHeight();

        if (!options.keepAspectRatio()) {
            return new ImagePlacement(pageWidth, pageHeight, 0, 0);
        }

        boolean cropped = isCropped(image, layout);
        float referenceWidth = cropped ? imageWidth : layout.originalWidth();
        float referenceHeight = cropped ? imageHeight : layout.originalHeight();
        float widthScale = pageWidth / referenceWidth;
        float heightScale = pageHeight / referenceHeight;
        float scale = Math.min(widthScale, heightScale);

        float drawWidth = imageWidth * scale;
        float drawHeight = imageHeight * scale;
        float x = (pageWidth - drawWidth) / 2f;
        float y = (pageHeight - drawHeight) / 2f;

        return new ImagePlacement(drawWidth, drawHeight, x, y);
    }

    private boolean isCropped(
            BufferedImage image,
            ProcessedImageLayoutRegistry.ProcessedImageLayout layout
    ) {
        return image.getWidth() != layout.originalWidth()
                || image.getHeight() != layout.originalHeight()
                || layout.offsetX() != 0
                || layout.offsetY() != 0;
    }

    private PDRectangle resolvePageSize(
            PageSize pageSize,
            BufferedImage image,
            ProcessedImageLayoutRegistry.ProcessedImageLayout layout
    ) {
        return switch (pageSize) {
            case A4 -> PDRectangle.A4;
            case A5 -> PDRectangle.A5;
            case LETTER -> PDRectangle.LETTER;
            case ORIGINAL -> new PDRectangle(layout.originalWidth(), layout.originalHeight());
        };
    }

    private PDImageXObject toPdfImage(PDDocument document, BufferedImage image, PdfOptions options) throws IOException {
        if (options.imageCompression() == ImageCompression.LOSSLESS) {
            return LosslessFactory.createFromImage(document, image);
        }
        return JPEGFactory.createFromImage(document, ensureRgb(image), options.jpegQuality() / 100f);
    }

    private BufferedImage resizeForEmbedding(BufferedImage image, ImagePlacement placement, PdfOptions options) {
        Integer targetDpi = options.targetDpi();
        if (targetDpi == null) {
            return image;
        }

        int targetWidth = Math.max(1, Math.round((placement.drawWidth() / 72f) * targetDpi));
        int targetHeight = Math.max(1, Math.round((placement.drawHeight() / 72f) * targetDpi));
        if (image.getWidth() <= targetWidth && image.getHeight() <= targetHeight) {
            return image;
        }

        BufferedImage source = options.imageCompression() == ImageCompression.JPEG ? ensureRgb(image) : image;
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, source.getType());
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private BufferedImage ensureRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }

        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgbImage;
    }

    record ImagePlacement(float drawWidth, float drawHeight, float x, float y) {
    }
}
