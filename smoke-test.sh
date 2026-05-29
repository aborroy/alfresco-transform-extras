#!/usr/bin/env bash
# smoke-test.sh — end-to-end smoke tests for AIO and individual engine containers.
#
# Usage:
#   bash smoke-test.sh              # runs AIO section then all 14 individual engines
#   bash smoke-test.sh --aio-only
#   bash smoke-test.sh --engines-only
#
# Requirements: docker, curl
# All engine images must be built first (make build / make build-engines).

set -euo pipefail

REGISTRY="${REGISTRY:-angelborroy}"
VERSION="${VERSION:-latest}"
AIO_PORT="${AIO_PORT:-8090}"
ENGINE_PORT="${ENGINE_PORT:-8091}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

RUN_AIO=true
RUN_ENGINES=true
case "${1:-}" in
  --aio-only)     RUN_ENGINES=false ;;
  --engines-only) RUN_AIO=false ;;
esac

PASS=0
FAIL=0
FAILURES=()

# ── Helpers ───────────────────────────────────────────────────────────────────

wait_healthy() {
  local url="$1" label="$2" max="${3:-120}"
  local i=0
  printf "  Waiting for %s to be healthy" "$label"
  while [ $i -lt $max ]; do
    if curl -sf --ipv4 "$url" 2>/dev/null | grep -q '"UP"'; then
      echo " OK"
      return 0
    fi
    printf "."
    sleep 2
    i=$((i + 2))
  done
  echo " TIMEOUT"
  return 1
}

# run_transform LABEL PORT SOURCE_MIME TARGET_MIME SAMPLE_FILE [EXTRA_FIELDS...]
run_transform() {
  local label="$1" port="$2" src="$3" tgt="$4" sample="$5"
  shift 5
  local extra=("$@")

  # Derive a sane extension for the output temp file
  local ext
  case "$tgt" in
    application/pdf)                                                       ext=pdf ;;
    text/plain)                                                            ext=txt ;;
    text/html)                                                             ext=html ;;
    text/markdown)                                                         ext=md ;;
    image/jpeg)                                                            ext=jpg ;;
    image/png)                                                             ext=png ;;
    application/vnd.openxmlformats-officedocument.wordprocessingml.document) ext=docx ;;
    application/vnd.ms-excel*)                                             ext=xls ;;
    alfresco-metadata-extract)                                             ext=json ;;
    *)                                                                     ext=bin ;;
  esac

  local outfile errfile
  outfile="$(mktemp)"
  errfile="$(mktemp)"

  local -a form_args=(
    -F "file=@${sample}"
    -F "sourceMimetype=${src}"
    -F "targetMimetype=${tgt}"
    -F "targetExtension=${ext}"
  )
  for f in "${extra[@]:-}"; do
    [ -n "$f" ] && form_args+=(-F "$f")
  done

  local http_code
  http_code=$(curl -s --ipv4 -o "$outfile" -w "%{http_code}" \
    "${form_args[@]}" \
    "http://localhost:${port}/transform" 2>"$errfile")

  local ok=false detail=""
  if [ "$http_code" = "200" ]; then
    if [ "$tgt" = "alfresco-metadata-extract" ]; then
      if python3 -c "import sys,json; json.load(open('$outfile'))" 2>/dev/null; then
        ok=true
      else
        detail="invalid JSON response: $(head -c 200 "$outfile" 2>/dev/null)"
      fi
    elif [ -s "$outfile" ]; then
      ok=true
    else
      detail="empty response body"
    fi
  else
    # Capture the error body — Spring error responses are JSON with a "message" field
    local body
    body="$(cat "$outfile" 2>/dev/null)"
    detail="$(printf '%s' "$body" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d.get('message') or d.get('error') or str(d)[:200])
except Exception:
    raw = open('$outfile').read(200).strip()
    print(raw if raw else 'no response body')
" 2>/dev/null || head -c 200 "$outfile" 2>/dev/null || echo "no response body")"
  fi

  rm -f "$outfile" "$errfile"

  printf "  %-18s  %-52s  %-52s  " "$label" "$src" "$tgt"
  if $ok; then
    echo "PASS"
    PASS=$((PASS + 1))
  else
    echo "FAIL (HTTP ${http_code})"
    [ -n "$detail" ] && printf "    %s\n" "$detail"
    FAIL=$((FAIL + 1))
    FAILURES+=("[$label] ${src} → ${tgt}  (HTTP ${http_code}: ${detail})")
  fi
}

