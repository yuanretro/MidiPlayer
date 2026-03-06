import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class MidiPlayerGUI {

    private MidiPlayerAplay player;
    private boolean loopMode = false;
    private Timer timer;
    private int timeElapsed = 0;
    private int length1 = 0;

    public MidiPlayerGUI() {
        JFrame frame = new JFrame("MIDI Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 200);
        frame.setLayout(new BorderLayout());

        // Display file name and length
        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        JLabel fileLabel = new JLabel("No file loaded");
        JLabel lengthLabel = new JLabel("Length: 00:00/00:00");
        infoPanel.add(fileLabel);
        infoPanel.add(lengthLabel);
        frame.add(infoPanel, BorderLayout.NORTH);

        // Buttons
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

        // Catching events
        loadButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (player == null) player = new MidiPlayerAplay(file.getAbsolutePath());
                else {
                    player.stop();
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
            if (player != null) player.stop();
            if (timer != null) timer.stop();
        });

        loopButton.addActionListener(e -> {
            loopMode = !loopMode;
            if (player != null) player.setLoop(loopMode);
        });

        exitButton.addActionListener(e -> {
            shutdown(frame);
        });

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
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