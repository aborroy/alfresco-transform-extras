REGISTRY  ?= angelborroy
VERSION   ?= latest
PLATFORMS ?= linux/amd64,linux/arm64
BUILDER   ?= builder

# AIO image
AIO_IMAGE := $(REGISTRY)/alfresco-transform-extras-aio:$(VERSION)

# Tool version ARGs (override on the command line, e.g. make build PANDOC_VERSION=3.5)
PANDOC_VERSION       ?= 3.9.0.2
OCRMYPDF_VERSION     ?= 17.5.0
TESSERACT_LANGUAGES  ?= eng
WHISPER_VERSION      ?= 20250625
PDF2DOCX_VERSION     ?= 0.5.13

# ── Helpers ──────────────────────────────────────────────────────────────────

# Single-platform build (loads into local Docker daemon)
# Usage: $(call build-engine,<name>[,extra --build-arg flags])
define build-engine
	docker build $(2) \
		--target runtime \
		-t $(REGISTRY)/alf-tengine-$(1):$(VERSION) \
		engines/$(1)/
endef

# Multi-platform build with SBOM + provenance attestation (pushes to registry)
# Usage: $(call buildx-engine,<name>[,extra --build-arg flags])
define buildx-engine
	docker buildx build $(2) \
		--builder $(BUILDER) \
		--target runtime \
		--platform $(PLATFORMS) \
		--sbom=true \
		--provenance=mode=max \
		--push \
		-t $(REGISTRY)/alf-tengine-$(1):$(VERSION) \
		engines/$(1)/
endef

# ── Maven ─────────────────────────────────────────────────────────────────────

.PHONY: package
package:
	mvn clean package -DskipTests --batch-mode

# ── AIO image ─────────────────────────────────────────────────────────────────

.PHONY: build build-full buildx buildx-full
build: package
	docker build \
		--build-arg PANDOC_VERSION=$(PANDOC_VERSION) \
		--build-arg OCRMYPDF_VERSION=$(OCRMYPDF_VERSION) \
		--build-arg TESSERACT_LANGUAGES=$(TESSERACT_LANGUAGES) \
		--build-arg WHISPER_VERSION=$(WHISPER_VERSION) \
		--build-arg PDF2DOCX_VERSION=$(PDF2DOCX_VERSION) \
		-t $(AIO_IMAGE) .

build-full: package
	docker build \
		--no-cache \
		--build-arg PANDOC_VERSION=$(PANDOC_VERSION) \
		--build-arg OCRMYPDF_VERSION=$(OCRMYPDF_VERSION) \
		--build-arg TESSERACT_LANGUAGES=eng,spa,fra,deu,ita,por \
		--build-arg WHISPER_VERSION=$(WHISPER_VERSION) \
		--build-arg PDF2DOCX_VERSION=$(PDF2DOCX_VERSION) \
		-t $(AIO_IMAGE) .

# Multi-platform AIO build — pushes to registry with SBOM + provenance
buildx: package
	docker buildx build \
		--builder $(BUILDER) \
		--platform $(PLATFORMS) \
		--sbom=true \
		--provenance=mode=max \
		--push \
		--build-arg PANDOC_VERSION=$(PANDOC_VERSION) \
		--build-arg OCRMYPDF_VERSION=$(OCRMYPDF_VERSION) \
		--build-arg TESSERACT_LANGUAGES=$(TESSERACT_LANGUAGES) \
		--build-arg WHISPER_VERSION=$(WHISPER_VERSION) \
		--build-arg PDF2DOCX_VERSION=$(PDF2DOCX_VERSION) \
		-t $(AIO_IMAGE) .

buildx-full: package
	docker buildx build \
		--builder $(BUILDER) \
		--platform $(PLATFORMS) \
		--sbom=true \
		--provenance=mode=max \
		--no-cache \
		--push \
		--build-arg PANDOC_VERSION=$(PANDOC_VERSION) \
		--build-arg OCRMYPDF_VERSION=$(OCRMYPDF_VERSION) \
		--build-arg TESSERACT_LANGUAGES=eng,spa,fra,deu,ita,por \
		--build-arg WHISPER_VERSION=$(WHISPER_VERSION) \
		--build-arg PDF2DOCX_VERSION=$(PDF2DOCX_VERSION) \
		-t $(AIO_IMAGE) .

# ── Individual engine images ──────────────────────────────────────────────────

.PHONY: build-xml build-excel \
        build-md2doc build-markdown build-html2md build-md2html \
        build-ocr build-convert2md build-pdf2docx build-whisper \
        build-pii build-msg build-videothumb build-heic build-ai \
        build-engines \
        buildx-xml buildx-excel \
        buildx-md2doc buildx-markdown buildx-html2md buildx-md2html \
        buildx-ocr buildx-convert2md buildx-pdf2docx buildx-whisper \
        buildx-pii buildx-msg buildx-videothumb buildx-heic buildx-ai \
        buildx-engines buildx-all