# ── Transform definitions ─────────────────────────────────────────────────────
# Each entry: ENGINE SOURCE_MIME TARGET_MIME SAMPLE_RELATIVE_PATH [EXTRA...]

declare -a TRANSFORMS=(
  "xml|application/xml|alfresco-metadata-extract|engines/xml/src/main/resources/sample.xml"
  "xml|text/xml|alfresco-metadata-extract|engines/xml/src/main/resources/sample.xml"

  "excel|application/vnd.ms-excel|alfresco-metadata-extract|engines/excel/src/main/resources/sample.xlsx"
  "excel|application/vnd.openxmlformats-officedocument.spreadsheetml.sheet|alfresco-metadata-extract|engines/excel/src/main/resources/sample.xlsx"
  "excel|application/vnd.ms-excel.sheet.macroenabled.12|alfresco-metadata-extract|engines/excel/src/main/resources/sample.xlsx"
  "excel|application/vnd.ms-excel.sheet.binary.macroenabled.12|alfresco-metadata-extract|engines/excel/src/main/resources/sample.xlsx"

  "markdown|text/markdown|application/pdf|engines/markdown/src/main/resources/sample.md"
  "markdown|text/x-markdown|application/pdf|engines/markdown/src/main/resources/sample.md"

  "html2md|text/html|text/markdown|engines/html2md/src/main/resources/sample.html"
  "html2md|application/xhtml+xml|text/markdown|engines/html2md/src/main/resources/sample.html"

  "md2html|text/markdown|text/html|engines/md2html/src/main/resources/sample.md"
  "md2html|text/x-markdown|text/html|engines/md2html/src/main/resources/sample.md"

  "md2doc|text/markdown|application/vnd.openxmlformats-officedocument.wordprocessingml.document|engines/md2doc/src/main/resources/sample.md"
  "md2doc|text/markdown|application/pdf|engines/md2doc/src/main/resources/sample.md"
  "md2doc|text/x-markdown|application/vnd.openxmlformats-officedocument.wordprocessingml.document|engines/md2doc/src/main/resources/sample.md"
  "md2doc|text/x-markdown|application/pdf|engines/md2doc/src/main/resources/sample.md"

  "msg|message/rfc822|application/pdf|engines/msg/src/main/resources/sample.eml"
  "msg|application/vnd.ms-outlook|application/pdf|engines/msg/src/main/resources/sample.msg"

  "ocr|application/pdf|application/pdf|engines/ocr/src/main/resources/sample.pdf"

  "convert2md|application/pdf|text/markdown|engines/convert2md/src/main/resources/sample.pdf"

  "pdf2docx|application/pdf|application/vnd.openxmlformats-officedocument.wordprocessingml.document|engines/pdf2docx/src/main/resources/sample.pdf"

  "pii|application/pdf|application/pdf|engines/pii/src/main/resources/sample.pdf"
  "pii|application/pdf|alfresco-metadata-extract|engines/pii/src/main/resources/sample.pdf"

  "videothumb|video/mp4|image/jpeg|engines/videothumb/src/main/resources/sample.mp4"
  "videothumb|video/quicktime|image/jpeg|engines/videothumb/src/main/resources/sample.mp4"
  "videothumb|video/x-msvideo|image/jpeg|engines/videothumb/src/main/resources/sample.mp4"
  "videothumb|video/x-matroska|image/jpeg|engines/videothumb/src/main/resources/sample.mp4"
  "videothumb|video/webm|image/jpeg|engines/videothumb/src/main/resources/sample.mp4"

  "whisper|audio/mpeg|text/plain|engines/whisper/src/main/resources/sample.mp3"
  "whisper|audio/wav|text/plain|engines/whisper/src/main/resources/sample.mp3"
  "whisper|audio/ogg|text/plain|engines/whisper/src/main/resources/sample.mp3"
  "whisper|audio/flac|text/plain|engines/whisper/src/main/resources/sample.mp3"
  "whisper|video/mp4|text/plain|engines/whisper/src/main/resources/sample.mp3"
  "whisper|video/quicktime|text/plain|engines/whisper/src/main/resources/sample.mp3"
  "whisper|video/x-msvideo|text/plain|engines/whisper/src/main/resources/sample.mp3"
  "whisper|video/x-matroska|text/plain|engines/whisper/src/main/resources/sample.mp3"

  "heic|image/heic|image/jpeg|engines/heic/src/main/resources/sample.heic"
  "heic|image/heic|image/png|engines/heic/src/main/resources/sample.heic"
  "heic|image/heif|image/jpeg|engines/heic/src/main/resources/sample.heic"
  "heic|image/heif|image/png|engines/heic/src/main/resources/sample.heic"
)

