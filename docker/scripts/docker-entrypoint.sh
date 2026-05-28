#!/bin/bash
set -e

JAR=/usr/bin/alf-tengine-aio.jar

if [ ! -f "$JAR" ]; then
    echo "ERROR: AIO JAR not found at $JAR"
    exit 1
fi

echo "Starting Alfresco TEngine AIO: $JAR"
exec java $JAVA_OPTS -jar "$JAR"
