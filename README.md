# MidiPlayer

A simple MIDI player for Linux written in Java. It plays MIDI files through `aplaymidi`
to any ALSA sequencer port, lets you choose the output port, and sends GM/GS/XG reset
messages before and after playback.

## Download

Get the latest build from the [Releases](https://github.com/yuanretro/MidiPlayer/releases) page:

- `MidiPlayer-<version>-linux-x86_64.tar.gz` / `...-linux-aarch64.tar.gz`: self-contained, Java included
- `MidiPlayer.jar`: needs Java 8+ installed, run with `java -jar MidiPlayer.jar`

```sh
tar xzf MidiPlayer-<version>-linux-x86_64.tar.gz
./MidiPlayer-<version>-linux-x86_64/bin/midiplayer       # GUI
./MidiPlayer-<version>-linux-x86_64/bin/midiplayer-cli   # text mode
```

## Requirements

- `aplaymidi` from `alsa-utils` (e.g. `sudo apt install alsa-utils`)
- A MIDI output: a hardware synth, or a software synth such as TiMidity++ or FluidSynth

## Build

Requires JDK 11+.

```sh
./build.sh            # output in dist/
```

Pushing a tag like `v1.0.0` builds x86_64 and aarch64 bundles with GitHub Actions and
publishes them as a release.
