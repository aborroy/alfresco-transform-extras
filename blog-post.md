# 15 Extra Transform Engines for Alfresco: OCR, AI, Whisper, PII Redaction and More

A user uploads a scanned contract. Nobody can search it. A colleague adds a meeting recording — no transcript appears. An iPhone photo lands in the repository as `.heic` and Share renders nothing. These are real gaps in Alfresco's default transform pipeline, and they come up often.

Alfresco Transform Services convert documents from one format to another: LibreOffice turns DOCX into PDF, ImageMagick generates image thumbnails, Tika extracts metadata from Office files. The official `alfresco-transform-core` AIO image covers those bases well. What it does not cover is OCR, speech-to-text, PII redaction, AI-generated metadata, video thumbnails, HEIC images, or the growing Markdown ecosystem.

`alfresco-transform-extras` is an open-source Maven multi-module project that fills this gap by adding 15 additional Transform Engines. Every engine follows the same standard t-engine contract, so from Alfresco's perspective they are indistinguishable from the official ones. No patches, no forks, no core modifications.

This post covers what each engine does, how to deploy them whether you run Community or Enterprise edition, and how to write a new engine from scratch.

---

## The 15 Engines at a Glance

A t-engine is a microservice that receives a source file and optional transform options over HTTP, applies a transformation, and returns the result. Alfresco sends the request; the engine does the work. The two sides only need to agree on MIME types and the `/transform` endpoint.

The 15 engines fall into six groups:

| Group | Engines | Key Technology |
|---|---|---|
| Metadata extraction | `xml`, `excel` | Java DOM, Apache POI |
| Markdown ecosystem | `html2md`, `md2html`, `markdown`, `md2doc` | Pandoc + XeLaTeX |
| Email | `msg` | Apache POI + Pandoc |
| PDF tools | `ocr`, `pii`, `pdf2docx`, `convert2md` | Tesseract, Presidio, pdf2docx, Docling |
| Rich media | `videothumb`, `heic`, `whisper` | ffmpeg, libheif, OpenAI Whisper (local) |
| AI / LLM | `ai` | Docker Model Runner (local LLM) |

**Metadata extraction**: `xml` reads Dublin Core and custom element values from XML files; `excel` reads workbook properties and custom document properties from XLS and XLSX files. Both write directly into Alfresco content model properties.

**Markdown ecosystem**: four engines covering every direction — HTML to Markdown, Markdown to HTML, Markdown to PDF (with full LaTeX rendering), and Markdown to DOCX. Useful for documentation-heavy repositories or content published in multiple formats.

**Email**: `msg` converts Outlook MSG and EML files to PDF, preserving headers and body, so email archives become searchable renditions.

**PDF tools**: `ocr` adds a hidden text layer to scanned PDFs using Tesseract, making them full-text searchable without altering the visual content. `pii` detects personally identifiable information using Microsoft Presidio, redacts it with black boxes, and writes the detected PII categories back as Alfresco metadata. `pdf2docx` converts PDFs to editable DOCX files. `convert2md` uses the Docling ML library to extract structured Markdown from PDFs, preserving tables and headings.

**Rich media**: `videothumb` extracts a JPEG thumbnail from any video format using ffmpeg. `heic` converts HEIC/HEIF images — the default format on iPhones — to JPEG or PNG. `whisper` transcribes audio and video to plain text using the OpenAI Whisper model running entirely locally.

**AI**: the `ai` engine sends document content to a local large language model via Docker Model Runner and writes AI-generated metadata (title, description, tags, language) back to Alfresco content model properties. No external API call, no API key.

---

## Deployment

### AIO vs Individual Containers

The project ships two deployment modes.

**AIO (All-In-One)** packages all 15 engines into a single Docker image. It is the simplest way to get started and covers every transform capability from one container:

```yaml
services:
  tengine-aio:
    image: angelborroy/alfresco-transform-extras-aio:latest
    ports:
      - "8090:8090"
    environment:
      JAVA_OPTS: "-Xms256m -Xmx2g"
      MANAGEMENT_HEALTH_JMS_ENABLED: "false"
      TEST_ENDPOINT_ENABLED: "true"
      TRANSFORM_AI_ENDPOINT: "http://model-runner.docker.internal/engines/llama.cpp/v1"
      TRANSFORM_AI_MODEL: "ai/smollm2"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8090/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
```

