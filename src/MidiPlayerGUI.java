import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class MidiPlayerGUI {
    private MidiBackend player;
    private final boolean useAplay = MidiBackend.useAplay();
    private boolean loopMode = false;
    private Timer timer;
    private int timeElapsed = 0;
    private int length1 = 0;
    private final JFileChooser chooser = new JFileChooser();
    private final Preferences prefs = MidiBackend.prefs();
    private String selectedPort;
    private boolean updatingPorts = false;
    private final DefaultComboBoxModel<String> portModel = new DefaultComboBoxModel<>();
    private final List<String> portIds = new ArrayList<>(); // Port id of each item in portModel

    public MidiPlayerGUI() {
        // 启动时恢复上次目录
        String lastDir = prefs.get("lastDir", null);
        if (lastDir != null) chooser.setCurrentDirectory(new File(lastDir));

        // 启动时恢复上次选择的端口（没有的话在 refreshPorts 中自动选择第一个可用端口）
        // Linux (aplaymidi) 和 Windows (Java Sound) 的端口格式不同，分开保存
        selectedPort = prefs.get(MidiBackend.portPrefKey(), null);

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
        portBox.setToolTipText((useAplay ? "aplaymidi output port" : "MIDI output device") + " (takes effect on next play)");
        JButton refreshButton = new JButton("Refresh");
        portPanel.add(new JLabel("Port:"), BorderLayout.WEST);
        portPanel.add(portBox, BorderLayout.CENTER);
        portPanel.add(refreshButton, BorderLayout.EAST);
        frame.add(portPanel, BorderLayout.SOUTH);
        if (useAplay && !MidiPlayerAplay.isAplaymidiAvailable()) {
            JOptionPane.showMessageDialog(frame,
                    "aplaymidi not found.\nPlease install alsa-utils (e.g. sudo apt install alsa-utils).",
                    "MIDI Player", JOptionPane.WARNING_MESSAGE);
        }
        refreshPorts();

        // 事件处理
        loadButton.addActionListener(e -> {
            int result = chooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();

                // 保存当前目录到 Preferences
                prefs.put("lastDir", file.getParent());

                if (player == null) {
                    player = MidiBackend.create(file.getAbsolutePath());
                    if (selectedPort != null) player.setPort(selectedPort);
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
            if (updatingPorts) return;
            int index = portBox.getSelectedIndex();
            if (index < 0 || index >= portIds.size()) return; // 占位项，例如没有可用端口
            usePort(portIds.get(index));
        });

        frame.setVisible(true);
    }

    // 重新获取可用端口列表（Linux 通过 aplaymidi -l，Windows 通过 Java Sound）
    // 上次使用的端口仍然可用时保持选中，否则自动选择第一个可用端口
    private void refreshPorts() {
        updatingPorts = true;
        try {
            portModel.removeAllElements();
            portIds.clear();
            int selectedIndex = -1;
            for (String[] info : MidiBackend.listPorts()) {
                if (info[0].equals(selectedPort)) selectedIndex = portIds.size();
                portIds.add(info[0]);
                portModel.addElement(info[1]);
            }
            if (portIds.isEmpty()) {
                portModel.addElement("(no MIDI output port found)");
                return;
            }
            if (selectedIndex < 0) selectedIndex = 0;
            portModel.setSelectedItem(portModel.getElementAt(selectedIndex));
            usePort(portIds.get(selectedIndex));
        } finally {
            updatingPorts = false;
        }
    }

    private void usePort(String port) {
        selectedPort = port;
        prefs.put(MidiBackend.portPrefKey(), port);
        if (player != null) player.setPort(port);
    }

    // 停止播放和复位需要一些时间，放到后台线程避免界面卡住
    private void stopInBackground() {
        if (player == null) return;
        MidiBackend p = player;
        new Thread(p::stop).start();
    }

    private void updateTime(JLabel lengthLabel1) {
        if (timeElapsed < length1) {
            timeElapsed++;
            lengthLabel1.setText("Length: " + MidiUtils.timeSeparation(timeElapsed) + "/" + MidiUtils.timeSeparation(length1));
        } else {
            if (player.isLoop()) {
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