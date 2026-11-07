package org.digger.android;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import org.digger.AudioSink;

/**
 * {@link AudioSink} backed by a streaming AudioTrack. The game produces 8-bit
 * signed PCM; AudioTrack's 8-bit format is unsigned, so samples are offset.
 */
final class AndroidAudioSink implements AudioSink {
    /** Silences output while the app is in the background. */
    static volatile boolean muted = false;

    private AudioTrack track;
    private byte[] conv = new byte[0];

    @Override
    public boolean open() {
        try {
            int min = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_8BIT);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, 4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build();
            track.play();
            return true;
        } catch (Exception e) {
            track = null;
            return false;
        }
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        if (conv.length < len) {
            conv = new byte[len];
        }
        boolean mute = muted;
        for (int i = 0; i < len; i++) {
            conv[i] = (byte) (mute ? 0x80 : (buf[off + i] + 128));
        }
        track.write(conv, 0, len);  // blocking: paces the synth in real time
    }

    @Override
    public void close() {
        if (track != null) {
            try {
                track.stop();
            } catch (IllegalStateException ignored) {
            }
            track.release();
            track = null;
        }
    }
}
