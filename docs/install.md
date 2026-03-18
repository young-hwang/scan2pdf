# scan2pdf Install Guide

## Requirements

- Python 3.9+
- `pip`

## Install Directly From GitHub

```bash
python3 -m pip install "git+https://github.com/young-hwang/scan2pdf.git"
```

Verify the CLI:

```bash
scan2pdf --version
```

## Install As an Isolated CLI

If you prefer not to install into your current Python environment:

```bash
pipx install "git+https://github.com/young-hwang/scan2pdf.git"
```

## Install From a Local Clone

```bash
git clone https://github.com/young-hwang/scan2pdf.git
cd scan2pdf
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install --upgrade pip
python3 -m pip install .
```

Verify the CLI:

```bash
scan2pdf --version
```

## Install With Deskew Support From GitHub

If you want `--deskew` without cloning the repository:

```bash
python3 -m pip install "scan2pdf[deskew] @ git+https://github.com/young-hwang/scan2pdf.git"
```

## Install With Deskew Support From a Local Clone

If you already cloned the repository:

```bash
python3 -m pip install ".[deskew]"
```

## First Run

```bash
scan2pdf ./scans ./output/book.pdf
```
