package io.img2pdf.cli;

import io.img2pdf.application.dto.ConvertImagesToPdfRequest;
import io.img2pdf.application.dto.ConvertImagesToPdfResult;
import io.img2pdf.application.inbound.ConvertImagesToPdfUseCase;
import io.img2pdf.domain.model.ImageCompression;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Scan2PdfCliCommandTest {
    @Test
    void callUsesCropByDefault() {
        CapturingUseCase useCase = new CapturingUseCase();
        Scan2PdfCliCommand command = new Scan2PdfCliCommand(useCase);

        int exitCode = new CommandLine(command).execute("scan-1.jpeg", "--output", "out.pdf");

        assertEquals(0, exitCode);
        assertNotNull(useCase.request);
        assertTrue(useCase.request.pdfOptions().crop());
        assertFalse(useCase.request.pdfOptions().cropToPageSize());
        assertFalse(useCase.request.pdfOptions().deskew());
        assertEquals(ImageCompression.JPEG, useCase.request.pdfOptions().imageCompression());
        assertEquals(75, useCase.request.pdfOptions().jpegQuality());
    }

    @Test
    void callAllowsDisablingCropExplicitly() {
        CapturingUseCase useCase = new CapturingUseCase();
        Scan2PdfCliCommand command = new Scan2PdfCliCommand(useCase);

        int exitCode = new CommandLine(command).execute("scan-1.jpeg", "--output", "out.pdf", "--crop=false", "--deskew");

        assertEquals(0, exitCode);
        assertNotNull(useCase.request);
        assertFalse(useCase.request.pdfOptions().crop());
        assertTrue(useCase.request.pdfOptions().deskew());
    }

    @Test
    void callMapsCropToPageSizeOption() {
        CapturingUseCase useCase = new CapturingUseCase();
        Scan2PdfCliCommand command = new Scan2PdfCliCommand(useCase);

        int exitCode = new CommandLine(command).execute(
                "scan-1.jpeg",
                "--output", "out.pdf",
                "--page-size", "A5",
                "--dpi", "300",
                "--crop-to-page-size"
        );

        assertEquals(0, exitCode);
        assertNotNull(useCase.request);
        assertTrue(useCase.request.pdfOptions().crop());
        assertTrue(useCase.request.pdfOptions().cropToPageSize());
        assertEquals(Integer.valueOf(300), useCase.request.pdfOptions().targetDpi());
    }

    @Test
    void callMapsPdfCompressionOptions() {
        CapturingUseCase useCase = new CapturingUseCase();
        Scan2PdfCliCommand command = new Scan2PdfCliCommand(useCase);

        int exitCode = new CommandLine(command).execute(
                "scan-1.jpeg",
                "--output", "out.pdf",
                "--dpi", "200",
                "--image-compression", "LOSSLESS",
                "--jpeg-quality", "55"
        );

        assertEquals(0, exitCode);
        assertNotNull(useCase.request);
        assertEquals(Integer.valueOf(200), useCase.request.pdfOptions().targetDpi());
        assertEquals(ImageCompression.LOSSLESS, useCase.request.pdfOptions().imageCompression());
        assertEquals(55, useCase.request.pdfOptions().jpegQuality());
        assertEquals(Integer.valueOf(200), useCase.request.ocrOptions().dpi());
    }

    private static final class CapturingUseCase implements ConvertImagesToPdfUseCase {
        private ConvertImagesToPdfRequest request;

        @Override
        public ConvertImagesToPdfResult handle(ConvertImagesToPdfRequest request) {
            this.request = request;
            return new ConvertImagesToPdfResult(Path.of("out.pdf"), request.inputPaths(), "");
        }
    }
}
