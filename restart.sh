#!/bin/bash
# eval-system restart script — stop, package, start jar, wait for health
# Usage: bash restart.sh

set -e
PORT=8080
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$APP_DIR/tmp"
JAR=$(find "$APP_DIR/eval-boot/target" -maxdepth 1 -name "*.jar" -not -name "*sources*" 2>/dev/null | head -1)
mkdir -p "$LOG_DIR"

echo "=== eval-system restart ==="

# 1. Kill existing process on port
PID=$(netstat -ano 2>/dev/null | grep ":$PORT " | grep LISTENING | awk '{print $NF}' | head -1)
if [ -n "$PID" ]; then
    echo "Killing process on port $PORT (PID=$PID)..."
    taskkill //PID "$PID" //F 2>/dev/null || true
    sleep 2
fi

# 2. Switch JDK + package
echo "Packaging..."
source "$APP_DIR/setup-env.sh" 2>/dev/null
mvn package -DskipTests -q -f "$APP_DIR/pom.xml" 2>&1 | tail -3
JAR=$(find "$APP_DIR/eval-boot/target" -maxdepth 1 -name "*.jar" -not -name "*sources*" | head -1)

# 3. Start app in background
echo "Starting $JAR ..."
JAVA17="$HOME/.jdks/temurin-17/bin/java"
nohup "$JAVA17" -jar "$JAR" > "$LOG_DIR/app.log" 2>&1 &
APP_PID=$!
echo "App PID: $APP_PID"

# 4. Wait for health
echo -n "Waiting for health..."
for i in $(seq 1 30); do
    sleep 2
    if curl -s http://localhost:$PORT/actuator/health 2>/dev/null | grep -q UP; then
        echo " UP! (${i}x2s)"
        echo "=== Restart complete ==="
        exit 0
    fi
    echo -n "."
done

echo ""
echo "WARNING: Health check timed out. Check $LOG_DIR/app.log"
exit 1
