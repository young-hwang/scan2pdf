package io.img2pdf.application.outbound;

import io.img2pdf.domain.model.PdfOptions;

import java.nio.file.Path;

public interface ImagePreProcessorPort {
    Path preprocess(Path imagePath, PdfOptions options);
}
