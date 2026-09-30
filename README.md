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
./MidiPlayer-<version>-linux-x86_64/bin/midiplayer       # GUI
./MidiPlayer-<version>-linux-x86_64/bin/midiplayer-cli   # text mode
```

## Requirements

Linux:

- `aplaymidi` from `alsa-utils` (e.g. `sudo apt install alsa-utils`)
- A MIDI output: a hardware synth, or a software synth such as TiMidity++ or FluidSynth

Windows:

- Nothing extra. Choose the device in the Port list, press Refresh after connecting a device.

## Build

Requires JDK 11+ (JDK 14+ on Windows for `jpackage`). On Windows run it from Git Bash.

```sh
./build.sh            # output in dist/
```

Pushing a tag like `v1.0.0` (or running the "Build and Release" workflow manually with a version) builds the Linux (x86_64, aarch64) and Windows (x86_64) bundles with GitHub Actions and
publishes them as a release.
