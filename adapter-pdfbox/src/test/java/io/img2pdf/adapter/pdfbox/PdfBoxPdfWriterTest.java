package io.img2pdf.adapter.pdfbox;

import io.img2pdf.domain.model.ImageCompression;
import io.img2pdf.domain.model.PageSize;
import io.img2pdf.domain.model.PdfOptions;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfBoxPdfWriterTest {
    @Test
    void resolvePlacementCentersCroppedImagesUsingContentScale() {
        PdfBoxPdfWriter writer = new PdfBoxPdfWriter();
        BufferedImage croppedImage = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB);
        PdfOptions options = new PdfOptions(PageSize.A5, true, false, true, false, null, null, ImageCompression.JPEG, 75);
        ProcessedImageLayoutRegistry.ProcessedImageLayout layout =
                new ProcessedImageLayoutRegistry.ProcessedImageLayout(1000, 1400, 100, 150);

        PdfBoxPdfWriter.ImagePlacement placement = writer.resolvePlacement(PDRectangle.A5, croppedImage, options, layout);

        float expectedScale = Math.min(PDRectangle.A5.getWidth() / 800f, PDRectangle.A5.getHeight() / 1000f);
        assertEquals(800f * expectedScale, placement.drawWidth(), 0.01f);
        assertEquals(1000f * expectedScale, placement.drawHeight(), 0.01f);
        assertEquals((PDRectangle.A5.getWidth() - (800f * expectedScale)) / 2f, placement.x(), 0.01f);
        assertEquals((PDRectangle.A5.getHeight() - (1000f * expectedScale)) / 2f, placement.y(), 0.01f);
    }

    @Test
    void resolvePlacementMatchesPreviousBehaviorWithoutCropMetadata() {
        PdfBoxPdfWriter writer = new PdfBoxPdfWriter();
        BufferedImage image = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB);
        PdfOptions options = new PdfOptions(PageSize.A5, true, false, false, false, null, null, ImageCompression.JPEG, 75);
        ProcessedImageLayoutRegistry.ProcessedImageLayout layout =
                new ProcessedImageLayoutRegistry.ProcessedImageLayout(800, 1000, 0, 0);

        PdfBoxPdfWriter.ImagePlacement placement = writer.resolvePlacement(PDRectangle.A5, image, options, layout);

        float expectedScale = Math.min(PDRectangle.A5.getWidth() / 800f, PDRectangle.A5.getHeight() / 1000f);
        assertEquals(800f * expectedScale, placement.drawWidth(), 0.01f);
        assertEquals(1000f * expectedScale, placement.drawHeight(), 0.01f);
        assertEquals((PDRectangle.A5.getWidth() - (800f * expectedScale)) / 2f, placement.x(), 0.01f);
        assertEquals((PDRectangle.A5.getHeight() - (1000f * expectedScale)) / 2f, placement.y(), 0.01f);
    }

    @Test
    void writeUsesJpegCompressionAndTargetDpiToReducePdfSize(@TempDir Path tempDir) throws Exception {
        PdfBoxPdfWriter writer = new PdfBoxPdfWriter();
        BufferedImage image = new BufferedImage(2200, 3200, BufferedImage.TYPE_INT_RGB);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }

        Path imagePath = tempDir.resolve("source.png");
        ImageIO.write(image, "png", imagePath.toFile());

        Path losslessPdf = tempDir.resolve("lossless.pdf");
        writer.write(
                java.util.List.of(imagePath),
                losslessPdf,
                new PdfOptions(PageSize.A4, true, false, false, false, null, null, ImageCompression.LOSSLESS, 75)
        );

        Path compressedPdf = tempDir.resolve("compressed.pdf");
        writer.write(
                java.util.List.of(imagePath),
                compressedPdf,
                new PdfOptions(PageSize.A4, true, false, false, false, null, 120, ImageCompression.JPEG, 40)
        );

        assertTrue(java.nio.file.Files.size(compressedPdf) < java.nio.file.Files.size(losslessPdf));
    }
}
