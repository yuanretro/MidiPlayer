import javax.sound.midi.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MidiPlayerAplay {

    private static final String APLAYMIDI = "/usr/bin/aplaymidi";

    // GM System On / GS Reset / XG System On
    private static final byte[] GM_RESET = {(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7};
    private static final byte[] GS_RESET = {(byte) 0xF0, 0x41, 0x10, 0x42, 0x12, 0x40, 0x00, 0x7F, 0x00, 0x41, (byte) 0xF7};
    private static final byte[] XG_RESET = {(byte) 0xF0, 0x43, 0x10, 0x4C, 0x00, 0x00, 0x7E, 0x00, (byte) 0xF7};

    private volatile Process process;
    private volatile boolean stopped = true;
    private int generation = 0; // Incremented on each play/stop, lets old playing threads know they are outdated
    private volatile String playingPort;
    private String midiFile;
    private volatile String port; // ALSA port, e.g. "128:0". If not set, the first available port is used
    public volatile boolean loop = false;

    public MidiPlayerAplay(String midiFile) {
        this.midiFile = midiFile;
    }

    // This method plays the MIDI file
    public synchronized void play() {
        if (!stopped) {
            System.out.println("Alredy playing");
            return;
        }

        if (port == null) {
            List<String[]> ports = listPorts();
            if (ports.isEmpty()) {
                System.out.println("No MIDI output port found");
                return;
            }
            setPort(ports.get(0)[0]);
        }

        stopped = false;
        int session = ++generation;
        String playPort = port;
        playingPort = playPort;
        String file = new File(midiFile).getAbsolutePath();
        new Thread(() -> {
            try {
                sendReset(playPort); // GM/GS/XG reset before playing
                Process p;
                do {
                    synchronized (this) {
                        if (session != generation) return; // stopped meanwhile
                        ProcessBuilder pb = new ProcessBuilder(APLAYMIDI, "-p", playPort, file);
                        pb.inheritIO();
                        p = pb.start();
                        process = p;
                    }
                    p.waitFor(); // Wait until the playing is complete
                } while (loop && session == generation);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                boolean finished;
                synchronized (this) {
                    finished = session == generation;
                    if (finished) stopped = true; // finished by itself
                }
                if (finished) sendReset(playPort); // GM/GS/XG reset after playing ends
            }
        }).start();

        System.out.println("Playing...");
    }

    // Method for loading the MIDI file
    public void load(String newFile) {
        this.midiFile = newFile;
        System.out.println("Loaded file: " + newFile);
        double length = MidiUtils.getMidiLength(newFile);
        System.out.println("File length: " + MidiUtils.timeSeparation(length));
    }

    // Method for stop playing
    public void stop() {
        Process p;
        synchronized (this) {
            if (stopped) {
                System.out.println("Not currently playing");
                return;
            }
            stopped = true;
            generation++;
            setLoop(false);
            p = process;
        }
        if (p != null && p.isAlive()) {
            p.destroy();
            try {
                p.waitFor(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        sendReset(playingPort); // GM/GS/XG reset after stopping, silences hanging notes
        System.out.println("Playing stopped");
    }

    public boolean isPlaying() {
        return !stopped;
    }

    // Set the ALSA output port, e.g. "128:0". Takes effect on the next play.
    public void setPort(String port) {
        this.port = port;
        System.out.println("Output port: " + port);
    }

    public String getPort() {
        return port;
    }

    // List available output ports using "aplaymidi -l". Each entry is {port, client name, port name}.
    public static List<String[]> listPorts() {
        List<String[]> ports = new ArrayList<>();
        Pattern pattern = Pattern.compile("^\\s*(\\d+:\\d+)\\s+(.*?)\\s{2,}(.*?)\\s*$");
        try {
            Process p = new ProcessBuilder(APLAYMIDI, "-l").redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = pattern.matcher(line);
                    if (m.matches()) ports.add(new String[]{m.group(1), m.group(2), m.group(3)});
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return ports;
    }

    // Send GM, GS and XG reset SysEx messages to the given port by playing a short temporary MIDI file
    public static void sendReset(String port) {
        File tmp = null;
        try {
            Sequence seq = new Sequence(Sequence.PPQ, 480); // 120 BPM default, about 1 ms per tick
            Track track = seq.createTrack();
            track.add(sysex(GM_RESET, 0));
            track.add(sysex(GS_RESET, 100));
            track.add(sysex(XG_RESET, 200));
            // All Sound Off / Reset All Controllers / All Notes Off on every channel
            for (int ch = 0; ch < 16; ch++) {
                track.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 120, 0), 300));
                track.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 121, 0), 300));
                track.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 123, 0), 300));
            }
            // Leave some time for the device to finish the reset
            track.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), 400));

            tmp = File.createTempFile("midireset", ".mid");
            MidiSystem.write(seq, 0, tmp);

            Process p = new ProcessBuilder(APLAYMIDI, "-p", port, tmp.getAbsolutePath()).inheritIO().start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroy();
            System.out.println("GM/GS/XG reset sent to " + port);
        } catch (InvalidMidiDataException | IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static MidiEvent sysex(byte[] data, long tick) throws InvalidMidiDataException {
        SysexMessage msg = new SysexMessage();
        msg.setMessage(data, data.length);
        return new MidiEvent(msg, tick);
    }

    // Set repeat play
    public void setLoop(boolean loop) {
        this.loop = loop;
        System.out.println("Loop " + (loop ? "is enabled" : "is disabled"));
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Please enter the path to the MIDI file: ");
        String path = MidiUtils.trimPath(scanner.nextLine());

        MidiPlayerAplay player = new MidiPlayerAplay(path);
        System.out.println("Loaded file: " + path);

        double length = MidiUtils.getMidiLength(path);
        System.out.println("File length: " + MidiUtils.timeSeparation(length));

        System.out.println("Available options: load / play / stop / loop / port / exit");
        boolean loopMode = false;

        while (true) {
            System.out.print("Please input an option: ");
            String command = scanner.nextLine().trim().toLowerCase();

            switch (command) {
                case "load":
                    System.out.print("Please enter the path to the MIDI file: ");
                    String newPath = MidiUtils.trimPath(scanner.nextLine());
                    player.load(newPath);
                    break;
                case "play":
                    player.play();
                    break;
                case "stop":
                    player.stop();
                    break;
                case "loop":
                    loopMode = !loopMode;
                    player.setLoop(loopMode);
                    break;
                case "port":
                    for (String[] info : listPorts()) {
                        System.out.println(info[0] + "\t" + info[1] + " - " + info[2]);
                    }
                    System.out.print("Please enter the port (current " + (player.getPort() == null ? "auto" : player.getPort()) + ", empty to keep): ");
                    String newPort = scanner.nextLine().trim();
                    if (!newPort.isEmpty()) player.setPort(newPort);
                    break;
                case "exit":
                    player.stop();
                    scanner.close();
                    return;
                default:
                    System.out.println("Unknown option. Available options: load / play / stop / loop / port / exit");
            }
        }
    }
}