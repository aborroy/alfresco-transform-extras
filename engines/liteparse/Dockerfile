FROM python:3.13-slim AS python-deps
ARG LITEPARSE_VERSION=2.0.4
RUN pip install --no-cache-dir liteparse==${LITEPARSE_VERSION}

FROM eclipse-temurin:21-jre AS runtime
COPY --from=python-deps /usr/local /usr/local
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    libreoffice-calc libreoffice-writer libreoffice-impress \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd -g 1001 alfresco && useradd -u 33050 -g alfresco -m alfte
WORKDIR /app
COPY target/alf-tengine-liteparse-1.1.0-exec.jar liteparse.jar
USER alfte
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/liteparse.jar"]

# ── Integration tests ─────────────────────────────────────────────────────────
# docker build --target test-runner -t alf-tengine-liteparse-test engines/liteparse/ && docker run --rm -e MANAGEMENT_HEALTH_JMS_ENABLED=false alf-tengine-liteparse-test
FROM runtime AS test-runner
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=runtime /app /app
USER alfte
ENTRYPOINT ["sh", "-c", "\
  java $JAVA_OPTS -jar /app/liteparse.jar & \
  APP_PID=$! && \
  for i in $(seq 1 30); do \
    curl -sf http://localhost:8090/actuator/health > /dev/null 2>&1 && break; \
    sleep 2; \
  done && \
  curl -sf http://localhost:8090/actuator/health | grep -q 'UP' && \
  echo 'SMOKE TEST PASSED' && \
  kill $APP_PID \
"]
