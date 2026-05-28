FROM python:3.11-slim AS python-deps
ARG WHISPER_VERSION=20250625
RUN pip install --no-cache-dir "openai-whisper==${WHISPER_VERSION}"

FROM eclipse-temurin:21-jre AS runtime
COPY --from=python-deps /usr/local /usr/local
RUN apt-get update && apt-get install -y --no-install-recommends python3 ffmpeg \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
RUN groupadd -g 1001 alfresco && useradd -u 33017 -g alfresco -m alfte
WORKDIR /app
COPY target/alf-tengine-whisper-1.0.0-exec.jar whisper.jar
USER alfte
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/whisper.jar"]

# ── Integration tests ─────────────────────────────────────────────────────────
# docker build --target test-runner -t alf-tengine-whisper-test engines/whisper/ && docker run --rm -e MANAGEMENT_HEALTH_JMS_ENABLED=false alf-tengine-whisper-test
FROM runtime AS test-runner
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=runtime /app /app
USER alfte
ENTRYPOINT ["sh", "-c", "\
  java $JAVA_OPTS -jar /app/whisper.jar & \
  APP_PID=$! && \
  for i in $(seq 1 30); do \
    curl -sf http://localhost:8090/actuator/health > /dev/null 2>&1 && break; \
    sleep 2; \
  done && \
  curl -sf http://localhost:8090/actuator/health | grep -q 'UP' && \
  echo 'SMOKE TEST PASSED' && \
  kill $APP_PID \
"]
