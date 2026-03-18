# scan2pdf Install Guide

## Requirements

- Python 3.9+
- `Pillow`
- `opencv-python` for deskew support

## Install

```bash
python -m venv .venv
source .venv/bin/activate
pip install Pillow opencv-python
```

## Minimal Dependency Setup

If you do not need deskew support, install only `Pillow`.

```bash
pip install Pillow
```
