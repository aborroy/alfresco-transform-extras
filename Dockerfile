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

RUN --mount=type=cache,target=/root/.cache/pip \
    pip install \
    "ocrmypdf==${OCRMYPDF_VERSION}" \
    "openai-whisper==${WHISPER_VERSION}" \
    "pdf2docx==${PDF2DOCX_VERSION}" \
    "presidio-analyzer==2.2.362" \
    "presidio-anonymizer==2.2.362" \
    "spacy==3.8.14" \
    "pymupdf==1.27.2.3" \
    "docling==2.96.0"

RUN python -m spacy download en_core_web_lg

# ── Stage 3: Runtime AIO ──────────────────────────────────────────────────────
FROM ${JAVA_BASE}
ARG PANDOC_VERSION
ARG TESSERACT_LANGUAGES

# System packages + optional Tesseract language packs in a single RUN layer
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        tesseract-ocr \
        libheif-examples \
        ffmpeg \
        poppler-utils \
        libgl1 \
        libmagic1 \
        python3 \
        python3-pip \
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

# Python environment from stage 2
COPY --from=python-deps /usr/local/lib/python3.11 /usr/local/lib/python3.11
COPY --from=python-deps /usr/local/lib/libpython3.11.so.1.0 /usr/local/lib/libpython3.11.so.1.0
COPY --from=python-deps /usr/local/bin/whisper /usr/local/bin/whisper
COPY --from=python-deps /usr/local/bin/ocrmypdf /usr/local/bin/ocrmypdf
COPY --from=python-deps /usr/local/bin/python3.11 /usr/local/bin/python3.11

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