# Java-only (no external tool ARGs)
build-xml:
	$(call build-engine,xml)

build-excel:
	$(call build-engine,excel)

# Pandoc engines
build-md2doc:
	$(call build-engine,md2doc,--build-arg PANDOC_VERSION=$(PANDOC_VERSION))

build-markdown:
	$(call build-engine,markdown,--build-arg PANDOC_VERSION=$(PANDOC_VERSION))

build-html2md:
	$(call build-engine,html2md,--build-arg PANDOC_VERSION=$(PANDOC_VERSION))

build-md2html:
	$(call build-engine,md2html,--build-arg PANDOC_VERSION=$(PANDOC_VERSION))

build-msg:
	$(call build-engine,msg,--build-arg PANDOC_VERSION=$(PANDOC_VERSION))

# Python engines
build-ocr:
	$(call build-engine,ocr,\
		--build-arg OCRMYPDF_VERSION=$(OCRMYPDF_VERSION) \
		--build-arg TESSERACT_LANGUAGES=$(TESSERACT_LANGUAGES))

build-convert2md:
	$(call build-engine,convert2md)

build-pdf2docx:
	$(call build-engine,pdf2docx,--build-arg PDF2DOCX_VERSION=$(PDF2DOCX_VERSION))

build-whisper:
	$(call build-engine,whisper,--build-arg WHISPER_VERSION=$(WHISPER_VERSION))

# pii needs project-root context (copies docker/presidio/)
build-pii:
	docker build \
		--target runtime \
		-f engines/pii/Dockerfile \
		-t $(REGISTRY)/alf-tengine-pii:$(VERSION) \
		.

# Media engines
build-videothumb:
	$(call build-engine,videothumb)

build-heic:
	$(call build-engine,heic)

build-ai:
	$(call build-engine,ai)

# Build all 15 individual engine images sequentially
build-engines: \
	build-xml build-excel \
	build-md2doc build-markdown build-html2md build-md2html build-msg \
	build-ocr build-convert2md build-pdf2docx build-whisper build-pii \
	build-videothumb build-heic build-ai

# ── Multi-platform individual engine images ───────────────────────────────────

buildx-xml:
	$(call buildx-engine,xml)

buildx-excel:
	$(call buildx-engine,excel)

buildx-md2doc:
	$(call buildx-engine,md2doc,--build-arg PANDOC_VERSION=$(PANDOC_VERSION))

buildx-markdown:
	$(call buildx-engine,markdown,--build-arg PANDOC_VERSION=$(PANDOC_VERSION))

buildx-html2md:
	$(call buildx-engine,html2md,--build-arg PANDOC_VERSION=$(PANDOC_VERSION))

buildx-md2html:
	$(call buildx-engine,md2html,--build-arg PANDOC_VERSION=$(PANDOC_VERSION))

buildx-msg:
	$(call buildx-engine,msg,--build-arg PANDOC_VERSION=$(PANDOC_VERSION))

buildx-ocr:
	$(call buildx-engine,ocr,\
		--build-arg OCRMYPDF_VERSION=$(OCRMYPDF_VERSION) \
		--build-arg TESSERACT_LANGUAGES=$(TESSERACT_LANGUAGES))

buildx-convert2md:
	$(call buildx-engine,convert2md)

buildx-pdf2docx:
	$(call buildx-engine,pdf2docx,--build-arg PDF2DOCX_VERSION=$(PDF2DOCX_VERSION))

buildx-whisper:
	$(call buildx-engine,whisper,--build-arg WHISPER_VERSION=$(WHISPER_VERSION))

# pii needs project-root context
buildx-pii:
	docker buildx build \
		--builder $(BUILDER) \
		--platform $(PLATFORMS) \
		--sbom=true \
		--provenance=mode=max \
		--push \
		-f engines/pii/Dockerfile \
		-t $(REGISTRY)/alf-tengine-pii:$(VERSION) \
		.

buildx-videothumb:
	$(call buildx-engine,videothumb)

buildx-heic:
	$(call buildx-engine,heic)

buildx-ai:
	$(call buildx-engine,ai)

# Build and push all 15 individual engine images for all platforms
buildx-engines: \
	buildx-xml buildx-excel \
	buildx-md2doc buildx-markdown buildx-html2md buildx-md2html buildx-msg \
	buildx-ocr buildx-convert2md buildx-pdf2docx buildx-whisper buildx-pii \
	buildx-videothumb buildx-heic buildx-ai

