#!/bin/sh
# Optional local toolchain; normal Android Studio/JDK installations work too.
RG_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -d "$RG_ROOT/.tools/jdk/jdk-17.0.20.1+1/Contents/Home" ]; then
  export JAVA_HOME="$RG_ROOT/.tools/jdk/jdk-17.0.20.1+1/Contents/Home"
fi
if [ -d "$RG_ROOT/.tools/android-sdk" ]; then
  export ANDROID_HOME="$RG_ROOT/.tools/android-sdk"
fi
exec "$RG_ROOT/gradlew" "$@"
