import javax.sound.midi.*;
import java.io.File;
import java.util.Scanner;

public class MidiPlayer {

    private Sequencer sequencer;
    private Synthesizer synth;

    public MidiPlayer(String filePath) {
        try {
            // 1. 获取 Sequencer，但不绑定默认音源
            sequencer = MidiSystem.getSequencer(false);
            sequencer.open();

            // 2. 获取 Synthesizer 并打开
            synth = MidiSystem.getSynthesizer();
            synth.open();

            // 3. 连接 Sequencer 输出到 Synthesizer
            sequencer.getTransmitter().setReceiver(synth.getReceiver());

            // 4. 加载 MIDI 文件
            Sequence sequence = MidiSystem.getSequence(new File(filePath));
            sequencer.setSequence(sequence);

            System.out.println("MIDI 文件加载成功！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 播放
    public void play() {
        if (sequencer != null && !sequencer.isRunning()) {
            sequencer.start();
            System.out.println("播放中...");
        }
    }

    // 暂停
    public void pause() {
        if (sequencer != null && sequencer.isRunning()) {
            sequencer.stop();
            System.out.println("已暂停");
        }
    }

    // 停止
    public void stop() {
        if (sequencer != null) {
            sequencer.stop();
            sequencer.setTickPosition(0);
            System.out.println("已停止");
        }
    }

    // 循环播放
    public void setLoop(boolean loop) {
        if (sequencer != null) {
            if (loop) {
                sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                System.out.println("已设置循环播放");
            } else {
                sequencer.setLoopCount(0);
                System.out.println("已取消循环播放");
            }
        }
    }

    // 关闭
    public void close() {
        try {
            if (sequencer != null) sequencer.close();
            if (synth != null) synth.close();
            System.out.println("播放器已关闭");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入 MIDI 文件路径：");
        String path = scanner.nextLine();

        MidiPlayer player = new MidiPlayer(path);

        System.out.println("操作命令：play / pause / stop / loop / exit");
        boolean loopMode = false;

        while (true) {
            System.out.print("输入命令：");
            String command = scanner.nextLine().trim().toLowerCase();

            switch (command) {
                case "play":
                    player.play();
                    break;
                case "pause":
                    player.pause();
                    break;
                case "stop":
                    player.stop();
                    break;
                case "loop":
                    loopMode = !loopMode;
                    player.setLoop(loopMode);
                    break;
                case "exit":
                    player.close();
                    scanner.close();
                    return;
                default:
                    System.out.println("未知命令，请输入 play / pause / stop / loop / exit");
            }
        }
    }
}