The trade-off: the image is large (several GB, including ML models pre-fetched at build time), and restarting the container for one engine upgrade restarts all of them.

**Individual containers** run each engine as a separate service. This is the production pattern: you can scale OCR horizontally without affecting Whisper, upgrade one engine without touching others, and avoid paying the memory cost for engines you do not use.

```yaml
services:
  transform-ocr:
    image: angelborroy/alf-tengine-ocr:latest
    ports:
      - "8091:8090"

  transform-whisper:
    image: angelborroy/alf-tengine-whisper:latest
    ports:
      - "8092:8090"
    deploy:
      resources:
        limits:
          memory: 4g
```

### Wiring into Alfresco Community Edition

Community Edition talks directly to transform engines via HTTP — no Transform Router, no message queue. Register each engine in `alfresco-global.properties` with a `localTransform.<name>.url` property. The key segment (e.g., `ocr`) must match the engine name declared in the engine's config:

```properties
# Register individual engines
localTransform.ocr.url=http://transform-ocr:8091/
localTransform.whisper.url=http://transform-whisper:8092/
localTransform.pii.url=http://transform-pii:8093/
localTransform.videothumb.url=http://transform-videothumb:8094/

# Or register the AIO image with a single property
localTransform.extras.url=http://tengine-aio:8090/
```

When using the AIO image, the single `localTransform.extras.url` property is enough — all 15 engines are served from the same container.

### Wiring into Alfresco Enterprise Edition

Enterprise uses the Transform Router and ActiveMQ. Engines self-register by advertising their capabilities when they start, and the router discovers them automatically. Point each engine at your ActiveMQ broker and Shared File Store:

```yaml
environment:
  ACTIVEMQ_URL: "failover:(nio://activemq:61616)?timeout=3000"
  FILE_STORE_URL: "http://shared-file-store:8099/alfresco/api/-default-/private/sfs/versions/1/file"
  TRANSFORMER_URL_OCR: "http://transform-ocr:8090/"
  TRANSFORMER_URL_WHISPER: "http://transform-whisper:8090/"
```

No manual route configuration is needed beyond these environment variables. Consult Hyland's Transform Router documentation for TLS and authentication settings in production.

### Health Checks and Testing

Every engine exposes a Spring Boot Actuator health endpoint. The probe performs a real transform on a bundled sample file at startup — a missing system dependency (e.g., `ocrmypdf` not installed in the image) causes an immediate `DOWN` status rather than a silent failure at transform time.

```bash
curl http://localhost:8091/actuator/health
# {"status":"UP"}
```

The project includes `smoke-test.sh`, which runs 40+ transforms across all engines and validates HTTP 200 responses with non-empty bodies. Run it after `docker compose up` to confirm all engines are working end-to-end.

### Resource Sizing

| Engine | CPU | Memory | Notes |
|---|---|---|---|
| `xml`, `excel`, `markdown` variants | 1 core | 512 MB | Lightweight |
| `ocr` | 2 cores | 1 GB | Scales horizontally well |
| `pii` | 2 cores | 2 GB min | Presidio NLP model |
| `pdf2docx`, `videothumb`, `heic` | 1 core | 1 GB | Moderate |
| `whisper` | 4 cores | 4 GB | GPU optional via CUDA image |
| `convert2md` | 2 cores | 4 GB | Docling ML model loaded at startup |
| `ai` | — | 8+ GB | Depends on Docker Model Runner model |

---

## Inside One Engine: OCR from End to End

The `ocr` engine is a clean reference case — one input type (PDF), one output type (searchable PDF), one external tool call. Tracing a single transform request shows exactly how the framework fits together.

When Alfresco requests an OCR transform, it POSTs the source PDF to `http://transform-ocr:8090/transform` with `targetMediaType=application/pdf` and optionally a `language` parameter.

The framework routes the request to `OcrTransformer` because its declared source/target MIME pair matches. The `transform()` method writes the incoming bytes to a temp file, calls `OcrService`, which runs `ocrmypdf` via `ProcessBuilder`, then streams the output bytes back:

