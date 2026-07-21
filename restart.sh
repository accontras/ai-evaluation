#!/bin/bash
# eval-system restart script — kill → clean install → start jar → wait health
# Usage: bash restart.sh
#
# 关键: 用 mvn install (非 package), 确保依赖模块更新到本地 Maven 仓库
#       先 kill 进程再 clean, 避免 jar 文件锁

set -e
PORT=8080
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$APP_DIR/tmp"
JAVA17="$HOME/.jdks/temurin-17/bin/java"
mkdir -p "$LOG_DIR"

echo "=== eval-system restart ==="

# 1. Kill existing process on port (必须先杀, 否则 jar 被锁无法 clean)
PID=$(netstat -ano 2>/dev/null | grep ":$PORT " | grep LISTENING | awk '{print $NF}' | head -1)
if [ -n "$PID" ]; then
    echo "Killing process on port $PORT (PID=$PID)..."
    taskkill //PID "$PID" //F 2>/dev/null || true
    sleep 2
fi

# 2. Full rebuild — install 所有模块到本地仓库, 再打包 boot
echo "Building (install all modules → local repo)..."
source "$APP_DIR/setup-env.sh" 2>/dev/null
mvn clean install -DskipTests -q -f "$APP_DIR/pom.xml" 2>&1 | tail -3
JAR=$(find "$APP_DIR/eval-boot/target" -maxdepth 1 -name "*.jar" -not -name "*sources*" | head -1)

# 3. Start
echo "Starting $JAR ..."
nohup "$JAVA17" -jar "$JAR" > "$LOG_DIR/app.log" 2>&1 &
echo "App PID: $!"

# 4. Wait health
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
