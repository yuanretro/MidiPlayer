#!/usr/bin/env bash
# Build MidiPlayer for Linux and Windows.
#
# Output in dist/:
#   MidiPlayer.jar                              runnable jar, needs Java 8+ installed
#   MidiPlayer-<version>-linux-<arch>.tar.gz    (on Linux) self-contained bundle with its own Java runtime
#   MidiPlayer-<version>-windows-<arch>.zip     (on Windows, run with Git Bash) self-contained
#                                               app with MidiPlayer.exe and its own Java runtime
#
# Requirements: JDK 11+ (javac, jar, jlink) on the build machine, JDK 14+ (jpackage) on Windows.
# The bundle is built for the operating system and architecture of the build machine.
set -euo pipefail

cd "$(dirname "$0")"
VERSION="${VERSION:-dev}"
ARCH="$(uname -m)"
case "$ARCH" in
    amd64) ARCH=x86_64 ;;
    arm64) ARCH=aarch64 ;;
esac

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) OS=windows ;;
    *) OS=linux ;;
esac

BUILD=build
DIST=dist
NAME="MidiPlayer-${VERSION}-${OS}-${ARCH}"
BUNDLE="$BUILD/$NAME"

rm -rf "$BUILD" "$DIST"
mkdir -p "$BUILD/classes" "$DIST"

echo "==> Compiling"
javac --release 8 -Xlint:-options -encoding UTF-8 -d "$BUILD/classes" src/*.java

echo "==> Packaging jar"
jar cfm "$DIST/MidiPlayer.jar" src/META-INF/MANIFEST.MF -C "$BUILD/classes" .

if [ "$OS" = windows ]; then
    echo "==> Creating Java runtime"
    jlink --add-modules java.base,java.desktop,java.prefs \
          --strip-debug --no-man-pages --no-header-files --compress=2 \
          --output "$BUILD/runtime" 2>&1 | grep -v -i "deprecat" || true
    test -f "$BUILD/runtime/bin/java.exe"

    echo "==> Creating Windows app"
    # jpackage needs a numeric version such as 1.2.3
    APP_VERSION="${VERSION#v}"
    [[ "$APP_VERSION" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]] || APP_VERSION=1.0.0
    mkdir -p "$BUILD/input"
    cp "$DIST/MidiPlayer.jar" "$BUILD/input/"
    # Second launcher for the text mode, opens a console window
    printf 'main-class=MidiPlayerCLI\nwin-console=true\n' > "$BUILD/cli-launcher.properties"
    jpackage --type app-image --name MidiPlayer --app-version "$APP_VERSION" \
             --input "$BUILD/input" --main-jar MidiPlayer.jar --main-class MidiPlayerGUI \
             --add-launcher MidiPlayer-cli="$BUILD/cli-launcher.properties" \
             --runtime-image "$BUILD/runtime" --dest "$BUILD/app"
    test -f "$BUILD/app/MidiPlayer/MidiPlayer.exe"
    test -f "$BUILD/app/MidiPlayer/MidiPlayer-cli.exe"
    mv "$BUILD/app/MidiPlayer" "$BUNDLE"

    cat > "$BUNDLE/README.txt" <<EOF
MidiPlayer ${VERSION} (Windows ${ARCH})

Run MidiPlayer.exe (GUI) or MidiPlayer-cli.exe (text mode, type "help" for the commands).
A Java runtime is included, Java does not need to be installed.

Requires Windows 10 64-bit, Windows 11 or Windows Server 2016 or newer.

Choose the output in the "Port" list at the bottom of the window. It lists every MIDI output
device of the system: hardware synthesizers and USB MIDI interfaces, Microsoft GS Wavetable
Synth, virtual ports (loopMIDI, VirtualMIDISynth), and the built-in Java synthesizer (Gervill).
Press "Refresh" after connecting a device.
EOF

    (cd "$BUILD" && 7z a -tzip -bso0 "../$DIST/$NAME.zip" "$NAME")
    echo "==> Done"
    ls -lh "$DIST"
    exit 0
fi

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
write_launcher midiplayer-cli MidiPlayerCLI

cat > "$BUNDLE/README.txt" <<EOF
MidiPlayer ${VERSION} (Linux ${ARCH})

Run:
  ./bin/midiplayer        GUI
  ./bin/midiplayer-cli    text mode (optional argument: MIDI file, type "help" for the commands)

A Java runtime is included, Java does not need to be installed.

Requirements on the target machine:
  - Linux with glibc 2.17 or newer (RHEL/CentOS 7+, Debian 8+, Ubuntu 14.04+,
    Fedora, openSUSE, Arch, Raspberry Pi OS 64-bit, ...). Alpine (musl) is not supported
  - aplaymidi (package alsa-utils), e.g.  sudo apt install alsa-utils
  - a desktop session (X11 or XWayland) for the GUI
  - a MIDI output: a hardware synth, or a software synth such as
    TiMidity++ or FluidSynth running as an ALSA sequencer client
EOF

tar -C "$BUILD" -czf "$DIST/$NAME.tar.gz" "$NAME"

echo "==> Done"
ls -lh "$DIST"
