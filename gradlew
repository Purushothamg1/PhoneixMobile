#!/bin/sh
SCRIPT=$(readlink -f "$0" 2>/dev/null || echo "$0")
APP_HOME=$(cd "$(dirname "$SCRIPT")" && pwd)
exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
