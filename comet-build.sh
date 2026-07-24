#!/bin/bash
export JAVA_HOME="$HOME/.jdks/temurin-17"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -f eval-system/pom.xml test -q -Dtest=MultiModelCompareServiceTest
