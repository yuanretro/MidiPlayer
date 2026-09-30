#!/usr/bin/env bash
# Build MidiPlayer for Linux.
#
# Output in dist/:
#   MidiPlayer.jar                          runnable jar, needs Java 8+ installed
#   MidiPlayer-<version>-linux-<arch>.tar.gz  self-contained bundle with its own Java runtime
#
# Requirements: JDK 11+ (javac, jar, jlink) on the build machine.
# The bundle is built for the architecture of the build machine.
set -euo pipefail

cd "$(dirname "$0")"
VERSION="${VERSION:-dev}"
ARCH="$(uname -m)"
case "$ARCH" in
    amd64) ARCH=x86_64 ;;
    arm64) ARCH=aarch64 ;;
esac

BUILD=build
DIST=dist
NAME="MidiPlayer-${VERSION}-linux-${ARCH}"
BUNDLE="$BUILD/$NAME"

rm -rf "$BUILD" "$DIST"
mkdir -p "$BUILD/classes" "$DIST"

echo "==> Compiling"
javac --release 8 -Xlint:-options -encoding UTF-8 -d "$BUILD/classes" src/*.java

echo "==> Packaging jar"
jar cfm "$DIST/MidiPlayer.jar" src/META-INF/MANIFEST.MF -C "$BUILD/classes" .

echo "==> Creating Java runtime"
jlink --add-modules java.base,java.desktop,java.prefs \
      --strip-debug --no-man-pages --no-header-files --compress=2 \
      --output "$BUNDLE/runtime" 2>&1 | grep -v -i "deprecat" || true
test -x "$BUNDLE/runtime/bin/java"

echo "==> Assembling bundle"
mkdir -p "$BUNDLE/bin" "$BUNDLE/lib"
cp "$DIST/MidiPlayer.jar" "$BUNDLE/lib/"

write_launcher() {
    # $1 = script name, $2 = main class
    cat > "$BUNDLE/bin/$1" <<EOF
#!/bin/sh
# Resolve the real location so the script also works through a symlink
SELF="\$(readlink -f "\$0" 2>/dev/null || echo "\$0")"
APP_HOME="\$(cd "\$(dirname "\$SELF")/.." && pwd)"
exec "\$APP_HOME/runtime/bin/java" -cp "\$APP_HOME/lib/MidiPlayer.jar" $2 "\$@"
EOF
    chmod +x "$BUNDLE/bin/$1"
}
write_launcher midiplayer MidiPlayerGUI
write_launcher midiplayer-cli MidiPlayerAplay

cat > "$BUNDLE/README.txt" <<EOF
MidiPlayer ${VERSION} (Linux ${ARCH})

Run:
  ./bin/midiplayer        GUI
  ./bin/midiplayer-cli    text mode

A Java runtime is included, Java does not need to be installed.

Requirements on the target machine:
  - aplaymidi (package alsa-utils), e.g.  sudo apt install alsa-utils
  - a desktop session (X11 or XWayland) for the GUI
  - a MIDI output: a hardware synth, or a software synth such as
    TiMidity++ or FluidSynth running as an ALSA sequencer client
EOF

tar -C "$BUILD" -czf "$DIST/$NAME.tar.gz" "$NAME"

echo "==> Done"
ls -lh "$DIST"
