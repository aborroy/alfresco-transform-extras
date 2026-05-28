# NOTE: The COPY docker/presidio/ instruction below copies from the PROJECT ROOT
# docker/presidio/ directory. The build context for this Dockerfile must be the
# project root (../../) rather than the engines/pii/ directory, e.g.:
#   docker build -f engines/pii/Dockerfile .
FROM python:3.11-slim AS python-deps
RUN pip install --no-cache-dir \
    "presidio-analyzer==2.2.362" \
    "presidio-anonymizer==2.2.362" \
    "spacy==3.8.14" \
    "pymupdf==1.27.2.3"
RUN python -m spacy download en_core_web_lg

FROM eclipse-temurin:21-jre AS runtime
COPY --from=python-deps /usr/local /usr/local
RUN apt-get update && apt-get install -y --no-install-recommends python3 \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
COPY docker/presidio/ /opt/presidio/
RUN groupadd -g 1001 alfresco && useradd -u 33017 -g alfresco -m alfte
WORKDIR /app
COPY engines/pii/target/alf-tengine-pii-1.0.0-exec.jar pii.jar
USER alfte
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/pii.jar"]

# ── Integration tests ─────────────────────────────────────────────────────────
# docker build --target test-runner -t alf-tengine-pii-test -f engines/pii/Dockerfile . && docker run --rm -e MANAGEMENT_HEALTH_JMS_ENABLED=false alf-tengine-pii-test
FROM runtime AS test-runner
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=runtime /app /app
USER alfte
ENTRYPOINT ["sh", "-c", "\
  java $JAVA_OPTS -jar /app/pii.jar & \
  APP_PID=$! && \
  for i in $(seq 1 30); do \
    curl -sf http://localhost:8090/actuator/health > /dev/null 2>&1 && break; \
    sleep 2; \
  done && \
  curl -sf http://localhost:8090/actuator/health | grep -q 'UP' && \
  echo 'SMOKE TEST PASSED' && \
  kill $APP_PID \
"]
