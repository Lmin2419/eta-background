#!/bin/sh
# build_engine.sh — compile vdrive/src/*.java into classes.dex.
# This is exactly what build_all.sh:17-23 does, extracted so it can run alone in CI.
#
#   ANDROID_JAR=/opt/android/platforms/android-36/android.jar \
#   R8_JAR=/opt/buildtools/r8.jar \
#   OUT=$PWD/out \
#   sh build_engine.sh
#
# Output: $OUT/classes.dex   (the file that becomes /data/local/tmp/eta/vdrive.dex
#                             and assets/vdrive.dex inside the APK)
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${SRC:-$HERE/vdrive/src}
SHARED=${SHARED:-$HERE/../shared/src}
OUT=${OUT:-$HERE/vdrive/dexout}
WORK=${WORK:-$HERE/vbuild2}
ANDROID_JAR=${ANDROID_JAR:?set ANDROID_JAR to platforms/android-XX/android.jar}
R8_JAR=${R8_JAR:?set R8_JAR to r8.jar (contains com.android.tools.r8.D8)}
MIN_API=${MIN_API:-29}
JAVAC=${JAVAC:-javac}
JAVA=${JAVA:-java}

[ -f "$ANDROID_JAR" ] || { echo "missing android.jar: $ANDROID_JAR"; exit 1; }
[ -f "$R8_JAR" ]       || { echo "missing r8.jar: $R8_JAR"; exit 1; }
ls "$SRC"/*.java >/dev/null 2>&1 || { echo "no sources in $SRC"; exit 1; }

rm -rf "$WORK"
mkdir -p "$WORK/classes" "$OUT"
rm -f "$OUT/classes.dex"

echo "== javac =="
"$JAVAC" -nowarn -source 8 -target 8 -cp "$ANDROID_JAR" -d "$WORK/classes" "$SRC"/*.java "$SHARED"/com/eta/shadow/*.java

echo "== d8 =="
"$JAVA" -cp "$R8_JAR" com.android.tools.r8.D8 --min-api "$MIN_API" --lib "$ANDROID_JAR" \
  --output "$OUT" $(find "$WORK/classes" -name '*.class' | sort)

echo "== result =="
ls -l "$OUT/classes.dex"
sha256sum "$OUT/classes.dex"