run_transforms_for_engine() {
  local engine="$1" port="$2" prefix="$3"
  for entry in "${TRANSFORMS[@]}"; do
    IFS='|' read -r eng src tgt sample <<< "$entry"
    [ "$eng" = "$engine" ] || continue
    local fullpath="${SCRIPT_DIR}/${sample}"
    if [ ! -f "$fullpath" ]; then
      printf "  %-18s  %-52s  %-52s  SKIP (no sample)\n" "$prefix" "$src" "$tgt"
      continue
    fi
    run_transform "$prefix" "$port" "$src" "$tgt" "$fullpath"
  done
}

# ── AIO section ───────────────────────────────────────────────────────────────

if $RUN_AIO; then
  echo ""
  echo "══════════════════════════════════════════════════════════════"
  echo "  AIO container smoke test"
  echo "══════════════════════════════════════════════════════════════"

  cd "$SCRIPT_DIR"
  docker compose up -d
  trap 'echo ""; echo "Stopping AIO..."; docker compose down' EXIT

  if ! wait_healthy "http://localhost:${AIO_PORT}/actuator/health" "AIO:${AIO_PORT}" 180; then
    echo "ERROR: AIO container did not become healthy" >&2
    docker compose down
    exit 1
  fi

  for engine in xml excel markdown html2md md2html md2doc msg ocr convert2md pdf2docx pii videothumb whisper heic; do
    echo ""
    echo "  --- ${engine} ---"
    run_transforms_for_engine "$engine" "$AIO_PORT" "AIO/${engine}"
  done

  trap - EXIT
  echo ""
  echo "Stopping AIO..."
  docker compose down
fi

# ── Individual engines section ────────────────────────────────────────────────

if $RUN_ENGINES; then
  ENGINES=(xml excel markdown html2md md2html md2doc msg ocr convert2md pdf2docx pii videothumb whisper heic)

  for engine in "${ENGINES[@]}"; do
    echo ""
    echo "══════════════════════════════════════════════════════════════"
    echo "  Engine: ${engine}"
    echo "══════════════════════════════════════════════════════════════"

    image="${REGISTRY}/alf-tengine-${engine}:${VERSION}"

    if ! docker image inspect "$image" > /dev/null 2>&1; then
      echo "  SKIP — image ${image} not found locally (run: make build-${engine})"
      continue
    fi

    container="smoke-${engine}"
    docker run -d --rm \
      -p "${ENGINE_PORT}:8090" \
      -e MANAGEMENT_HEALTH_JMS_ENABLED=false \
      --name "$container" \
      "$image" > /dev/null

    if ! wait_healthy "http://localhost:${ENGINE_PORT}/actuator/health" "${engine}:${ENGINE_PORT}" 120; then
      echo "  ERROR: ${engine} did not become healthy" >&2
      docker stop "$container" 2>/dev/null || true
      FAIL=$((FAIL + 1))
      FAILURES+=("[${engine}] container failed to start")
      continue
    fi

    run_transforms_for_engine "$engine" "$ENGINE_PORT" "$engine"

    docker stop "$container" > /dev/null
  done
fi

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  Summary: ${PASS}/${TOTAL} passed"
echo "══════════════════════════════════════════════════════════════"

if [ ${#FAILURES[@]} -gt 0 ]; then
  echo ""
  echo "  Failures:"
  for f in "${FAILURES[@]}"; do
    echo "    - $f"
  done
  echo ""
  exit 1
fi

echo ""
