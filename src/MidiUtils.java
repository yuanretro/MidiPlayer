import javax.sound.midi.*;
import java.io.File;

public class MidiUtils {

    /**
     * 获取 MIDI 文件长度（秒）
     * @param midiFilePath MIDI 文件路径
     * @return 文件长度（秒），读取失败返回 -1
     */
    public static double getMidiLength(String midiFilePath) {
        try {
            File midiFile = new File(midiFilePath);
            if (!midiFile.exists() || !midiFile.isFile()) {
                System.out.println("文件不存在或不是有效 MIDI 文件！");
                return -1;
            }

            Sequence sequence = MidiSystem.getSequence(midiFile);
            long microseconds = sequence.getMicrosecondLength();
            return microseconds / 1_000_000.0;  // 转换为秒
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    // 测试用例
    public static void main(String[] args) {
        String path = "example.mid";  // 替换成你的 MIDI 文件路径
        double length = getMidiLength(path);
        System.out.println("MIDI 文件长度：" + length + " 秒");
    }
}