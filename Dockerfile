# Multi-stage AIO TEngine build
# Customizable build args (override with --build-arg):
#   PANDOC_VERSION     — Pandoc GitHub release tag      (default: 3.9.0.2)
#   OCRMYPDF_VERSION   — ocrmypdf PyPI version           (default: 17.5.0)
#   TESSERACT_LANGUAGES — comma-separated language codes (default: eng)
#   WHISPER_VERSION    — openai-whisper PyPI version     (default: 20250625)
#   PDF2DOCX_VERSION   — pdf2docx PyPI version           (default: 0.5.13)
#   JAVA_BASE          — base JRE image                  (default: eclipse-temurin:21-jre)

ARG PANDOC_VERSION=3.9.0.2
ARG OCRMYPDF_VERSION=17.5.0
ARG TESSERACT_LANGUAGES=eng
ARG WHISPER_VERSION=20250625
ARG PDF2DOCX_VERSION=0.5.13
ARG JAVA_BASE=eclipse-temurin:21-jre

# ── Stage 1: Maven build ──────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy all pom.xml files first — Docker caches this layer separately from sources.
# Dependency downloads are only re-triggered when a pom changes, not on source edits.
COPY pom.xml .
COPY engines/aio/pom.xml        engines/aio/pom.xml
COPY engines/convert2md/pom.xml engines/convert2md/pom.xml
COPY engines/excel/pom.xml      engines/excel/pom.xml
COPY engines/heic/pom.xml       engines/heic/pom.xml
COPY engines/html2md/pom.xml    engines/html2md/pom.xml
COPY engines/markdown/pom.xml   engines/markdown/pom.xml
COPY engines/md2doc/pom.xml     engines/md2doc/pom.xml
COPY engines/md2html/pom.xml    engines/md2html/pom.xml
COPY engines/msg/pom.xml        engines/msg/pom.xml
COPY engines/ocr/pom.xml        engines/ocr/pom.xml
COPY engines/pdf2docx/pom.xml   engines/pdf2docx/pom.xml
COPY engines/pii/pom.xml        engines/pii/pom.xml
COPY engines/videothumb/pom.xml engines/videothumb/pom.xml
COPY engines/whisper/pom.xml    engines/whisper/pom.xml
COPY engines/xml/pom.xml        engines/xml/pom.xml

# Download all dependencies (cached as a separate layer; only re-runs when a pom changes)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline --batch-mode -q

# Now copy sources and build
COPY engines/ engines/
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests --batch-mode

# ── Stage 2: Python dependencies ─────────────────────────────────────────────
FROM python:3.11-slim AS python-deps
ARG OCRMYPDF_VERSION
ARG WHISPER_VERSION
ARG PDF2DOCX_VERSION

# libgl1/libxcb1/libglib2.0-0 are required by opencv (a transitive dep of
# rapidocr) so that "import cv2" works when we pre-fetch models below.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 libxcb1 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN --mount=type=cache,target=/root/.cache/pip \
    pip install \
    "ocrmypdf==${OCRMYPDF_VERSION}" \
    "openai-whisper==${WHISPER_VERSION}" \
    "pdf2docx==${PDF2DOCX_VERSION}" \
    "presidio-analyzer==2.2.362" \
    "presidio-anonymizer==2.2.362" \
    "spacy==3.8.14" \
    "pymupdf==1.27.2.3" \
    "docling==2.96.0" \
    "onnxruntime==1.20.1"

RUN python -m spacy download en_core_web_lg

# Pre-fetch docling/RapidOCR models so the runtime (non-root, read-only
# site-packages) doesn't try to download them on first request. RapidOCR's
# default config points to *_mobile.onnx weights that aren't bundled, and
# it writes them under .../site-packages/rapidocr/models/ — populate that
# dir now while we have root. Docling's layout/table/figure-classifier
# models come from HuggingFace Hub and are loaded lazily on the first
# convert() call, so we run a real conversion against a tiny sample PDF
# to force every model snapshot to be cached under HF_HOME.
ENV HF_HOME=/usr/local/share/huggingface
COPY engines/convert2md/src/main/resources/sample.pdf /tmp/prefetch-sample.pdf
RUN mkdir -p "$HF_HOME" \
    && python -c "from rapidocr import RapidOCR; RapidOCR()" \
    && python -c "from docling.document_converter import DocumentConverter; DocumentConverter().convert('/tmp/prefetch-sample.pdf').document.export_to_markdown()" \
    && rm -f /tmp/prefetch-sample.pdf