# Build and push everything (AIO + all engines) for all platforms
buildx-all: buildx buildx-engines

# ── Smoke tests (Docker test-runner stage) ────────────────────────────────────
# Builds the test-runner stage and verifies the engine starts healthy.
# Usage: make smoke-test ENGINE=xml

.PHONY: smoke-test smoke-test-all

smoke-test:
	@[ -n "$(ENGINE)" ] || (echo "Usage: make smoke-test ENGINE=<name>"; exit 1)
	@if [ "$(ENGINE)" = "pii" ]; then \
		docker build --target test-runner \
			-f engines/pii/Dockerfile \
			-t alf-tengine-pii-test .; \
	else \
		docker build --target test-runner \
			-t alf-tengine-$(ENGINE)-test \
			engines/$(ENGINE)/; \
	fi
	docker run --rm -e MANAGEMENT_HEALTH_JMS_ENABLED=false alf-tengine-$(ENGINE)-test

smoke-test-all:
	@for engine in xml excel md2doc markdown html2md md2html msg ocr convert2md pdf2docx whisper pii videothumb heic ai; do \
		echo ""; \
		echo "=== Smoke test: $$engine ==="; \
		$(MAKE) smoke-test ENGINE=$$engine || exit 1; \
	done

# ── Integration tests (Testcontainers) ───────────────────────────────────────
# Requires Docker. Engine images must be built first (make build-<name>).
# Runs @Tag("integration") JUnit tests against the actual engine container.
# Usage: make integration-test ENGINE=xml
#        make integration-test-all

.PHONY: integration-test integration-test-all

integration-test:
	@[ -n "$(ENGINE)" ] || (echo "Usage: make integration-test ENGINE=<name>"; exit 1)
	mvn test -pl engines/$(ENGINE) -Dgroups=integration -Dsurefire.excludedGroups= --batch-mode

integration-test-all:
	mvn test -Dgroups=integration -Dsurefire.excludedGroups= --batch-mode

# ── Run / test / health ───────────────────────────────────────────────────────

.PHONY: run run-engine test health

run:
	docker compose up

# Run a single engine standalone: make run-engine ENGINE=xml PORT=8091
run-engine:
	docker run --rm -p $(PORT):8090 \
		-e JAVA_OPTS="-Xms128m -Xmx512m" \
		-e MANAGEMENT_HEALTH_JMS_ENABLED=false \
		$(REGISTRY)/alf-tengine-$(ENGINE):$(VERSION)

test:
	@echo "Registered transformers on localhost:8090:"
	@curl -sf http://localhost:8090/transform/config | \
		python3 -c "import sys,json; [print(' ',t['transformerName']) for t in json.load(sys.stdin)['transformers']]"

health:
	@curl -sf http://localhost:8090/actuator/health | python3 -m json.tool

# ── Sample file generation ────────────────────────────────────────────────────
# Binary probe samples are git-ignored. Run this after a fresh clone to regenerate them.
# Requires: pandoc, ffmpeg, heif-enc, python3 with openpyxl

