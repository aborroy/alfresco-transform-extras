FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd -g 1001 alfresco && useradd -u 33017 -g alfresco -m alfte
WORKDIR /app
COPY target/alf-tengine-videothumb-1.0.0-exec.jar videothumb.jar
USER alfte
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/videothumb.jar"]

# ── Integration tests ─────────────────────────────────────────────────────────
# docker build --target test-runner -t alf-tengine-videothumb-test engines/videothumb/ && docker run --rm -e MANAGEMENT_HEALTH_JMS_ENABLED=false alf-tengine-videothumb-test
FROM runtime AS test-runner
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=runtime /app /app
USER alfte
ENTRYPOINT ["sh", "-c", "\
  java $JAVA_OPTS -jar /app/videothumb.jar & \
  APP_PID=$! && \
  for i in $(seq 1 30); do \
    curl -sf http://localhost:8090/actuator/health > /dev/null 2>&1 && break; \
    sleep 2; \
  done && \
  curl -sf http://localhost:8090/actuator/health | grep -q 'UP' && \
  echo 'SMOKE TEST PASSED' && \
  kill $APP_PID \
"]
