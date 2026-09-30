# MidiPlayer

A simple MIDI player for Linux and Windows written in Java. It lets you choose the MIDI
output port and sends GM/GS/XG reset messages before and after playback.

- **Linux**: plays through `aplaymidi` to any ALSA sequencer port (hardware synths,
  TiMidity++, FluidSynth, ...).
- **Windows**: plays through the Java Sound API to any MIDI output device (hardware synths
  and USB MIDI interfaces, Microsoft GS Wavetable Synth, loopMIDI / VirtualMIDISynth, ...).

The backend is chosen from the operating system. It can be forced with
`-Dmidiplayer.backend=aplay` or `-Dmidiplayer.backend=javasound`.

## Download

Get the latest build from the [Releases](https://github.com/yuanretro/MidiPlayer/releases) page:

- `MidiPlayer-<version>-linux-x86_64.tar.gz` / `...-linux-aarch64.tar.gz`: self-contained, Java included
- `MidiPlayer-<version>-windows-x86_64.zip`: self-contained, Java included, unzip and run `MidiPlayer.exe`
- `MidiPlayer.jar`: needs Java 8+ installed, run with `java -jar MidiPlayer.jar`

```sh
tar xzf MidiPlayer-<version>-linux-x86_64.tar.gz
./MidiPlayer-<version>-linux-x86_64/bin/midiplayer                 # GUI
./MidiPlayer-<version>-linux-x86_64/bin/midiplayer-cli [file.mid]  # text mode
```

On Windows, unzip and run `MidiPlayer.exe` (GUI) or `MidiPlayer-cli.exe` (text mode).
With the jar: `java -jar MidiPlayer.jar` (GUI) or `java -cp MidiPlayer.jar MidiPlayerCLI` (text mode).

## Text mode

The text mode has the same features as the GUI, on Linux and Windows, and shares its saved
settings (last port, last folder).

| Command | |
| --- | --- |
| `load [file]` | Load a MIDI file. A relative path is also looked up in the last used folder |
| `play` | Play (GM/GS/XG reset before playing) |
| `stop` | Stop (GM/GS/XG reset after stopping) |
| `loop` | Turn repeat on / off |
| `port [n]` | List the MIDI output ports and choose one by number |
| `status` | Show file, play time, state, loop and port |
| `help` | Show the commands |
| `exit` | Stop playing and quit (also at the end of input, e.g. Ctrl+D) |

## System requirements

### Windows (`MidiPlayer-<version>-windows-x86_64.zip`)

| | Minimum |
| --- | --- |
| OS | Windows 10 64-bit, Windows 11, Windows Server 2016 or newer |
| CPU | x86_64 (Intel/AMD 64-bit) |
| Java | Not needed, a Java 21 runtime is included |
| MIDI output | Any MIDI output device: hardware synthesizer / USB MIDI interface, Microsoft GS Wavetable Synth, virtual ports (loopMIDI, VirtualMIDISynth), or the built-in Java synthesizer |

- Windows 7 / 8 / 8.1 and 32-bit Windows are not supported by the bundled Java 21 runtime.
  On those systems use `MidiPlayer.jar` with Java 8 or newer installed.
- Windows on ARM (Windows 11 ARM64) is not tested; it may work through x64 emulation.

### Linux (`MidiPlayer-<version>-linux-x86_64.tar.gz` / `...-linux-aarch64.tar.gz`)

| | Minimum |
| --- | --- |
| C library | glibc 2.17 or newer, e.g. RHEL / CentOS / Rocky / Alma 7+, Debian 8+, Ubuntu 14.04+, Fedora, openSUSE, Arch, Linux Mint, Raspberry Pi OS 64-bit |
| CPU | x86_64 (Intel/AMD 64-bit) or aarch64 (ARM 64-bit, e.g. Raspberry Pi 3/4/5 with a 64-bit OS) |
| Java | Not needed, a Java 21 runtime is included |
| Packages | `alsa-utils` (provides `aplaymidi`, e.g. `sudo apt install alsa-utils` / `sudo dnf install alsa-utils`) |
| GUI | An X11 or Wayland (XWayland) desktop with the usual X11 libraries (`libX11`, `libXext`, `libXi`, `libXrender`, `libXtst`), installed on every desktop distribution. The text mode (`midiplayer-cli`) does not need a desktop |
| MIDI output | An ALSA sequencer port: hardware synthesizer / USB MIDI interface, or a software synthesizer such as TiMidity++ or FluidSynth |

- musl-based distributions (Alpine Linux) and 32-bit systems (i386, armhf such as 32-bit
  Raspberry Pi OS) are not supported by the bundles. On those systems use `MidiPlayer.jar`
  with Java 8 or newer installed.

### `MidiPlayer.jar`

- Java 8 or newer, on any system Java runs on (including 32-bit and older systems).
- Linux additionally needs `alsa-utils`, as above.
- Other systems such as macOS use the Java Sound backend like Windows, but are not tested.

## Build

Requires JDK 11+ (JDK 14+ on Windows for `jpackage`). On Windows run it from Git Bash.

```sh
./build.sh            # output in dist/
```

Pushing a tag like `v1.0.0` (or running the "Build and Release" workflow manually with a version) builds the Linux (x86_64, aarch64) and Windows (x86_64) bundles with GitHub Actions and
publishes them as a release.
