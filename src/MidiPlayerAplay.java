import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class MidiPlayerAplay {

    private Process process;
    private String midiFile;
    public int clientId = 129; // ALSA Client ID for Qsynth (or FluidSynth), can be altered
    public boolean loop = false;

    public MidiPlayerAplay(String midiFile) {
        this.midiFile = midiFile;
    }

    // This method plays the MIDI file
    public void play() {
        if (process != null && process.isAlive()) {
            System.out.println("Alredy playing");
            return;
        }

        new Thread(() -> {
            try {
                do {
                    ProcessBuilder pb = new ProcessBuilder(
                            "/usr/bin/aplaymidi", "-p", clientId + ":0", new File(midiFile).getAbsolutePath()
                    );
                    pb.inheritIO();
                    process = pb.start();
                    process.waitFor(); // Wait until the playing is complete
                } while (loop);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
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
        if (process != null && process.isAlive()) {
            setLoop(false);
            process.destroy();
            System.out.println("Playing stopped");
        } else {
            System.out.println("Not currently playing");
        }
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

        System.out.println("Available options: load / play / stop / loop / exit");
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
                case "exit":
                    player.stop();
                    scanner.close();
                    return;
                default:
                    System.out.println("Unknown option. Available options: load / play / stop / loop / exit");
            }
        }
    }
}