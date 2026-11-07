package org.digger;

/**
 * Audio output used by the square-wave synth in {@link Snd}: 22050 Hz, 8-bit
 * signed, mono PCM. Each platform supplies one (javax.sound on the desktop,
 * AudioTrack on Android) via {@link Snd#sinkFactory}.
 */
public interface AudioSink {
    int SAMPLE_RATE = 22050;

    /** Open the device. Return false if audio is unavailable (game runs silently). */
    boolean open();

    /** Write samples; expected to block roughly in real time like a sound card. */
    void write(byte[] buf, int off, int len);

    void close();
}
