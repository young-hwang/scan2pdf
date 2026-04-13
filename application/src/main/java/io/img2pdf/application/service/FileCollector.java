package io.img2pdf.application.service;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FileCollector {
    public List<Path> collectImages(List<Path> inputs) {
        try {
            List<Path> result = new ArrayList<>();

            for (Path input : inputs) {
                if (Files.isDirectory(input)) {
                    try (Stream<Path> stream = Files.walk(input)) {
                        stream.filter(Files::isRegularFile)
                                .filter(this::isSupportedImage)
                                .forEach(result::add);
                    }
                } else if (Files.isRegularFile(input) && isSupportedImage(input)) {
                    result.add(input);
                }
            }
            result.sort(Comparator.comparing(path -> path.toAbsolutePath().toString()));
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to collect image files", e);
        }
    }

    private boolean isSupportedImage(Path path) {
        var name = path.getFileName().toString().toLowerCase();
        boolean supportedExtension = name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".bmp")
                || name.endsWith(".gif")
                || name.endsWith(".tif")
                || name.endsWith(".tiff");
        if (!supportedExtension) {
            return false;
        }

        try (ImageInputStream stream = ImageIO.createImageInputStream(path.toFile())) {
            if (stream == null) {
                return false;
            }
            return ImageIO.getImageReaders(stream).hasNext();
        } catch (IOException e) {
            return false;
        }
    }
}
