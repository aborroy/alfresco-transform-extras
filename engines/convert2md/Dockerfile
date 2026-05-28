FROM python:3.11-slim AS python-deps
RUN pip install --no-cache-dir "docling==2.96.0"

FROM eclipse-temurin:17-jre AS runtime
COPY --from=python-deps /usr/local /usr/local
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 libgl1 libmagic1 poppler-utils \
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
