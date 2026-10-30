#!/bin/sh
# Uruchamia Gradle bez wrappera: dystrybucja z cache Android Studio + JDK 21.
export JAVA_HOME="/c/Users/kubab/.jdks/jbr-21.0.11"
GRADLE="/c/Users/kubab/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle"
cd "$(dirname "$0")/.." || exit 1
exec "$GRADLE" "$@"
