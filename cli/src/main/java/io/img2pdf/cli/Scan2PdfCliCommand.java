package io.img2pdf.cli;

import io.img2pdf.application.dto.ConvertImagesToPdfRequest;
import io.img2pdf.application.inbound.ConvertImagesToPdfUseCase;
import io.img2pdf.domain.model.OcrOptions;
import io.img2pdf.domain.model.PageSize;
import io.img2pdf.domain.model.PdfOptions;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "image2pdf",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "Convert images to PDF and optionally run OCR."
)
public class Scan2PdfCliCommand implements Callable<Integer> {

    private final ConvertImagesToPdfUseCase useCase;

    public Scan2PdfCliCommand(ConvertImagesToPdfUseCase convertImagesToPdfUseCase) {
        this.useCase = convertImagesToPdfUseCase;
    }

    @CommandLine.Parameters(arity = "1..*", description = "Input image files or directories")
    private List<Path> inputs;

    @CommandLine.Option(names = {"-o", "--output"}, required = true, description = "Output PDF path")
    private Path outputPdf;

    @CommandLine.Option(names = {"--page-size"}, defaultValue = "ORIGINAL")
    private PageSize pageSize;

    @CommandLine.Option(names = {"--stretch"}, defaultValue = "false")
    private boolean stretch;

    @CommandLine.Option(names = {"--deskew"}, defaultValue = "false", description = "Deskew text-heavy scanned images before PDF/OCR")
    private boolean deskew;

    @CommandLine.Option(
            names = {"--deskew-temp-dir"},
            description = "Directory for intermediate deskew images. Defaults to ./.img2pdf-temp"
    )
    private Path deskewTempDir;

    @CommandLine.Option(names = {"--ocr"}, defaultValue = "false")
    private boolean ocrEnabled;

    @CommandLine.Option(names = {"--lang"}, defaultValue = "eng")
    private String language;

    @CommandLine.Option(names = {"--tessdata"})
    private String tessdataPath;

    @CommandLine.Option(names = {"--dpi"})
    private Integer dpi;

    @CommandLine.Option(names = {"--psm"})
    private Integer psm;

    @CommandLine.Option(names = {"--ocr-text-out"})
    private Path ocrTextOutput;

    @Override
    public Integer call() throws Exception {
        try {
            var request = new ConvertImagesToPdfRequest(
                    inputs,
                    outputPdf,
                    ocrTextOutput,
                    new PdfOptions(pageSize, !stretch, deskew, deskewTempDir),
                    new OcrOptions(ocrEnabled, language, tessdataPath, dpi, psm));

            var result = useCase.handle(request);

            System.out.println("PDF created: " + result.outputPdf());

            if (!result.ocrText().isBlank()) {
                if (ocrTextOutput != null) {
                    System.out.println("OCR test written to: " + ocrTextOutput);
                } else {
                    System.out.println("----- OCR RESULT -----");
                    System.out.println(result.ocrText());
                }
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
            return 1;
        }
    }
}
