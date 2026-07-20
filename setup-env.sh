#!/bin/bash
# eval-system 环境设置 — 切换到 JDK 17
# 用法: source setup-env.sh
# 不影响系统默认的 JAVA_HOME (Java 8)

export JAVA17_HOME="$HOME/.jdks/temurin-17"
export JAVA_HOME="$JAVA17_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "JDK 17 activated: $JAVA_HOME"
java -version 2>&1 | head -1
