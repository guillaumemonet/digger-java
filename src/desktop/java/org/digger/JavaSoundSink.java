package org.digger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** Desktop {@link AudioSink} backed by javax.sound.sampled. */
final class JavaSoundSink implements AudioSink {
    private SourceDataLine line;

    @Override
    public boolean open() {
        try {
            AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, 4096);
            line.start();
            return true;
        } catch (Exception e) {
            line = null;
            return false;
        }
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        line.write(buf, off, len);
    }

    @Override
    public void close() {
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
    }
}