# ── Stage 3: Runtime AIO ──────────────────────────────────────────────────────
FROM ${JAVA_BASE}
ARG PANDOC_VERSION
ARG TESSERACT_LANGUAGES

# System packages + optional Tesseract language packs in a single RUN layer.
# We do NOT install apt's python3 here: the next stage copies a Python 3.11
# tree from python:3.11-slim, and apt's python3 (currently 3.14 on the
# eclipse-temurin base) is ABI-incompatible with those site-packages.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        tesseract-ocr \
        libheif-examples \
        ffmpeg \
        poppler-utils \
        libgl1 \
        libmagic1 \
        texlive-latex-base \
        texlive-xetex \
        texlive-fonts-recommended \
        lmodern \
    && for lang in $(echo "${TESSERACT_LANGUAGES}" | tr ',' ' '); do \
         [ "$lang" != "eng" ] && apt-get install -y --no-install-recommends "tesseract-ocr-${lang}" || true; \
       done \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Pandoc — version-pinned via ARG, architecture-aware
RUN ARCH=$(dpkg --print-architecture) && \
    if [ "$ARCH" = "amd64" ]; then PANDOC_ARCH="amd64"; else PANDOC_ARCH="arm64"; fi && \
    curl -fsSL "https://github.com/jgm/pandoc/releases/download/${PANDOC_VERSION}/pandoc-${PANDOC_VERSION}-linux-${PANDOC_ARCH}.tar.gz" \
    | tar -xz --strip-components=2 -C /usr/local/bin "pandoc-${PANDOC_VERSION}/bin/pandoc"

# Python environment from stage 2 — the engine code calls "python3" via PATH,
# so we symlink it to the 3.11 binary that matches the site-packages we copy.
COPY --from=python-deps /usr/local/lib/python3.11 /usr/local/lib/python3.11
COPY --from=python-deps /usr/local/lib/libpython3.11.so.1.0 /usr/local/lib/libpython3.11.so.1.0
COPY --from=python-deps /usr/local/bin/whisper /usr/local/bin/whisper
COPY --from=python-deps /usr/local/bin/ocrmypdf /usr/local/bin/ocrmypdf
COPY --from=python-deps /usr/local/bin/python3.11 /usr/local/bin/python3.11
RUN ln -sf /usr/local/bin/python3.11 /usr/local/bin/python3 \
    && ln -sf /usr/local/bin/python3.11 /usr/local/bin/python

# Pre-fetched docling/HuggingFace model cache from stage 2. Chmod so the
# non-root alfte user can write HF's lock/refs files (HF still touches
# these even when the snapshot already exists locally).
COPY --from=python-deps /usr/local/share/huggingface /usr/local/share/huggingface
RUN chmod -R a+rwX /usr/local/share/huggingface
ENV HF_HOME=/usr/local/share/huggingface

# AIO fat JAR (produced by engines/aio module, named alf-tengine-aio.jar)
COPY --from=build /build/engines/aio/target/alf-tengine-aio.jar /usr/bin/alf-tengine-aio.jar

# Presidio scripts
COPY docker/presidio/ /opt/presidio/

# Entrypoint
COPY docker/scripts/docker-entrypoint.sh /docker/scripts/docker-entrypoint.sh
RUN chmod +x /docker/scripts/docker-entrypoint.sh

# Non-root user
RUN groupadd -g 1001 alfresco && \
    useradd -u 33050 -g alfresco -m -d /home/alfte alfte

USER alfte
EXPOSE 8090

ENTRYPOINT ["/docker/scripts/docker-entrypoint.sh"]
