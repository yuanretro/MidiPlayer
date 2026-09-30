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
      --output "$BUILD/runtime" 2>&1 | grep -v -i "deprecat" || true
test -x "$BUILD/runtime/bin/java"

echo "==> Creating Linux app"
# Native launchers bin/MidiPlayer and bin/MidiPlayer-cli, which file managers run on double-click
APP_VERSION="${VERSION#v}"
[[ "$APP_VERSION" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]] || APP_VERSION=1.0.0
mkdir -p "$BUILD/input"
cp "$DIST/MidiPlayer.jar" "$BUILD/input/"
printf 'main-class=MidiPlayerCLI\n' > "$BUILD/cli-launcher.properties"
jpackage --type app-image --name MidiPlayer --app-version "$APP_VERSION" \
         --input "$BUILD/input" --main-jar MidiPlayer.jar --main-class MidiPlayerGUI \
         --add-launcher MidiPlayer-cli="$BUILD/cli-launcher.properties" \
         --runtime-image "$BUILD/runtime" --dest "$BUILD/app"
test -x "$BUILD/app/MidiPlayer/bin/MidiPlayer"
test -x "$BUILD/app/MidiPlayer/bin/MidiPlayer-cli"
mv "$BUILD/app/MidiPlayer" "$BUNDLE"

# The old script names still work, they start the native launchers
write_launcher() {
    # $1 = script name, $2 = launcher
    cat > "$BUNDLE/bin/$1" <<EOF
#!/bin/sh
# Resolve the real location so the script also works through a symlink
SELF="\$(readlink -f "\$0" 2>/dev/null || echo "\$0")"
exec "\$(dirname "\$SELF")/$2" "\$@"
EOF
    chmod +x "$BUNDLE/bin/$1"
}
write_launcher midiplayer MidiPlayer
write_launcher midiplayer-cli MidiPlayer-cli

# Shortcuts that can be double-clicked in the file manager. %k is the location of the .desktop
# file, so they work wherever the folder is unpacked
write_desktop() {
    # $1 = file name, $2 = title, $3 = launcher, $4 = open in a terminal
    cat > "$BUNDLE/$1" <<EOF
[Desktop Entry]
Type=Application
Name=$2
Comment=MIDI player with port selection and GM/GS/XG reset
Exec=sh -c 'exec "\$(dirname "\$1")/bin/$3"' sh %k
Icon=audio-x-generic
Terminal=$4
Categories=AudioVideo;Audio;Player;
EOF
    chmod +x "$BUNDLE/$1"
}
write_desktop MidiPlayer.desktop "MIDI Player" MidiPlayer false
write_desktop MidiPlayer-cli.desktop "MIDI Player (text mode)" MidiPlayer-cli true

# Adds the two shortcuts to the application menu, with the absolute path of this folder
cat > "$BUNDLE/install-menu.sh" <<'EOF'
#!/bin/sh
# Add MIDI Player to the application menu of the current user.
# Run it again after moving this folder. Remove with: ./install-menu.sh --remove
APP_HOME="$(cd "$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$0")")" && pwd)"
MENU="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
if [ "$1" = "--remove" ]; then
    rm -f "$MENU/midiplayer.desktop" "$MENU/midiplayer-cli.desktop"
    echo "Removed MIDI Player from the application menu"
    exit 0
fi
mkdir -p "$MENU"
for f in MidiPlayer MidiPlayer-cli; do
    target="$MENU/$(echo "$f" | tr 'A-Z' 'a-z').desktop"
    sed "s|^Exec=.*|Exec=\"$APP_HOME/bin/$f\"|" "$APP_HOME/$f.desktop" > "$target"
    chmod +x "$target"
done
echo "Added MIDI Player to the application menu ($MENU)"
EOF
chmod +x "$BUNDLE/install-menu.sh"

cat > "$BUNDLE/README.txt" <<EOF
MidiPlayer ${VERSION} (Linux ${ARCH})

Run:
  bin/MidiPlayer          GUI        (double-click it, or MidiPlayer.desktop, in the file manager)
  bin/MidiPlayer-cli      text mode  (optional argument: MIDI file, type "help" for the commands;
                                      double-click MidiPlayer-cli.desktop to open it in a terminal)
  ./install-menu.sh       add both to the application menu

  bin/midiplayer and bin/midiplayer-cli are kept as aliases for the commands above.
  Some desktops ask to "Allow Launching" / "Trust" a .desktop file the first time.

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
