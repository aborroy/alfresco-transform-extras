# alfresco-transform-extras

Additional [Alfresco Transform Engines](https://github.com/Alfresco/alfresco-transform-core) not included in the official AIO image. Engines are fully compatible with the Alfresco transform framework and can be deployed **individually** as standalone containers or **together** as a single AIO image.

## Engines

| Engine | Source → Target | External tool |
|--------|----------------|---------------|
| `convert2md` | PDF → Markdown (tables preserved) | [Docling](https://github.com/docling-project/docling) |
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
| `ai` | Text, PDF → AI metadata | Local LLM via [Docker Model Runner](https://www.docker.com/products/model-runner/) |
| `liteparse` | PDF, DOCX, XLSX, XLS, PPTX, DOC → Markdown\*, Text | [LiteParse](https://pypi.org/project/liteparse/) + LibreOffice + Apache POI |

\* **`liteparse` Markdown includes tables for spreadsheets only.**

Spreadsheets (XLSX, XLS) are read cell by cell with Apache POI rather than through LiteParse, so every
column is preserved and the grid becomes a real Markdown table. Sheet names become `##` headings, and
narrative text in a single column stays prose rather than being folded into the table beside it.

For PDF, DOCX, PPTX and DOC the Markdown output is per-page text with a `## Page N` heading and a
`---` separator: headings survive, table structure does not, and a table arrives as space-padded
columns. That is a limitation of the parser, not a bug here. LiteParse 2.0.4 exposes no table model at
all: a `ParsedPage` carries only `text` and positioned `text_items`, and `output_format` accepts just
`json` or `text`. Verified by measurement rather than inferred, across PDF and DOCX fixtures the
Markdown output contains zero pipe rows.

Use `convert2md` when Markdown **tables** are the point for PDFs. On the same PDF fixtures its
separator-row count matched the source table count exactly, with every column and value preserved. The
trade is speed: roughly 20 s per two-page PDF against 0.3 to 0.7 s for `liteparse`, so pick per corpus
rather than globally.

## After cloning

Binary probe sample files (`.pdf`, `.xlsx`, `.mp3`, `.mp4`, `.heic`) are git-ignored. Regenerate them after a fresh clone with:

```bash
make generate-samples   # requires pandoc, ffmpeg, heif-enc, python3+openpyxl, and `say` (macOS) or `espeak-ng` for the whisper sample
```

## Quick start

### AIO container (all 16 engines)

```bash
git clone https://github.com/aborroy/alfresco-transform-extras
cd alfresco-transform-extras
make build
docker compose up
```

The container starts on port **8090**. Verify:

```bash
make health   # {"status":"UP"}
make test     # lists all transformer names (16 engines, some with multiple transformers)
```

> **Browser test page (`/`):** the form at `http://localhost:8090/` (which POSTs to `/test`) is disabled by default in `alfresco-transform-core` 5.4.1 and returns `403`. `compose.yaml` enables it for local development by setting `TEST_ENDPOINT_ENABLED=true`. **Do not enable in production** — see the [Hyland 5.2.0 → 5.4.1 upgrade guide](https://connect.hyland.com/t5/alfresco-blog/upgrade-guide-alfresco-transform-core-5-2-0-to-5-4-1/ba-p/498369).

### Single engine

```bash
make build-xml
make run-engine ENGINE=xml PORT=8090
```

## Deployment — Community Edition

ACS Community communicates with T-Engines over **direct HTTP** using `localTransform.*` properties. No ActiveMQ or Shared File Store is required.

### AIO (all 16 engines in one container)

Add to `alfresco-global.properties` (or as `-D` flags in `JAVA_OPTS`):

```properties
localTransform.core-aio.url=http://transform-extras-aio:8090/
```

### Individual engines

Run only the engines you need alongside the official AIO:

```properties
# Keep the official AIO for built-in transforms
localTransform.core-aio.url=http://transform-core-aio:8090/

# Register additional engines individually
localTransform.ocr.url=http://transform-ocr:8090/
localTransform.xml.url=http://transform-xml:8090/
localTransform.md2doc.url=http://transform-md2doc:8090/
localTransform.pii.url=http://transform-pii:8090/
```

### Docker Compose example

Add to your ACS Compose file:

```yaml
transform-ocr:
  image: angelborroy/alf-tengine-ocr:latest
  environment:
    JAVA_OPTS: "-Xms256m -Xmx512m"

alfresco:
  environment:
    JAVA_OPTS: >-
      -DlocalTransform.core-aio.url=http://transform-core-aio:8090/
      -DlocalTransform.ocr.url=http://transform-ocr:8090/
```

> **ACS 7.4+:** Slow transforms (OCR on dense PDFs, Whisper transcription) may exceed the default
> HTTP timeout. Set `httpclient.config.transform.socketTimeout=500000` in `alfresco-global.properties`.

## Deployment — Enterprise Edition

ACS Enterprise routes transforms **asynchronously** through a Transform Router backed by ActiveMQ and a Shared File Store.

### Engine container

Each engine container requires connectivity to ActiveMQ and Shared File Store:

```yaml
transform-ocr:
  image: angelborroy/alf-tengine-ocr:latest
  environment:
    ACTIVEMQ_URL: "nio://activemq:61616"
    FILE_STORE_URL: "http://shared-file-store:8099/alfresco/api/-default-/private/sfs/versions/1/file"
    JAVA_OPTS: "-Xms256m -Xmx512m"
```

### Transform Router configuration

Register each engine with the router using a URL + queue name pair:

```yaml
transform-router:
  environment:
    TRANSFORMER_URL_OCR: "http://transform-ocr:8090"
    TRANSFORMER_QUEUE_OCR: "ocr-engine-queue"
    TRANSFORMER_URL_XML: "http://transform-xml:8090"
    TRANSFORMER_QUEUE_XML: "xml-engine-queue"
    TRANSFORMER_URL_MD2DOC: "http://transform-md2doc:8090"
    TRANSFORMER_QUEUE_MD2DOC: "md2doc-engine-queue"
    # ... one pair per deployed engine
```

### Queue names

| Engine | `TRANSFORMER_QUEUE_*` value |
|--------|----------------------------|
| `convert2md` | `convert2md-engine-queue` |
| `excel` | `excel-engine-queue` |
| `heic` | `heic-engine-queue` |
| `html2md` | `html2md-engine-queue` |
| `markdown` | `markdown-engine-queue` |
| `md2doc` | `md2doc-engine-queue` |
| `md2html` | `md2html-engine-queue` |
| `msg` | `msg-engine-queue` |
| `ocr` | `ocr-engine-queue` |
| `pdf2docx` | `pdf2docx-engine-queue` |
| `pii` | `pii-engine-queue` |
| `videothumb` | `videothumb-engine-queue` |
| `whisper` | `whisper-engine-queue` |
| `xml` | `xml-engine-queue` |
| `ai` | `ai-engine-queue` |
| `liteparse` | `liteparse-engine-queue` |

## AI Engine

The `ai` engine uses a **local LLM** via [Docker Model Runner](https://www.docker.com/products/model-runner/) to extract metadata from documents. A single transformer (`ai-metadata`) accepts an optional `aiFields` parameter (comma-separated) to select which properties to extract. When `aiFields` is omitted, all four are extracted.

| `aiFields` value | Metadata property | Description |
|-----------------|-------------------|-------------|
| `title` | `cm:title` | Short generated document title |
| `description` | `cm:description` | Concise paragraph summary of document content |
| `tags` | `cm:taggable` | Comma-separated list of 5-10 topic keywords |
| `language` | `cm:locale` | ISO 639-1 two-letter language code (e.g. `en`, `es`) |

**Supported input formats:** `text/plain`, `application/pdf`

### Requirements

**Docker Model Runner** must be running locally. In Docker Desktop ≥ 4.40 it ships built-in; enable it in **Settings → Features in development → Docker Model Runner**.

Pull the default model once:

```bash
docker model pull ai/bartowski/Llama-3.2-3B-Instruct-GGUF
```

### Configuration

The AI engine connects to Docker Model Runner via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `TRANSFORM_AI_ENDPOINT` | `http://model-runner.docker.internal/engines/llama.cpp/v1` | OpenAI-compatible API endpoint |
| `TRANSFORM_AI_MODEL` | `ai/bartowski/Llama-3.2-3B-Instruct-GGUF` | Model identifier |

Override in `compose.yaml` or Kubernetes manifests:

```yaml
transform-ai:
  image: angelborroy/alf-tengine-ai:latest
  environment:
    TRANSFORM_AI_ENDPOINT: "http://model-runner.docker.internal/engines/llama.cpp/v1"
    TRANSFORM_AI_MODEL: "ai/bartowski/Llama-3.2-3B-Instruct-GGUF"
```

For Docker containers to reach Docker Model Runner, add the host gateway:

```bash
docker run --add-host=model-runner.docker.internal:host-gateway angelborroy/alf-tengine-ai:latest
```

In `compose.yaml`:

```yaml
transform-ai:
  extra_hosts:
    - "model-runner.docker.internal:host-gateway"
```

### Performance notes

- First request takes 10-15s (model warm-up); subsequent requests ~2-5s
- Memory: 4GB minimum for Llama-3.2-3B (scales with model size)
- Text is truncated to 4000 characters before being sent to the LLM
- The AI engine works alongside the official AIO — no conflicts

## Transform options

These options can be passed as request parameters to `/transform` or configured via Alfresco's transform pipeline rules.

| Engine | Option | Type | Default | Description |
|--------|--------|------|---------|-------------|
| `ocr` | `language` | string | `eng` | Tesseract language code(s), e.g. `spa`, `fra+eng` |
| `pii` | `entities` | string | `PERSON,PHONE_NUMBER,EMAIL_ADDRESS` | Comma-separated Presidio entity types to detect |
| `pii` | `scoreThreshold` | double | `0.5` | Minimum Presidio confidence score (0.0–1.0) |
| `pii` | `label` | string | `[REDACTED]` | Replacement text for redacted content |
| `md2doc` | `tocEnabled` | boolean | `false` | Generate a table of contents |
| `md2doc` | `tocDepth` | integer | `3` | Maximum heading depth in the TOC |
| `whisper` | `model` | string | `base` | Whisper model: `tiny`, `base`, `small`, `medium`, `large` |
| `videothumb` | `timeOffset` | integer | `1` | Seconds into the video for the thumbnail frame |
| `convert2md` | `language` | string | `english` | Document language hint for Docling OCR |
| `convert2md` | `image` | string | `placeholder` | Image handling mode for Docling |

## Scaling

**Horizontal scaling** is supported for all engines. In Enterprise mode the Transform Router distributes requests across all replicas of the same engine automatically (multiple containers reading from the same ActiveMQ queue). In Community mode use a reverse proxy (nginx, Traefik) in front of multiple instances.

**Memory requirements** vary significantly:

| Engine | Minimum | Notes |
|--------|---------|-------|
| `convert2md` | 4 GB | Docling loads a PyTorch model |
| `whisper` (large) | 8 GB | Model size scales with quality |
| `pii` | 2 GB | spaCy `en_core_web_lg` model |
| `liteparse` | 1 GB | LibreOffice for Office formats |
| All others | 512 MB | Java + lightweight external tool |

**Timeout**: OCR on dense PDFs and Whisper transcription of long files can take minutes. Set a generous socket timeout in ACS:

```properties
httpclient.config.transform.socketTimeout=500000
```

**File size limits** default to 50–100 MB per engine. Override with environment variables on the engine container:

```yaml
environment:
  JAVA_OPTS: >-
    -Dspring.servlet.multipart.max-file-size=200MB
    -Dspring.servlet.multipart.max-request-size=200MB
```

## Security

- All engine containers run as a **non-root user** (`alfte`, UID 33050).
- Engines have no external network dependencies at runtime — no outbound calls after startup. The `pii` engine loads Presidio models from the image; `convert2md` loads Docling models from the image.
- **Do not expose port 8090 on the public internet.** Engines are internal services intended to be accessed only by ACS or the Transform Router.
- For network isolation in production, place engines on a dedicated internal Docker network or use Kubernetes `NetworkPolicy` to restrict ingress to the Transform Router and block all egress.
- The `pii` engine processes document content containing sensitive data. Ensure the container host, Docker volumes, and any temp file paths are secured appropriately.

## Version pinning

External tool versions are controlled via Docker build ARGs with stable defaults:

| ARG | Default | Engines |
|-----|---------|---------|
| `PANDOC_VERSION` | `3.9.0.2` | md2doc, markdown, html2md, md2html, msg |
| `OCRMYPDF_VERSION` | `17.5.0` | ocr |
| `TESSERACT_LANGUAGES` | `eng` | ocr |
| `WHISPER_VERSION` | `20250625` | whisper |
| `PDF2DOCX_VERSION` | `0.5.13` | pdf2docx |
| `LITEPARSE_VERSION` | `2.0.4` | liteparse |

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
make build-engines          All 16 individual images

# Multi-platform linux/amd64 + linux/arm64 — pushes to registry with SBOM + provenance
make buildx                 AIO image
make buildx-<name>          Individual engine (e.g. make buildx-ocr)
make buildx-engines         All 16 individual images
make buildx-all             AIO + all 16 engines

# Run
make run                    docker compose up (AIO)
make run-engine ENGINE=<name> PORT=<port>

# Verify
make test                   List transformer names on localhost:8090
make health                 Health check on localhost:8090

# Integration tests (requires Docker, images must be built first)
make integration-test ENGINE=<name>
make integration-test-all

# Push (single-platform)
make push / push-engines / push-all

# Clean
make clean-all
```

Override variables on any target:

```bash
make buildx PLATFORMS=linux/amd64,linux/arm64 VERSION=1.1.0
make buildx-ocr TESSERACT_LANGUAGES=eng,spa,fra OCRMYPDF_VERSION=16
make buildx BUILDER=my-builder
```

## Releasing

Image tags are independent of the Maven version: `VERSION` in the Makefile drives the tag and defaults
to `latest`, so a release needs no POM change and no Dockerfile edit.

```bash
# Multi-platform, with SBOM and provenance, pushed to the registry
make buildx-engines VERSION=1.1.0
make buildx VERSION=1.1.0          # the AIO image

# Then tag the source that produced them
git tag -a v1.1.0 -m "1.1.0"
git push origin v1.1.0
```

Publish a version tag rather than relying on `latest`. A consumer that pins `latest` cannot reproduce a
build, and `latest` moving under them changes extraction behaviour silently. Before 1.1.0 only
`convert2md` published a version tag (`1.0.0`) and every other engine published `latest` only.

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

| ACS version | Community | Enterprise |
|-------------|-----------|------------|
| 7.x | `localTransform.*` properties | Transform Router + ActiveMQ |
| 23.x | `localTransform.*` properties | Transform Router + ActiveMQ |
| 25.x | `localTransform.*` properties | Transform Router + ActiveMQ |
| 26.x | `localTransform.*` properties | Transform Router + ActiveMQ |

Minimum Java version: **17** (required by ACS 25.2+).

ACS 26.2 was verified only for the direct-HTTP path: an engine was started standalone and driven over
`POST /transform` and `GET /transform/config`, which is how a non-ACS consumer uses it. Registration
through `localTransform.*` against an ACS 26.x repository, and routing through an Enterprise Transform
Router at that version, have not been exercised. Treat the 26.x row as "the engine protocol works",
not as a certified ACS integration.

Nothing in an engine is ACS-specific: they implement the `alfresco-transform-core` HTTP contract, so
any client that can POST a multipart form to `/transform` can use them, with no repository involved.

## Licence

The Java sources in this repository are licensed under the Apache License, Version 2.0. See
[LICENSE](LICENSE).

The published container images additionally bundle third-party software that keeps its own licence,
and some of it is copyleft. [NOTICE](NOTICE) lists the components per engine. The points worth knowing
before redistributing an image:

- `convert2md` and `liteparse` bundle only permissive components (MIT, Apache-2.0, MPL-2.0,
  BSD-3-Clause).
- `html2md`, `md2html`, `markdown`, `md2doc` and `msg` bundle Pandoc, which is GPL-2.0-or-later.
- `ocr` pulls in Ghostscript (AGPL-3.0).
- `pii` and `pdf2docx` bundle PyMuPDF, which is AGPL-3.0 or an Artifex commercial licence. The AGPL's
  network-use clause is triggered by the deployment shape these engines are built for, a
  network-reachable service.

This is attribution guidance, not legal advice.
