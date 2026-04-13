package io.img2pdf.application.service;

import io.img2pdf.application.dto.ConvertImagesToPdfRequest;
import io.img2pdf.application.outbound.ImagePreProcessorPort;
import io.img2pdf.application.outbound.OcrProcessorPort;
import io.img2pdf.application.outbound.PdfWriterPort;
import io.img2pdf.domain.model.ImageCompression;
import io.img2pdf.domain.model.OcrOptions;
import io.img2pdf.domain.model.PageSize;
import io.img2pdf.domain.model.PdfOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertImagesToPdfServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void handlePreprocessesImagesConcurrentlyWhilePreservingInputOrder() {
        List<Path> inputs = List.of(
                Path.of("scan-1.jpeg"),
                Path.of("scan-2.jpeg"),
                Path.of("scan-3.jpeg"),
                Path.of("scan-4.jpeg")
        );
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger maxConcurrentTasks = new AtomicInteger();
        Set<String> threadsUsed = ConcurrentHashMap.newKeySet();
        CountDownLatch firstWorkersStarted = new CountDownLatch(2);

        FileCollector fileCollector = new StubFileCollector(inputs);
        ImagePreProcessorPort preProcessor = (imagePath, options) -> {
            threadsUsed.add(Thread.currentThread().getName());
            firstWorkersStarted.countDown();
            int current = activeTasks.incrementAndGet();
            maxConcurrentTasks.accumulateAndGet(current, Math::max);
            try {
                assertTrue(firstWorkersStarted.await(2, TimeUnit.SECONDS), "Expected worker threads to overlap.");
                TimeUnit.MILLISECONDS.sleep(150);
                return Path.of(imagePath.toString() + ".deskewed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                activeTasks.decrementAndGet();
            }
        };
        CapturingPdfWriter pdfWriter = new CapturingPdfWriter();
        OcrProcessorPort ocrProcessor = (imagePath, options) -> "";
        ConvertImagesToPdfService service = new ConvertImagesToPdfService(
                ocrProcessor,
                pdfWriter,
                preProcessor,
                fileCollector
        );

        service.handle(new ConvertImagesToPdfRequest(
                inputs,
                Path.of("out.pdf"),
                null,
                new PdfOptions(PageSize.ORIGINAL, true, true, true, false, null, null, ImageCompression.JPEG, 75),
                new OcrOptions(false, "eng", null, null, null)
        ));

        assertEquals(
                List.of(
                        Path.of("scan-1.jpeg.deskewed"),
                        Path.of("scan-2.jpeg.deskewed"),
                        Path.of("scan-3.jpeg.deskewed"),
                        Path.of("scan-4.jpeg.deskewed")
                ),
                pdfWriter.imagePaths
        );
        assertTrue(maxConcurrentTasks.get() > 1, "Expected image preprocessing to overlap.");
        assertTrue(threadsUsed.size() > 1, "Expected multiple worker threads to be used.");
    }

    @Test
    void handleSkipsPreprocessingWhenDeskewAndCropAreBothDisabled() {
        List<Path> inputs = List.of(
                Path.of("scan-1.jpeg"),
                Path.of("scan-2.jpeg")
        );
        AtomicInteger preprocessCalls = new AtomicInteger();

        FileCollector fileCollector = new StubFileCollector(inputs);
        ImagePreProcessorPort preProcessor = (imagePath, options) -> {
            preprocessCalls.incrementAndGet();
            return Path.of(imagePath.toString() + ".processed");
        };
        CapturingPdfWriter pdfWriter = new CapturingPdfWriter();
        OcrProcessorPort ocrProcessor = (imagePath, options) -> "";
        ConvertImagesToPdfService service = new ConvertImagesToPdfService(
                ocrProcessor,
                pdfWriter,
                preProcessor,
                fileCollector
        );

        service.handle(new ConvertImagesToPdfRequest(
                inputs,
                Path.of("out.pdf"),
                null,
                new PdfOptions(PageSize.ORIGINAL, true, false, false, false, null, null, ImageCompression.JPEG, 75),
                new OcrOptions(false, "eng", null, null, null)
        ));

        assertEquals(0, preprocessCalls.get());
        assertEquals(inputs, pdfWriter.imagePaths);
    }

    @Test
    void collectImagesSkipsNonImageFilesWithImageExtensions() throws IOException {
        Path validImage = tempDir.resolve("scan-1.jpeg");
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpeg", validImage.toFile());

        Path invalidImage = tempDir.resolve("scan-2.jpeg");
        Files.writeString(invalidImage, "not an image");

        FileCollector fileCollector = new FileCollector();

        List<Path> collected = fileCollector.collectImages(List.of(tempDir));

        assertEquals(List.of(validImage), collected);
    }

    private static final class StubFileCollector extends FileCollector {
        private final List<Path> images;

        private StubFileCollector(List<Path> images) {
            this.images = images;
        }

        @Override
        public List<Path> collectImages(List<Path> inputs) {
            return images;
        }
    }

    private static final class CapturingPdfWriter implements PdfWriterPort {
        private List<Path> imagePaths = new ArrayList<>();

        @Override
        public void write(List<Path> imagePaths, Path outputPdf, PdfOptions options) {
            this.imagePaths = List.copyOf(imagePaths);
        }
    }
}
