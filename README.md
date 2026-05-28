# alfresco-transform-extras

Additional [Alfresco Transform Engines](https://github.com/Alfresco/alfresco-transform-core) not included in the official AIO image. Engines are fully compatible with the Alfresco transform framework and can be deployed **individually** as standalone containers or **together** as a single AIO image.

## Engines

| Engine | Source → Target | External tool |
|--------|----------------|---------------|
| `convert2md` | PDF → Markdown | [Docling](https://github.com/docling-project/docling) |
| `md2doc` | Markdown → DOCX, PDF | [Pandoc](https://pandoc.org) + XeLaTeX |
| `markdown` | Markdown → PDF | Pandoc + XeLaTeX |
| `html2md` | HTML, XHTML → Markdown | Pandoc |
| `md2html` | Markdown → HTML | Pandoc |
| `msg` | MSG, EML → PDF | Apache POI HSMF + Pandoc |
| `ocr` | PDF → searchable PDF | [ocrmypdf](https://ocrmypdf.readthedocs.io) + Tesseract |
| `pii` | PDF → redacted PDF, metadata | [Presidio](https://microsoft.github.io/presidio) + PyMuPDF |
| `pdf2docx` | PDF → DOCX | [pdf2docx](https://pdf2docx.readthedocs.io) |
| `whisper` | Audio/Video → text | [OpenAI Whisper](https://github.com/openai/whisper) |
| `videothumb` | Video → JPEG thumbnail | ffmpeg |
| `heic` | HEIC/HEIF → JPEG, PNG | libheif |
| `excel` | XLS, XLSX → metadata | Apache POI |
| `xml` | XML → metadata | Java DOM |

## After cloning

Binary probe sample files (`.pdf`, `.xlsx`, `.mp3`, `.mp4`, `.heic`) are git-ignored. Regenerate them after a fresh clone with:

```bash
make generate-samples   # requires pandoc, ffmpeg, heif-enc, python3+openpyxl
```

## Quick start

### AIO container (all 14 engines)

```bash
git clone https://github.com/angelborroy/alfresco-transform-extras
cd alfresco-transform-extras
make build
docker compose up
```

The container starts on port **8090**. Verify:

```bash
make health   # {"status":"UP"}
make test     # lists all 15 transformer names
```

### Single engine

```bash
# Build one engine image
make build-xml

# Run it
make run-engine ENGINE=xml PORT=8090
```

Or with Docker directly:

```bash
docker build -t alf-tengine-xml engines/xml/
docker run -p 8090:8090 -e MANAGEMENT_HEALTH_JMS_ENABLED=false alf-tengine-xml
```

## Version pinning

External tool versions are controlled via Docker build ARGs with stable defaults:

| ARG | Default | Engines |
|-----|---------|---------|
| `PANDOC_VERSION` | `3.9.0.2` | md2doc, markdown, html2md, md2html, msg |
| `OCRMYPDF_VERSION` | `17.5.0` | ocr |
| `TESSERACT_LANGUAGES` | `eng` | ocr |
| `WHISPER_VERSION` | `20250625` | whisper |
| `PDF2DOCX_VERSION` | `0.5.13` | pdf2docx |

Override at build time:

```bash
make build PANDOC_VERSION=3.5 TESSERACT_LANGUAGES=eng,spa,fra
make build-ocr OCRMYPDF_VERSION=16 TESSERACT_LANGUAGES=eng,deu
```

## Makefile reference

```
# Single-platform (loads into local daemon)
make build                  Maven + AIO Docker image
make build-<name>           Individual engine (e.g. make build-ocr)
make build-engines          All 14 individual images

# Multi-platform linux/amd64 + linux/arm64 — pushes to registry with SBOM + provenance
make buildx                 AIO image
make buildx-<name>          Individual engine (e.g. make buildx-ocr)
make buildx-engines         All 14 individual images
make buildx-all             AIO + all 14 engines

# Run
make run                    docker compose up (AIO)
make run-engine ENGINE=<name> PORT=<port>

# Verify
make test                   List transformer names on localhost:8090
make health                 Health check on localhost:8090

# Push (single-platform)
make push / push-engines / push-all

# Clean
make clean-all
```

Override variables on any target:
```bash
make buildx PLATFORMS=linux/amd64,linux/arm64 VERSION=1.0.0
make buildx-ocr TESSERACT_LANGUAGES=eng,spa,fra OCRMYPDF_VERSION=16
make buildx BUILDER=my-builder
```

## Superseded projects

This project consolidates the following standalone repositories, which are no longer maintained individually:

| Repository | Engine in this project |
|---|---|
| [alf-tengine-convert2md](https://github.com/aborroy/alf-tengine-convert2md) | `convert2md` |
| [alf-tengine-md2doc](https://github.com/aborroy/alf-tengine-md2doc) | `md2doc` |
| [alf-tengine-markdown](https://github.com/aborroy/alf-tengine-markdown) | `markdown` |
| [alf-tengine-ocr](https://github.com/aborroy/alf-tengine-ocr) | `ocr` |
| [alf-tengine-pii](https://github.com/aborroy/alf-tengine-pii) | `pii` |
| [alf-tengine-xml](https://github.com/aborroy/alf-tengine-xml) | `xml` |
| [alf-tengine-excel](https://github.com/aborroy/alf-tengine-excel) | `excel` |

## Compatibility

Inherits from `org.alfresco:alfresco-transform-core:5.4.1`. Engines work alongside the official AIO (`imagemagick`, `libreoffice`, `tika`, `pdfrenderer`, `misc`) behind an Alfresco Transform Router.

To register an individual engine with a Community deployment add to `alfresco-global.properties`:

```properties
localTransform.ocr.url=http://localhost:8090/
```

## Project structure

```
engines/
  <name>/
    src/main/java/org/alfresco/transform/<name>/
      <Name>Engine.java          # TransformEngine — registers the engine
      <Name>Transformer.java     # CustomTransformer — does the work
      <Name>Service.java         # wraps the external CLI tool
    src/main/resources/
      <name>_engine_config.json  # supported MIME type pairs + options
      application-default.yaml  # queue name, tool defaults
      sample.<ext>               # probe transform file
    Dockerfile                   # standalone image
    pom.xml
  aio/
    pom.xml                      # assembly: depends on all engines → single fat JAR
Dockerfile                       # AIO multi-stage image
compose.yaml
Makefile
docker/presidio/                 # Python scripts for PII engine
```