.PHONY: generate-samples
generate-samples:
	@echo "Generating binary probe sample files..."
	@echo "# Sample PDF" > /tmp/probe.md && \
	 pandoc /tmp/probe.md -o /tmp/probe.pdf 2>/dev/null && \
	 for engine in ocr pii convert2md pdf2docx; do \
	   cp /tmp/probe.pdf engines/$$engine/src/main/resources/sample.pdf; \
	   echo "  ✓ $$engine/sample.pdf"; \
	 done
	@python3 -c "\
import openpyxl; wb=openpyxl.Workbook(); ws=wb.active; \
ws['A1']='Title'; ws['B1']='Value'; wb.properties.title='Sample'; \
wb.save('engines/excel/src/main/resources/sample.xlsx')" && \
	 echo "  ✓ excel/sample.xlsx"
	@SPEECH="This is a sample audio file used to verify the Whisper transcription engine. The quick brown fox jumps over the lazy dog."; \
	 if command -v say >/dev/null 2>&1; then \
	   say -v Samantha -o /tmp/probe_speech.aiff "$$SPEECH" && \
	   ffmpeg -y -i /tmp/probe_speech.aiff -ac 1 -ar 16000 -b:a 64k \
	     engines/whisper/src/main/resources/sample.mp3 -loglevel error; \
	 elif command -v espeak-ng >/dev/null 2>&1; then \
	   espeak-ng -w /tmp/probe_speech.wav "$$SPEECH" && \
	   ffmpeg -y -i /tmp/probe_speech.wav -ac 1 -ar 16000 -b:a 64k \
	     engines/whisper/src/main/resources/sample.mp3 -loglevel error; \
	 else \
	   echo "  ! 'say' (macOS) or 'espeak-ng' not found — falling back to a 1s sine tone (whisper probe will start but produce empty transcripts for real requests)"; \
	   ffmpeg -y -f lavfi -i "sine=frequency=440:duration=1" \
	     engines/whisper/src/main/resources/sample.mp3 -loglevel error; \
	 fi && \
	 echo "  ✓ whisper/sample.mp3"
	@ffmpeg -y -f lavfi -i "color=black:size=320x240:duration=5:rate=25" \
	   -f lavfi -i "sine=frequency=440:duration=5" \
	   -c:v libx264 -c:a aac -shortest \
	   engines/videothumb/src/main/resources/sample.mp4 -loglevel error && \
	 echo "  ✓ videothumb/sample.mp4"
	@magick -size 64x64 xc:white /tmp/probe_white.jpg && \
	 heif-enc /tmp/probe_white.jpg \
	   -o engines/heic/src/main/resources/sample.heic && \
	 echo "  ✓ heic/sample.heic"
	@$(eval M2 := $(shell mvn help:evaluate -q -DforceStdout -Dexpression=settings.localRepository 2>/dev/null || echo $$HOME/.m2/repository)) \
	 POI_VERSION=5.5.1 && \
	 CP="$$M2/org/apache/poi/poi/$$POI_VERSION/poi-$$POI_VERSION.jar:$$M2/org/apache/poi/poi-scratchpad/$$POI_VERSION/poi-scratchpad-$$POI_VERSION.jar:$$M2/org/apache/commons/commons-collections4/4.2/commons-collections4-4.2.jar:$$M2/commons-codec/commons-codec/1.9/commons-codec-1.9.jar:$$M2/org/apache/logging/log4j/log4j-api/2.25.4/log4j-api-2.25.4.jar:$$M2/org/apache/logging/log4j/log4j-core/2.25.3/log4j-core-2.25.3.jar:$$M2/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar:$$M2/commons-io/commons-io/2.21.0/commons-io-2.21.0.jar" && \
	 cat > /tmp/GenMsg.java << 'JAVA_EOF' \
import org.apache.poi.poifs.filesystem.POIFSFileSystem; \
import java.io.*; import java.nio.charset.StandardCharsets; \
public class GenMsg { public static void main(String[] a) throws Exception { \
  try (POIFSFileSystem fs = new POIFSFileSystem()) { \
    fs.createDocument(new ByteArrayInputStream("Sample Email".getBytes(StandardCharsets.UTF_8)), "__substg1.0_0037001E"); \
    fs.createDocument(new ByteArrayInputStream("sender@example.com".getBytes(StandardCharsets.UTF_8)), "__substg1.0_0C1F001E"); \
    fs.createDocument(new ByteArrayInputStream("recipient@example.com".getBytes(StandardCharsets.UTF_8)), "__substg1.0_0E04001E"); \
    fs.createDocument(new ByteArrayInputStream("This is the body of the sample email.".getBytes(StandardCharsets.UTF_8)), "__substg1.0_1000001E"); \
    try (FileOutputStream fos = new FileOutputStream(a[0])) { fs.writeFilesystem(fos); } \
  } \
} } \
JAVA_EOF \
	 && javac -proc:none -cp "$$CP" /tmp/GenMsg.java -d /tmp 2>/dev/null \
	 && java -cp "/tmp:$$CP" -Dlog4j2.disable.jmx=true GenMsg \
	      engines/msg/src/main/resources/sample.msg 2>/dev/null \
	 && echo "  ✓ msg/sample.msg"
	@echo "Done. All binary sample files regenerated."

# ── Push ──────────────────────────────────────────────────────────────────────

.PHONY: push push-engines push-all

push:
	docker push $(AIO_IMAGE)

push-engines:
	@for engine in xml excel md2doc markdown html2md md2html msg ocr convert2md pdf2docx whisper pii videothumb heic ai; do \
		echo "Pushing $(REGISTRY)/alf-tengine-$$engine:$(VERSION)"; \
		docker push $(REGISTRY)/alf-tengine-$$engine:$(VERSION); \
	done

push-all: push push-engines

# ── Clean ─────────────────────────────────────────────────────────────────────

.PHONY: clean clean-engines clean-all

clean:
	mvn clean
	docker rmi $(AIO_IMAGE) 2>/dev/null || true

clean-engines:
	@for engine in xml excel md2doc markdown html2md md2html msg ocr convert2md pdf2docx whisper pii videothumb heic ai; do \
		docker rmi $(REGISTRY)/alf-tengine-$$engine:$(VERSION) 2>/dev/null || true; \
	done

clean-all: clean clean-engines
