package io.img2pdf.application.service;

import io.img2pdf.application.dto.ConvertImagesToPdfRequest;
import io.img2pdf.application.dto.ConvertImagesToPdfResult;
import io.img2pdf.application.inbound.ConvertImagesToPdfUseCase;
import io.img2pdf.application.outbound.ImagePreProcessorPort;
import io.img2pdf.application.outbound.OcrProcessorPort;
import io.img2pdf.application.outbound.PdfWriterPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ConvertImagesToPdfService implements ConvertImagesToPdfUseCase {

    private final OcrProcessorPort ocrProcessorPort;
    private final PdfWriterPort pdfWriterPort;
    private final ImagePreProcessorPort imagePreProcessorPort;
    private final FileCollector fileCollector;

    public ConvertImagesToPdfService(
            OcrProcessorPort ocrProcessorPort,
            PdfWriterPort pdfWriterPort,
            ImagePreProcessorPort imagePreProcessorPort,
            FileCollector fileCollector
    ) {
        this.ocrProcessorPort = ocrProcessorPort;
        this.pdfWriterPort = pdfWriterPort;
        this.imagePreProcessorPort = imagePreProcessorPort;
        this.fileCollector = fileCollector;
    }

    @Override
    public ConvertImagesToPdfResult handle(ConvertImagesToPdfRequest request) {
        List<Path> imageFiles = fileCollector.collectImages(request.inputPaths());

        if (imageFiles.isEmpty()) {
            throw new IllegalArgumentException("No supported image files found.");
        }

        List<Path> processedImageFiles = imageFiles;
        try {
            processedImageFiles = preprocessImages(imageFiles, request);

            createParentDirectoryIfNeeded(request.outputPdf());
            pdfWriterPort.write(processedImageFiles, request.outputPdf(), request.pdfOptions());

            String ocrText = "";
            if (request.ocrOptions().enabled()) {
                StringBuilder sb = new StringBuilder();

                for (Path imageFile : processedImageFiles) {
                    String text = ocrProcessorPort.extractText(imageFile, request.ocrOptions());
                    sb.append("===== ")
                            .append(imageFile.getFileName())
                            .append(" =====")
                            .append(System.lineSeparator())
                            .append(text)
                            .append(System.lineSeparator())
                            .append(System.lineSeparator());
                }

                ocrText = sb.toString();

                if (request.ocrTextOutput() != null) {
                    writeTextFile(request.ocrTextOutput(), ocrText);
                }
            }

            return new ConvertImagesToPdfResult(
                    request.outputPdf(),
                    imageFiles,
                    ocrText
            );
        } finally {
            deleteTemporaryImages(imageFiles, processedImageFiles);
        }
    }

    private List<Path> preprocessImages(List<Path> imageFiles, ConvertImagesToPdfRequest request) {
        if (!request.pdfOptions().deskew()) {
            return imageFiles;
        }

        int workerCount = Math.min(
                imageFiles.size(),
                Math.max(2, Runtime.getRuntime().availableProcessors())
        );
        if (workerCount <= 1) {
            return imageFiles.stream()
                    .map(imagePath -> imagePreProcessorPort.preprocess(imagePath, request.pdfOptions()))
                    .toList();
        }

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        try {
            List<Future<Path>> futures = imageFiles.stream()
                    .map(imagePath -> executor.submit(() -> imagePreProcessorPort.preprocess(imagePath, request.pdfOptions())))
                    .toList();

            return futures.stream()
                    .map(this::awaitPreprocessedImage)
                    .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private Path awaitPreprocessedImage(Future<Path> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Image preprocessing was interrupted.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Image preprocessing failed.", e.getCause());
        }
    }

    private void deleteTemporaryImages(List<Path> originalImages, List<Path> processedImages) {
        Set<Path> originalPaths = new HashSet<>(originalImages.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList());

        for (Path processedImage : processedImages) {
            Path normalizedPath = processedImage.toAbsolutePath().normalize();
            if (!originalPaths.contains(normalizedPath)) {
                try {
                    Files.deleteIfExists(processedImage);
                } catch (IOException ignored) {
                    // Temporary files are best-effort cleanup.
                }
            }
        }
    }

    private void writeTextFile(Path output, String text) {
        try {
            createParentDirectoryIfNeeded(output);
            Files.writeString(output, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write OCR text file: " + output, e);
        }
    }

    private void createParentDirectoryIfNeeded(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create parent directory for: " + file, e);
        }
    }
}
