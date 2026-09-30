import javax.sound.midi.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// Playing backend based on the Java Sound API, used on Windows (and other non-Linux systems).
// It can output to every MIDI output device the system provides: hardware synthesizers and
// USB MIDI interfaces, Microsoft GS Wavetable Synth, virtual ports (loopMIDI, VirtualMIDISynth),
// and the Java built-in software synthesizer.
public class MidiPlayerJavaSound implements MidiBackend {

    // All device operations (open, reset, close) run one after another on this thread, so a new
    // playing never uses a device that an earlier stop is still resetting or closing.
    // The thread ends after being idle for a second, so it does not keep the JVM alive.
    private static final ExecutorService DEVICE_THREAD =
            new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    private String midiFile;
    private volatile String port; // Port id from listPorts(). If not set, the first available port is used
    private volatile boolean loop = false;
    private volatile boolean stopped = true;
    private int generation = 0; // Incremented on each play/stop, lets old playing threads know they are outdated

    // The device and sequencer currently playing, guarded by this
    private MidiDevice device;
    private Sequencer sequencer;

    public MidiPlayerJavaSound(String midiFile) {
        this.midiFile = midiFile;
    }

    public synchronized void play() {
        if (!stopped) {
            System.out.println("Alredy playing");
            return;
        }

        stopped = false;
        int session = ++generation;
        String playPort = port;
        String file = new File(midiFile).getAbsolutePath();
        DEVICE_THREAD.execute(() -> start(session, playPort, file));

        System.out.println("Playing...");
    }

    private void start(int session, String playPort, String file) {
        synchronized (this) {
            if (session != generation) return; // Already stopped before it started
        }
        MidiDevice dev = null;
        Sequencer seq = null;
        try {
            MidiDevice.Info info = findPort(playPort);
            if (info == null) {
                System.out.println("No MIDI output port found");
                finished(session);
                return;
            }
            if (playPort == null) setPort(portId(info));

            dev = MidiSystem.getMidiDevice(info);
            dev.open();
            sendReset(dev.getReceiver()); // GM/GS/XG reset before playing

            seq = MidiSystem.getSequencer(false); // Not connected to the default synthesizer
            seq.open();
            seq.getTransmitter().setReceiver(dev.getReceiver());
            seq.setSequence(MidiSystem.getSequence(new File(file)));
            Sequencer s = seq;
            MidiDevice d = dev;
            seq.addMetaEventListener(meta -> {
                if (meta.getType() == 0x2F) { // End of track
                    Thread t = new Thread(() -> onEnd(session, s, d));
                    t.setDaemon(false); // The sequencer's event thread is a daemon, make sure the reset completes
                    t.start();
                }
            });

            synchronized (this) {
                if (session == generation) {
                    device = dev;
                    sequencer = seq;
                    seq.start();
                    return;
                }
            }
            // Stopped while opening the device
            close(seq, dev);
        } catch (Exception e) {
            e.printStackTrace();
            close(seq, dev);
            finished(session);
        }
    }

