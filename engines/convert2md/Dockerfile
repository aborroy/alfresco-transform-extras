FROM python:3.11-slim AS python-deps
RUN apt-get update && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 libxcb1 \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir "docling==2.96.0" "onnxruntime==1.20.1"

# Pre-fetch docling/HuggingFace models and RapidOCR weights so the non-root
# runtime user doesn't try to download them on first request.
ENV HF_HOME=/usr/local/share/huggingface
COPY src/main/resources/sample.pdf /tmp/prefetch-sample.pdf
RUN mkdir -p "$HF_HOME" \
    && python -c "from rapidocr import RapidOCR; RapidOCR()" \
    && python -c "from docling.document_converter import DocumentConverter; DocumentConverter().convert('/tmp/prefetch-sample.pdf').document.export_to_markdown()" \
    && rm -f /tmp/prefetch-sample.pdf

FROM eclipse-temurin:21-jre AS runtime
COPY --from=python-deps /usr/local /usr/local
COPY --from=python-deps /usr/local/share/huggingface /usr/local/share/huggingface
RUN chmod -R a+rwX /usr/local/share/huggingface
ENV HF_HOME=/usr/local/share/huggingface
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 libmagic1 poppler-utils \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
RUN groupadd -g 1001 alfresco && useradd -u 33017 -g alfresco -m alfte
WORKDIR /app
COPY target/alf-tengine-convert2md-1.0.0-exec.jar convert2md.jar
USER alfte
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/convert2md.jar"]

# ── Integration tests ─────────────────────────────────────────────────────────
# docker build --target test-runner -t alf-tengine-convert2md-test engines/convert2md/ && docker run --rm -e MANAGEMENT_HEALTH_JMS_ENABLED=false alf-tengine-convert2md-test
FROM runtime AS test-runner
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=runtime /app /app
USER alfte
ENTRYPOINT ["sh", "-c", "\
  java $JAVA_OPTS -jar /app/convert2md.jar & \
  APP_PID=$! && \
  for i in $(seq 1 30); do \
    curl -sf http://localhost:8090/actuator/health > /dev/null 2>&1 && break; \
    sleep 2; \
  done && \
  curl -sf http://localhost:8090/actuator/health | grep -q 'UP' && \
  echo 'SMOKE TEST PASSED' && \
  kill $APP_PID \
"]