```java
@Override
public void transform(String sourceMimetype, InputStream inputStream,
                      String targetMimetype, OutputStream outputStream,
                      Map<String, String> transformOptions,
                      TransformManager transformManager) throws Exception {

    String language = transformOptions.getOrDefault("language", "eng");

    Path tempDir = Files.createTempDirectory("ocr-transform-");
    File inputFile = tempDir.resolve("input.pdf").toFile();

    try {
        Files.copy(inputStream, inputFile.toPath());
        File outputFile = ocrService.ocr(inputFile, language);
        Files.copy(outputFile.toPath(), outputStream);
    } finally {
        // clean up temp directory
    }
}
```

`OcrEngine` handles registration. It tells the framework the engine name, points to the config JSON, and defines the health probe — a real `ocrmypdf` run on a bundled `sample.pdf`:

```java
@Override
public ProbeTransform getProbeTransform() {
    return new ProbeTransform(
            "sample.pdf", "application/pdf", "application/pdf",
            Map.of(), 120, 16, 800, 20480, 3601, 1840);
}
```

The config JSON declares the supported MIME pair and the optional `language` parameter:

```json
{
  "transformOptions": {
    "ocrOptions": [
      { "value": { "name": "language" } }
    ]
  },
  "transformers": [{
    "transformerName": "ocr",
    "supportedSourceAndTargetList": [
      {
        "sourceMediaType": "application/pdf",
        "targetMediaType": "application/pdf",
        "maxSourceSizeBytes": 52428800
      }
    ],
    "transformOptions": ["ocrOptions"]
  }]
}
```

All 15 engines follow this same structure. Switching from `CustomTransformer` to `AbstractMetadataExtractorEmbedder` is the only variation — used by `xml`, `excel`, `pii`, and `ai` to write metadata back into Alfresco properties instead of returning a converted file.

---

## Building a New Engine

If you need a transform not covered by the existing 15, the pattern is straightforward. Here is how to add a hypothetical `barcode` engine that reads barcodes from images and returns the decoded text.

### The Three Files

**`BarcodeEngine.java`** — registers the engine with the framework:

```java
@Component
@RequiredArgsConstructor
public class BarcodeEngine implements TransformEngine {

    private final TransformConfigResourceReader transformConfigResourceReader;

    @Override
    public String getTransformEngineName() { return "barcode"; }

    @Override
    public String getStartupMessage() { return "Startup barcode"; }

    @Override
    public TransformConfig getTransformConfig() {
        return transformConfigResourceReader.read("classpath:barcode_engine_config.json");
    }

    @Override
    public ProbeTransform getProbeTransform() {
        return new ProbeTransform("sample.png", "image/png", "text/plain",
                Map.of(), 60, 16, 400, 1024, 600, 400);
    }
}
```

