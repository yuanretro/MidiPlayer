import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class MidiPlayerGUI {

    private MidiPlayerAplay player;
    private boolean loopMode = false;

    public MidiPlayerGUI() {
        JFrame frame = new JFrame("MIDI Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 200);
        frame.setLayout(new BorderLayout());

        // Display file name and length
        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        JLabel fileLabel = new JLabel("No file loaded");
        JLabel lengthLabel = new JLabel("Length: 0");
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
                else player.load(file.getAbsolutePath());
                fileLabel.setText("Loaded: " + file.getName());
                double length = MidiUtils.getMidiLength(file.getAbsolutePath());
                lengthLabel.setText("Length: " + MidiUtils.timeSeparation(length));
            }
        });

        playButton.addActionListener(e -> {
            if (player != null) player.play();
        });

        stopButton.addActionListener(e -> {
            if (player != null) player.stop();
        });

        loopButton.addActionListener(e -> {
            loopMode = !loopMode;
            if (player != null) player.setLoop(loopMode);
        });

        exitButton.addActionListener(e -> {
            if (player != null) player.stop();
            frame.dispose();
        });

        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MidiPlayerGUI::new);
    }
}