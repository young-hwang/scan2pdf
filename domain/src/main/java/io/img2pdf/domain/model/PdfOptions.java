package io.img2pdf.domain.model;

import java.nio.file.Path;

public record PdfOptions(
    PageSize pageSize,
    boolean keepAspectRatio,
    boolean deskew,
    Path deskewTempDir) {
}