**`BarcodeTransformer.java`** — implements the transform logic:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class BarcodeTransformer implements CustomTransformer {

    @Override
    public String getTransformerName() { return "barcode"; }

    @Override
    public void transform(String sourceMimetype, InputStream inputStream,
                          String targetMimetype, OutputStream outputStream,
                          Map<String, String> transformOptions,
                          TransformManager transformManager) throws Exception {

        Path tempDir = Files.createTempDirectory("barcode-");
        File inputFile = tempDir.resolve("input.png").toFile();
        try {
            Files.copy(inputStream, inputFile.toPath());

            List<String> cmd = List.of("zbarimg", "--raw", inputFile.getAbsolutePath());
            Process proc = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            byte[] output = proc.getInputStream().readAllBytes();
            int exitCode = proc.waitFor();
            if (exitCode != 0) throw new RuntimeException("zbarimg failed: " + new String(output));

            outputStream.write(output);
        } finally {
            // clean up
        }
    }
}
```

For metadata engines (where the result is written as Alfresco properties rather than a returned file), extend `AbstractMetadataExtractorEmbedder` and override `extractMetadata()` returning a `Map<String, Serializable>`.

**`barcode_engine_config.json`** — declares the supported MIME pairs:

```json
{
  "transformers": [{
    "transformerName": "barcode",
    "supportedSourceAndTargetList": [
      { "sourceMediaType": "image/png",  "targetMediaType": "text/plain" },
      { "sourceMediaType": "image/jpeg", "targetMediaType": "text/plain" }
    ]
  }]
}
```

You also need:
- `application-default.yaml` — Spring Boot config with the queue name and transform version
- `sample.png` — a real barcode image the health probe can successfully transform
- `_metadata_extract.properties` — only for metadata engines; maps extracted key names to `cm:` content model properties

### Wiring the Module into the Project

Once the three files exist, wire the module in seven steps:

1. Create `engines/barcode/` as a Maven module with the standard `pom.xml` (copy from any existing engine; keep the `spring-boot-maven-plugin` block with `<classifier>exec</classifier>`)
2. Add `<module>engines/barcode</module>` to root `pom.xml` **before** `engines/aio` — the AIO module must come last
3. Add the barcode artifact as a `<dependency>` in `engines/aio/pom.xml` so it is included in the all-in-one image
4. Write `engines/barcode/Dockerfile` — typically `FROM eclipse-temurin:21-jre`, install `zbar-tools`, copy the exec JAR
5. Add `make build-barcode` to the `Makefile` using the `$(call build-engine,...)` helper
6. Add `barcode` to the `build-engines`, `push-engines`, and `clean-engines` loops in the `Makefile`
7. Add a smoke-test assertion to `smoke-test.sh` — POST a sample PNG, assert HTTP 200 and a non-empty response

### Testing Without Alfresco

You can validate a new engine entirely independently of Alfresco:

```bash
# Build and start
mvn clean package -pl engines/barcode
docker build -t alfresco-transform-barcode:latest engines/barcode/
docker run --rm -p 8090:8090 -e TEST_ENDPOINT_ENABLED=true alfresco-transform-barcode:latest

# Confirm it starts clean
curl http://localhost:8090/actuator/health
# {"status":"UP"}

# Run a real transform
curl -X POST http://localhost:8090/transform \
  -F "file=@sample.png;type=image/png" \
  -F "targetMediaType=text/plain" \
  -o result.txt && cat result.txt
```

Only after the container passes health and returns the expected output should you wire it into Alfresco via `localTransform.barcode.url`.

---

## A Few Engines Worth Extra Attention

**`ai`**: requires Docker Model Runner, available in Docker Desktop 4.40 and later. The engine calls a local OpenAI-compatible API endpoint — no OpenAI API key, no outbound network call. The model (default: `ai/smollm2`) is loaded by Docker Model Runner separately. Memory requirements depend on the model; plan for 8 GB or more for a typical LLM.

**`whisper`**: the `openai-whisper` Python library runs entirely locally — there is no call to OpenAI's API despite the name. Transcription time scales linearly with audio length; set a generous `localTransform.timeout` in `alfresco-global.properties` for long recordings. A CUDA-enabled image variant is available for GPU acceleration.

**`pii`**: behaves differently from most engines — it produces two outputs from one input. It returns a redacted PDF (PII replaced with black boxes) as the rendition and writes the detected PII categories (email, phone number, person name, etc.) as Alfresco metadata properties. Before enabling this engine in production, decide whether redaction should replace the original or create a new rendition, and review your organisation's retention policy for the PII metadata itself.

**`convert2md`**: Docling ML models are downloaded and cached at image build time into the image, so the first request does not stall waiting for a model download. In air-gapped environments, build the image on a connected machine and transfer it — or mount a pre-populated HuggingFace cache volume at `/root/.cache/huggingface`.

---

## Conclusion

`alfresco-transform-extras` adds 15 engines to Alfresco's transform pipeline with no changes to the core platform. OCR, AI metadata extraction, speech transcription, PII redaction, video thumbnails, HEIC conversion, and the full Markdown ecosystem become standard Alfresco capabilities.

For DevOps teams: use the AIO image to get all 15 engines running in minutes, or deploy individual containers in production to scale and upgrade them independently. The wiring into both Community and Enterprise editions follows the same patterns you already use for the official engines.

For developers: the three-file pattern — Engine, Transformer, config JSON — is the entire contract. Any Java developer familiar with Spring Boot can add a new engine in an afternoon. The external tool can be anything reachable via `ProcessBuilder`: a Python script, a compiled binary, an HTTP service.

The project source, Dockerfiles, and Makefile are on GitHub. Try the AIO image in your local Alfresco stack, and if you build an engine that others would find useful, open a pull request.
