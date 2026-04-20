# img2pdf Install Guide

## Requirements

- JDK 21
- Gradle wrapper support from this repository
- `tesseract` on `PATH` if you want OCR

## Build From a Local Clone

```bash
git clone https://github.com/young-hwang/img2pdf.git
cd img2pdf
./gradlew :cli:installDist
```

This generates a standalone launcher under `cli/build/install/cli/bin/`.

## Verify the CLI

```bash
./cli/build/install/cli/bin/img2pdf-cli --help
```

## Run Without Installing the Distribution

For development, you can run the CLI directly through Gradle:

```bash
./gradlew :cli:run --args="./images --output ./output/book.pdf"
```

## OCR Requirements

`--ocr` requires:

- system `tesseract`
- installed Tesseract language data such as `kor` and `eng`

Example on Homebrew:

```bash
brew install tesseract tesseract-lang
```

Then verify:

```bash
tesseract --list-langs
```

## First Run

```bash
./cli/build/install/cli/bin/img2pdf-cli ./images --output ./output/book.pdf
```
