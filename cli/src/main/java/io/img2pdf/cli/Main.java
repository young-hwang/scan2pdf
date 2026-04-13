package io.img2pdf.cli;

import io.img2pdf.adapter.pdfbox.DeskewImagePreProcessor;
import io.img2pdf.adapter.pdfbox.PdfBoxPdfWriter;
import io.img2pdf.adapter.tess4j.Tess4JOcrProcessor;
import io.img2pdf.application.service.ConvertImagesToPdfService;
import io.img2pdf.application.service.FileCollector;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {

        var pdfWriter = new PdfBoxPdfWriter();
        var imagePreProcessor = new DeskewImagePreProcessor();
        var ocrProcessor = new Tess4JOcrProcessor();
        var fileCollector = new FileCollector();
        var useCase = new ConvertImagesToPdfService(ocrProcessor, pdfWriter, imagePreProcessor, fileCollector);
        var command = new Scan2PdfCliCommand(useCase);
        int exitCode = new CommandLine(command).execute(args);
        System.exit(exitCode);
    }
}
