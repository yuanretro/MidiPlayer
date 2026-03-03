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
                System.out.println("File not found or not valid MIDI file!");
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

    public static String timeSeparation (double second) {
        int minutes = (int) (second / 60);
        int seconds = (int) (second - (double) (minutes * 60));
        return String.format("%02d:%02d", minutes, seconds);
    }

    // 去除文件地址两边的单引号或者双引号
    public static String trimPath (String path) {
        if (path == null) return null;

        path = path.trim(); // 去掉首尾空格

        // 去掉首尾单引号或双引号
        if ((path.startsWith("'") && path.endsWith("'")) ||
                (path.startsWith("\"") && path.endsWith("\""))) {
            path = path.substring(1, path.length() - 1);
        }

        return path;
    }

    public static void main(String[] args) {}
}