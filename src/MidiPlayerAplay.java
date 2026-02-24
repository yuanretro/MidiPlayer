import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class MidiPlayerAplay {

    private Process process;
    private String MidiFile;
    private int ClientId = 128; // QSynth 的 ALSA client ID
    private boolean loop = false;

    public MidiPlayerAplay(String MidiFile) {
        this.MidiFile = MidiFile;
    }

    // 播放 MIDI
    public void play() {
        if (process != null && process.isAlive()) {
            System.out.println("已经在播放中");
            return;
        }

        new Thread(() -> {
            try {
                do {
                    ProcessBuilder pb = new ProcessBuilder(
                            "/usr/bin/aplaymidi", "-p", ClientId + ":0", new File(MidiFile).getAbsolutePath()
                    );
                    pb.inheritIO();
                    process = pb.start();
                    process.waitFor(); // 等待播放结束
                } while (loop);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        System.out.println("播放中...");
    }

    // 去除文件地址两边的单引号或者双引号
    public static String TrimPath (String path) {
        if (path == null) return null;

        path = path.trim(); // 去掉首尾空格

        // 去掉首尾单引号或双引号
        if ((path.startsWith("'") && path.endsWith("'")) ||
                (path.startsWith("\"") && path.endsWith("\""))) {
            path = path.substring(1, path.length() - 1);
        }

        return path;
    }

    // 加载文件
    public void load(String NewFile) {
        this.MidiFile = NewFile;
        System.out.println("已加载文件：" + NewFile);
        double length = MidiUtils.getMidiLength(NewFile);
        System.out.println("文件长度：" + length + " 秒");
    }

    // 停止播放
    public void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
            System.out.println("已停止播放");
        } else {
            System.out.println("当前没有播放");
        }
    }

    // 设置循环播放
    public void SetLoop(boolean loop) {
        this.loop = loop;
        System.out.println("循环播放 " + (loop ? "已开启" : "已关闭"));
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入 MIDI 文件路径：");
        String path = TrimPath(scanner.nextLine());

        MidiPlayerAplay player = new MidiPlayerAplay(path);
        System.out.println("已加载文件：" + path);

        double length = MidiUtils.getMidiLength(path);
        System.out.println("文件长度：" + length + " 秒");

        System.out.println("操作命令：load / play / stop / loop / exit");
        boolean LoopMode = false;

        while (true) {
            System.out.print("输入命令：");
            String command = scanner.nextLine().trim().toLowerCase();

            switch (command) {
                case "load":
                    System.out.print("请输入 MIDI 文件路径：");
                    String NewPath = TrimPath(scanner.nextLine());
                    player.load(NewPath);
                    break;
                case "play":
                    player.play();
                    break;
                case "stop":
                    player.stop();
                    break;
                case "loop":
                    LoopMode = !LoopMode;
                    player.SetLoop(LoopMode);
                    break;
                case "exit":
                    player.stop();
                    scanner.close();
                    return;
                default:
                    System.out.println("未知命令，请输入 load / play / stop / loop / exit");
            }
        }
    }
}