    // Called when the sequence has reached its end
    private void onEnd(int session, Sequencer seq, MidiDevice dev) {
        // Wait until the sequencer has really stopped before restarting it
        for (int i = 0; i < 200 && seq.isRunning(); i++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        synchronized (this) {
            if (session != generation) return; // Stopped meanwhile
            if (loop) {
                seq.setTickPosition(0);
                seq.start();
                return;
            }
            // Finished by itself
            stopped = true;
            generation++;
            device = null;
            sequencer = null;
        }
        DEVICE_THREAD.execute(() -> close(seq, dev)); // GM/GS/XG reset after playing ends
    }

    private synchronized void finished(int session) {
        if (session == generation) stopped = true;
    }

    public void load(String newFile) {
        this.midiFile = newFile;
        System.out.println("Loaded file: " + newFile);
        double length = MidiUtils.getMidiLength(newFile);
        System.out.println("File length: " + MidiUtils.timeSeparation(length));
    }

    public void stop() {
        Sequencer seq;
        MidiDevice dev;
        synchronized (this) {
            if (stopped) {
                System.out.println("Not currently playing");
                return;
            }
            stopped = true;
            generation++;
            setLoop(false);
            seq = sequencer;
            dev = device;
            sequencer = null;
            device = null;
        }
        // GM/GS/XG reset after stopping, silences hanging notes. Wait until it is done
        try {
            DEVICE_THREAD.submit(() -> close(seq, dev)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        System.out.println("Playing stopped");
    }

    // Stop the sequencer, send the reset to the device and release it
    private static void close(Sequencer seq, MidiDevice dev) {
        if (seq != null) {
            seq.stop();
            seq.close();
        }
        if (dev != null) {
            try {
                if (dev.isOpen()) sendReset(dev.getReceiver());
            } catch (MidiUnavailableException e) {
                e.printStackTrace();
            }
            dev.close();
        }
    }

    public boolean isPlaying() {
        return !stopped;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
        System.out.println("Loop " + (loop ? "is enabled" : "is disabled"));
    }

    public boolean isLoop() {
        return loop;
    }

    public void setPort(String port) {
        this.port = port;
        System.out.println("Output port: " + port);
    }

    public String getPort() {
        return port;
    }

    // MIDI output devices, hardware/external ports first and software synthesizers last
    private static List<MidiDevice.Info> outputDevices() {
        List<MidiDevice.Info> ports = new ArrayList<>();
        List<MidiDevice.Info> synths = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                if (dev instanceof Sequencer || dev.getMaxReceivers() == 0) continue; // Not an output
                if (dev instanceof Synthesizer) synths.add(info);
                else ports.add(info);
            } catch (MidiUnavailableException | IllegalArgumentException ignored) {
            }
        }
        ports.addAll(synths);
        return ports;
    }

    // Stable id for a device: its name, plus " #n" when several devices have the same name
    private static Map<MidiDevice.Info, String> portIds(List<MidiDevice.Info> devices) {
        Map<MidiDevice.Info, String> ids = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (MidiDevice.Info info : devices) {
            int n = counts.merge(info.getName(), 1, Integer::sum);
            ids.put(info, n == 1 ? info.getName() : info.getName() + " #" + n);
        }
        return ids;
    }

    private static String portId(MidiDevice.Info info) {
        return portIds(outputDevices()).get(info);
    }

    // Find the device for a port id, or the first available device if id is null
    private static MidiDevice.Info findPort(String id) {
        List<MidiDevice.Info> devices = outputDevices();
        if (id == null) return devices.isEmpty() ? null : devices.get(0);
        Map<MidiDevice.Info, String> ids = portIds(devices);
        for (MidiDevice.Info info : devices) {
            if (id.equals(ids.get(info))) return info;
        }
        System.out.println("Port not available: " + id);
        return null;
    }

    // Each entry is {port id, display name}
    public static List<String[]> listPorts() {
        List<MidiDevice.Info> devices = outputDevices();
        Map<MidiDevice.Info, String> ids = portIds(devices);
        List<String[]> result = new ArrayList<>();
        for (MidiDevice.Info info : devices) {
            String id = ids.get(info);
            String desc = info.getDescription();
            boolean showDesc = desc != null && !desc.isEmpty() && !desc.equals(info.getName())
                    && !desc.equalsIgnoreCase("No details available");
            result.add(new String[]{id, showDesc ? id + " - " + desc : id});
        }
        return result;
    }

    // Send GM, GS and XG reset SysEx messages, then All Sound Off / Reset All Controllers /
    // All Notes Off on every channel
    public static void sendReset(Receiver receiver) {
        try {
            byte[][] resets = {MidiPlayerAplay.GM_RESET, MidiPlayerAplay.GS_RESET, MidiPlayerAplay.XG_RESET};
            for (byte[] data : resets) {
                SysexMessage msg = new SysexMessage();
                msg.setMessage(data, data.length);
                receiver.send(msg, -1);
                Thread.sleep(100); // Give the device time to finish the reset
            }
            for (int ch = 0; ch < 16; ch++) {
                receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 120, 0), -1);
                receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 121, 0), -1);
                receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 123, 0), -1);
            }
            Thread.sleep(100);
            System.out.println("GM/GS/XG reset sent");
        } catch (InvalidMidiDataException | IllegalStateException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Without arguments: list the output ports. With arguments: play <file> [port id] to the end
    public static void main(String[] args) throws InterruptedException {
        if (args.length == 0) {
            List<String[]> ports = listPorts();
            if (ports.isEmpty()) System.out.println("No MIDI output port found");
            for (String[] p : ports) System.out.println(p[1]);
            return;
        }
        MidiPlayerJavaSound player = new MidiPlayerJavaSound(args[0]);
        if (args.length > 1) player.setPort(args[1]);
        player.play();
        while (player.isPlaying()) Thread.sleep(200);
    }
}
