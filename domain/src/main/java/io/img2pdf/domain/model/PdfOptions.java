package io.img2pdf.domain.model;

import java.nio.file.Path;

public record PdfOptions(
    PageSize pageSize,
    boolean keepAspectRatio,
    boolean deskew,
    boolean crop,
    boolean cropToPageSize,
    Path deskewTempDir,
    Integer targetDpi,
    ImageCompression imageCompression,
    int jpegQuality) {

    public PdfOptions {
        if (targetDpi != null && targetDpi <= 0) {
            throw new IllegalArgumentException("targetDpi must be a positive integer.");
        }
        if (jpegQuality < 1 || jpegQuality > 100) {
            throw new IllegalArgumentException("jpegQuality must be between 1 and 100.");
        }
        if (imageCompression == null) {
            imageCompression = ImageCompression.JPEG;
        }
    }
}
