import java.util.List;
import java.util.prefs.Preferences;

// Common interface of the playing backends used by the GUI.
//  - Linux:   MidiPlayerAplay     (aplaymidi, can reach every ALSA sequencer port incl. TiMidity/FluidSynth)
//  - Others:  MidiPlayerJavaSound (Java Sound API, e.g. Windows MIDI output devices incl. hardware synths)
// The backend can be forced with -Dmidiplayer.backend=aplay or -Dmidiplayer.backend=javasound
public interface MidiBackend {

    void play();

    void stop();

    void load(String midiFile);

    void setLoop(boolean loop);

    boolean isLoop();

    boolean isPlaying();

    // Port id as returned in element 0 of listPorts(). Takes effect on the next play
    void setPort(String port);

    String getPort();

    static boolean useAplay() {
        String backend = System.getProperty("midiplayer.backend", "");
        if (backend.equalsIgnoreCase("aplay")) return true;
        if (backend.equalsIgnoreCase("javasound")) return false;
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    // Each entry is {port id, display name}
    static List<String[]> listPorts() {
        return useAplay() ? MidiPlayerAplay.listPortsForDisplay() : MidiPlayerJavaSound.listPorts();
    }

    // Settings shared by the GUI and the CLI (last folder, last port)
    static Preferences prefs() {
        return Preferences.userNodeForPackage(MidiPlayerGUI.class);
    }

    // aplaymidi and Java Sound use different port ids, so they are saved separately
    static String portPrefKey() {
        return useAplay() ? "port" : "port.javasound";
    }

    static MidiBackend create(String midiFile) {
        return useAplay() ? new MidiPlayerAplay(midiFile) : new MidiPlayerJavaSound(midiFile);
    }
}
