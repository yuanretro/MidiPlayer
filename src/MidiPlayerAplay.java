import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class MidiPlayerAplay {

    private Process process;
    private String midiFile;
    private int clientId = 128; // QSynth 的 ALSA client ID
    private boolean loop = false;

    public MidiPlayerAplay(String midiFile) {
        this.midiFile = midiFile;
    }

    // 播放 MIDI
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
                    process.waitFor(); // 等待播放结束
                } while (loop);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        System.out.println("Playing...");
    }

    // 加载文件
    public void load(String newFile) {
        this.midiFile = newFile;
        System.out.println("Loaded file: " + newFile);
        double length = MidiUtils.getMidiLength(newFile);
        System.out.println("File length: " + MidiUtils.timeSeparation(length));
    }

    // 停止播放
    public void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
            System.out.println("Playing stopped");
        } else {
            System.out.println("Not currently playing");
        }
    }

    // 设置循环播放
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