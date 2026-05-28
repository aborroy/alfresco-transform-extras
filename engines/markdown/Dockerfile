FROM eclipse-temurin:21-jre AS runtime
ARG PANDOC_VERSION=3.9.0.2
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl texlive-latex-base texlive-xetex texlive-fonts-recommended lmodern \
    && rm -rf /var/lib/apt/lists/*
RUN ARCH=$(dpkg --print-architecture) && \
    if [ "$ARCH" = "amd64" ]; then PANDOC_ARCH="amd64"; else PANDOC_ARCH="arm64"; fi && \
    curl -fsSL "https://github.com/jgm/pandoc/releases/download/${PANDOC_VERSION}/pandoc-${PANDOC_VERSION}-linux-${PANDOC_ARCH}.tar.gz" \
    | tar -xz --strip-components=2 -C /usr/local/bin "pandoc-${PANDOC_VERSION}/bin/pandoc"
RUN groupadd -g 1001 alfresco && useradd -u 33017 -g alfresco -m alfte
WORKDIR /app
COPY target/alf-tengine-markdown-1.0.0-exec.jar markdown.jar
USER alfte
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/markdown.jar"]

# ── Integration tests ─────────────────────────────────────────────────────────
# docker build --target test-runner -t alf-tengine-markdown-test engines/markdown/ && docker run --rm -e MANAGEMENT_HEALTH_JMS_ENABLED=false alf-tengine-markdown-test
FROM runtime AS test-runner
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=runtime /app /app
USER alfte
ENTRYPOINT ["sh", "-c", "\
  java $JAVA_OPTS -jar /app/markdown.jar & \
  APP_PID=$! && \
  for i in $(seq 1 30); do \
    curl -sf http://localhost:8090/actuator/health > /dev/null 2>&1 && break; \
    sleep 2; \
  done && \
  curl -sf http://localhost:8090/actuator/health | grep -q 'UP' && \
  echo 'SMOKE TEST PASSED' && \
  kill $APP_PID \
"]
