import javax.sound.sampled.*;

/**
 * Gerencia a reprodução em tempo real de áudio do microfone do celular no PC.
 */
public class AudioPlaybackManager {

    private static SourceDataLine speakerLine;

    public static synchronized void playAudio(byte[] pcmData) {
        try {
            if (speakerLine == null || !speakerLine.isOpen()) {
                AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                speakerLine = (SourceDataLine) AudioSystem.getLine(info);
                speakerLine.open(format, 4096);
                speakerLine.start();
            }
            speakerLine.write(pcmData, 0, pcmData.length);
        } catch (Exception e) {
            System.err.println("Erro na reprodução de áudio: " + e.getMessage());
        }
    }

    public static synchronized void stop() {
        if (speakerLine != null) {
            try {
                speakerLine.stop();
                speakerLine.close();
            } catch (Exception ignored) {}
            speakerLine = null;
        }
    }
}
