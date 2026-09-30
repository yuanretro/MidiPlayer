import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.prefs.Preferences;

public class MidiPlayerGUI {
    private MidiPlayerAplay player;
    private boolean loopMode = false;
    private Timer timer;
    private int timeElapsed = 0;
    private int length1 = 0;
    private final JFileChooser chooser = new JFileChooser();
    private final Preferences prefs = Preferences.userNodeForPackage(MidiPlayerGUI.class);
    private String selectedPort;
    private final DefaultComboBoxModel<String> portModel = new DefaultComboBoxModel<>();

    public MidiPlayerGUI() {
        // 启动时恢复上次目录
        String lastDir = prefs.get("lastDir", null);
        if (lastDir != null) chooser.setCurrentDirectory(new File(lastDir));

        // 启动时恢复上次选择的端口
        selectedPort = prefs.get("port", "32:0");

        JFrame frame = new JFrame("MIDI Player");
        // 关闭窗口时也走 shutdown，保证发送复位信息
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown(frame);
            }
        });
        frame.setSize(560, 200);
        frame.setLayout(new BorderLayout());

        // 显示文件名和长度
        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        JLabel fileLabel = new JLabel("No file loaded");
        JLabel lengthLabel = new JLabel("Length: 00:00/00:00");
        infoPanel.add(fileLabel);
        infoPanel.add(lengthLabel);
        frame.add(infoPanel, BorderLayout.NORTH);

        // 按钮
        JPanel buttonPanel = new JPanel();
        JButton loadButton = new JButton("Load");
        JButton playButton = new JButton("Play");
        JButton stopButton = new JButton("Stop");
        JButton loopButton = new JButton("Loop");
        JButton exitButton = new JButton("Exit");
        buttonPanel.add(loadButton);
        buttonPanel.add(playButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(loopButton);
        buttonPanel.add(exitButton);
        frame.add(buttonPanel, BorderLayout.CENTER);

        // 端口选择
        JPanel portPanel = new JPanel(new BorderLayout(5, 0));
        portPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        JComboBox<String> portBox = new JComboBox<>(portModel);
        portBox.setToolTipText("aplaymidi output port (takes effect on next play)");
        JButton refreshButton = new JButton("Refresh");
        portPanel.add(new JLabel("Port:"), BorderLayout.WEST);
        portPanel.add(portBox, BorderLayout.CENTER);
        portPanel.add(refreshButton, BorderLayout.EAST);
        frame.add(portPanel, BorderLayout.SOUTH);
        refreshPorts();

        // 事件处理
        loadButton.addActionListener(e -> {
            int result = chooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();

                // 保存当前目录到 Preferences
                prefs.put("lastDir", file.getParent());

                if (player == null) {
                    player = new MidiPlayerAplay(file.getAbsolutePath());
                    player.setPort(selectedPort);
                } else {
                    stopInBackground();
                    player.load(file.getAbsolutePath());
                }
                fileLabel.setText("Loaded: " + file.getName());
                double length = MidiUtils.getMidiLength(file.getAbsolutePath());
                lengthLabel.setText("Length: 00:00/" + MidiUtils.timeSeparation(length));
                length1 = (int) length;
                timeElapsed = 0;
                if (timer != null) timer.stop();
                timer = new Timer(1000, event -> updateTime(lengthLabel));
            }
        });

        playButton.addActionListener(e -> {
            if (player != null) player.play();
            if (timer != null) {
                timeElapsed = 0;
                lengthLabel.setText("Length: 00:00/" + MidiUtils.timeSeparation(length1));
                timer.start();
            }
        });

        stopButton.addActionListener(e -> {
            stopInBackground();
            if (timer != null) timer.stop();
        });

        loopButton.addActionListener(e -> {
            loopMode = !loopMode;
            if (player != null) player.setLoop(loopMode);
        });

        exitButton.addActionListener(e -> shutdown(frame));

        refreshButton.addActionListener(e -> refreshPorts());

        portBox.addActionListener(e -> {
            Object item = portBox.getSelectedItem();
            if (item == null) return;
            selectedPort = item.toString().split("\\s+", 2)[0];
            prefs.put("port", selectedPort);
            if (player != null) player.setPort(selectedPort);
        });

        frame.setVisible(true);
    }

    // 通过 aplaymidi -l 重新获取可用端口列表
    private void refreshPorts() {
        String keep = selectedPort;
        portModel.removeAllElements();
        String selectedItem = null;
        for (String[] info : MidiPlayerAplay.listPorts()) {
            String item = info[0] + "   " + info[1] + " - " + info[2];
            portModel.addElement(item);
            if (info[0].equals(keep)) selectedItem = item;
        }
        // 上次使用的端口当前不可用时仍然保留显示
        if (selectedItem == null) {
            selectedItem = keep + "   (not available)";
            portModel.insertElementAt(selectedItem, 0);
        }
        portModel.setSelectedItem(selectedItem);
    }

    // 停止播放和复位需要一些时间，放到后台线程避免界面卡住
    private void stopInBackground() {
        if (player == null) return;
        MidiPlayerAplay p = player;
        new Thread(p::stop).start();
    }

    private void updateTime(JLabel lengthLabel1) {
        if (timeElapsed < length1) {
            timeElapsed++;
            lengthLabel1.setText("Length: " + MidiUtils.timeSeparation(timeElapsed) + "/" + MidiUtils.timeSeparation(length1));
        } else {
            if (player.loop) {
                timeElapsed = 0;
                lengthLabel1.setText("Length: 00:00/" + MidiUtils.timeSeparation(length1));
            } else {
                timer.stop();
            }
        }
    }

    private void shutdown(JFrame frame1) {
        if (player != null) {
            player.setLoop(false);
            player.stop();
        }
        if (timer != null) timer.stop();
        frame1.dispose();
        System.exit(0);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MidiPlayerGUI::new);
    }
}