import java.io.File;
import java.util.List;
import java.util.Scanner;
import java.util.prefs.Preferences;

// Text mode of the MIDI player, with the same features as the GUI.
// Uses the same backend as the GUI (aplaymidi on Linux, Java Sound on Windows) and the same
// saved settings (last folder, last port).
//
// Usage: MidiPlayerCLI [midi file]
public class MidiPlayerCLI {

    private static final String OPTIONS = "load / play / stop / loop / port / status / help / exit";

    private final MidiBackend player = MidiBackend.create(null);
    private final Preferences prefs = MidiBackend.prefs();
    private final Scanner scanner = new Scanner(System.in);
    private String midiFile;
    private double length = 0;
    private long playStart; // Time when play was pressed, for the elapsed time

    public static void main(String[] args) {
        new MidiPlayerCLI().run(args);
    }

    private void run(String[] args) {
        if (MidiBackend.useAplay() && !MidiPlayerAplay.isAplaymidiAvailable()) {
            System.out.println("aplaymidi not found. Please install alsa-utils (e.g. sudo apt install alsa-utils).");
        }
        selectInitialPort();

        if (args.length > 0) {
            load(args[0]);
        } else {
            String path = prompt("Please enter the path to the MIDI file" + lastDirHint() + ": ");
            if (path == null) {
                exit();
                return;
            }
            if (!path.trim().isEmpty()) load(path);
        }

        System.out.println("Available options: " + OPTIONS);
        while (true) {
            String line = prompt("Please input an option: ");
            if (line == null) { // End of input, e.g. Ctrl+D or the end of a pipe
                System.out.println();
                exit();
                return;
            }
            String[] parts = line.trim().split("\\s+", 2);
            String command = parts[0].toLowerCase();
            String argument = parts.length > 1 ? parts[1] : null;

            switch (command) {
                case "":
                    break;
                case "load":
                    if (argument == null) {
                        argument = prompt("Please enter the path to the MIDI file" + lastDirHint() + ": ");
                        if (argument == null) {
                            exit();
                            return;
                        }
                    }
                    if (!argument.trim().isEmpty()) load(argument);
                    break;
                case "play":
                    play();
                    break;
                case "stop":
                    player.stop();
                    break;
                case "loop":
                    player.setLoop(!player.isLoop()); // stop turns loop off, so toggle the real state
                    break;
                case "port":
                    if (!port(argument)) {
                        exit();
                        return;
                    }
                    break;
                case "status":
                    status();
                    break;
                case "help":
                    help();
                    break;
                case "exit":
                case "quit":
                    exit();
                    return;
                default:
                    System.out.println("Unknown option. Available options: " + OPTIONS);
            }
        }
    }

    // Read a line, null at the end of input
    private String prompt(String text) {
        System.out.print(text);
        System.out.flush();
        return scanner.hasNextLine() ? scanner.nextLine() : null;
    }

    private String lastDirHint() {
        String lastDir = prefs.get("lastDir", null);
        return lastDir == null ? "" : " (last folder: " + lastDir + ")";
    }

    // A relative path that does not exist in the current folder is looked up in the last used folder
    private void load(String path) {
        File file = new File(MidiUtils.trimPath(path));
        String lastDir = prefs.get("lastDir", null);
        if (!file.exists() && !file.isAbsolute() && lastDir != null && new File(lastDir, file.getPath()).exists()) {
            file = new File(lastDir, file.getPath());
        }
        if (!file.isFile()) {
            System.out.println("File not found: " + file.getPath());
            return;
        }
        double newLength = MidiUtils.getMidiLength(file.getAbsolutePath());
        if (newLength < 0) {
            System.out.println("Not a valid MIDI file: " + file.getPath());
            return;
        }

        if (player.isPlaying()) player.stop();
        midiFile = file.getAbsolutePath();
        length = newLength;
        player.load(midiFile);
        if (file.getAbsoluteFile().getParent() != null) prefs.put("lastDir", file.getAbsoluteFile().getParent());
    }

    private void play() {
        if (midiFile == null) {
            System.out.println("No file loaded, use \"load\" first");
            return;
        }
        if (player.isPlaying()) {
            System.out.println("Alredy playing");
            return;
        }
        playStart = System.currentTimeMillis();
        player.play();
    }

    // Use the saved port if it is still available, otherwise the first available port
    private void selectInitialPort() {
        String saved = prefs.get(MidiBackend.portPrefKey(), null);
        List<String[]> ports = MidiBackend.listPorts();
        for (String[] p : ports) {
            if (p[0].equals(saved)) {
                usePort(saved);
                return;
            }
        }
        if (!ports.isEmpty()) usePort(ports.get(0)[0]);
        else System.out.println("No MIDI output port found");
    }

    private void usePort(String port) {
        player.setPort(port);
        prefs.put(MidiBackend.portPrefKey(), port);
    }

    // List the ports and choose one by number (or by its id). Returns false at the end of input
    private boolean port(String choice) {
        List<String[]> ports = MidiBackend.listPorts();
        if (ports.isEmpty()) {
            System.out.println("No MIDI output port found");
            return true;
        }
        String current = player.getPort();
        for (int i = 0; i < ports.size(); i++) {
            String mark = ports.get(i)[0].equals(current) ? " *" : "";
            System.out.println("  " + (i + 1) + ") " + ports.get(i)[1] + mark);
        }
        if (choice == null) {
            choice = prompt("Please enter the port number (current " + (current == null ? "none" : current)
                    + ", empty to keep): ");
            if (choice == null) return false;
        }
        choice = choice.trim();
        if (choice.isEmpty()) return true;

        String selected = null;
        if (choice.matches("\\d+")) {
            int n = Integer.parseInt(choice);
            if (n >= 1 && n <= ports.size()) selected = ports.get(n - 1)[0];
        }
        for (String[] p : ports) {
            if (p[0].equals(choice)) selected = p[0];
        }
        if (selected == null) {
            System.out.println("Invalid port: " + choice);
        } else {
            usePort(selected);
            if (player.isPlaying()) System.out.println("The new port is used from the next play");
        }
        return true;
    }

    private void status() {
        System.out.println("File:    " + (midiFile == null ? "(none)" : midiFile));
        String time = MidiUtils.timeSeparation(0);
        if (player.isPlaying() && length > 0) {
            double elapsed = (System.currentTimeMillis() - playStart) / 1000.0;
            elapsed = player.isLoop() ? elapsed % length : Math.min(elapsed, length);
            time = MidiUtils.timeSeparation(elapsed);
        }
        System.out.println("Time:    " + time + "/" + MidiUtils.timeSeparation(Math.max(length, 0)));
        System.out.println("State:   " + (player.isPlaying() ? "playing" : "stopped"));
        System.out.println("Loop:    " + (player.isLoop() ? "on" : "off"));
        System.out.println("Port:    " + (player.getPort() == null ? "(none)" : player.getPort()));
        System.out.println("Backend: " + (MidiBackend.useAplay() ? "aplaymidi" : "Java Sound"));
    }

    private void help() {
        System.out.println("  load [file]   load a MIDI file (relative paths are also looked up in the last folder)");
        System.out.println("  play          play the loaded file (GM/GS/XG reset before playing)");
        System.out.println("  stop          stop playing (GM/GS/XG reset after stopping)");
        System.out.println("  loop          turn repeat on / off");
        System.out.println("  port [n]      list the MIDI output ports and choose one by number");
        System.out.println("  status        show file, play time, state, loop and port");
        System.out.println("  exit          stop playing and quit");
    }

    private void exit() {
        if (player.isPlaying()) player.stop(); // stop also turns loop off
        scanner.close();
    }
}
