package org.digger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import static org.digger.Def.*;

/**
 * Sound, ported from sound.c. The original simulated the PC speaker via timer
 * interrupts; here the same note state machines drive a Java square-wave synth.
 * Effects (timer2) take priority over the background music (timer0), matching
 * how the single PC speaker multiplexed them.
 */
public final class Snd {
    private Snd() {}

    public static boolean soundflag = true, musicflag = true;
    public static int volume = 8;
    public static int timerrate = 0x7d0;
    public static int timercount = 0;

    static int wavetype = 0, musvol = 0;
    static int t2val = 0, t0val = 0;
    static int timerclock = 0;
    static boolean sndflag = false, soundpausedflag = false;

    static long randvs;

    static int randnos(int n) {
        randvs = randvs * 0x15a4e35L + 1;
        return (int) ((randvs & 0x7fffffffL) % n);
    }

    // ------------------------------------------------------------------ synth
    // Square-wave amplitude out of 127 (8-bit signed). Kept low: square waves
    // are harsh and the original PC speaker was quiet.
    private static final int AMPLITUDE = 9;
    // Two independent tone sources; effects take priority over music, just as
    // the single PC speaker multiplexed them.
    private static volatile double fxFreq = 0, musFreq = 0;
    private static volatile boolean fxOn = false, musOn = false;
    private static Thread synthThread;
    private static volatile boolean synthRunning = false;
    private static SourceDataLine line;

    private static void synthStart() {
        if (synthRunning) return;
        try {
            AudioFormat fmt = new AudioFormat(22050f, 8, 1, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, 4096);
            line.start();
        } catch (Exception e) {
            line = null;
            return; // sound unavailable: game continues silently
        }
        synthRunning = true;
        synthThread = new Thread(Snd::synthLoop, "digger-synth");
        synthThread.setDaemon(true);
        synthThread.start();
    }

    private static void synthStop() {
        synthRunning = false;
        if (synthThread != null) {
            try { synthThread.join(200); } catch (InterruptedException ignored) {}
            synthThread = null;
        }
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
    }

    private static void synthLoop() {
        double phase = 0;
        byte[] buf = new byte[441]; // ~20ms at 22050Hz
        while (synthRunning) {
            double f = fxOn ? fxFreq : (musOn ? musFreq : 0);
            boolean on = soundflag && f > 30 && f < 9000;
            for (int i = 0; i < buf.length; i++) {
                if (on) {
                    phase += f / 22050.0;
                    if (phase >= 1.0) phase -= 1.0;
                    buf[i] = (byte) (phase < 0.5 ? AMPLITUDE : -AMPLITUDE);
                } else {
                    buf[i] = 0;
                }
            }
            if (line != null) {
                line.write(buf, 0, buf.length);
            } else {
                try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            }
        }
    }

    private static void setFx(double freq, boolean on) {
        fxFreq = freq;
        fxOn = on;
    }

    // ---- sound threads: effects at a fixed rate, music at the note rate ----
    private static Thread effectThread, musicThread;
    private static volatile boolean threadsRunning = false;

    // Music tempo in melody-ticks per second (pitch comes from the synth, so
    // this is purely tempo). The death dirge needs a higher rate to play
    // through before the digger respawns.
    private static final double MUSIC_HZ_NORMAL = 72.0;
    private static final double MUSIC_HZ_DIRGE = 115.0;

    private static void startSoundThreads() {
        if (threadsRunning) return;
        threadsRunning = true;
        effectThread = new Thread(() -> {
            while (threadsRunning) {
                soundintEffects();
                try { Thread.sleep(14); } catch (InterruptedException ignored) {}
            }
        }, "digger-effects");
        effectThread.setDaemon(true);
        effectThread.start();

        musicThread = new Thread(() -> {
            // Pitch comes from the synth directly, so the tick rate only sets
            // tempo. The original advanced its melody on the note-frequency
            // timer (1193181/t0val); we reproduce that with a real-time
            // accumulator so OS sleep granularity (~15ms on Windows) doesn't
            // distort the tempo and MUSIC_TEMPO actually takes effect.
            long prev = System.nanoTime();
            double acc = 0;
            while (threadsRunning) {
                long now = System.nanoTime();
                acc += (now - prev) / 1_000_000.0;
                prev = now;
                int guard = 0;
                while (acc >= musicPeriodMs() && guard++ < 256) {
                    acc -= musicPeriodMs();
                    musicTick();
                }
                try { Thread.sleep(4); } catch (InterruptedException ignored) {}
            }
        }, "digger-music");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    private static void stopSoundThreads() {
        threadsRunning = false;
        for (Thread t : new Thread[]{effectThread, musicThread}) {
            if (t != null) {
                try { t.join(200); } catch (InterruptedException ignored) {}
            }
        }
        effectThread = musicThread = null;
    }

    public static void fillbuffer() { /* s0fillbuffer: empty */ }

    private static void soundintEffects() {
        timerclock++;
        if (soundflag && !sndflag) {
            sndflag = true;
        }
        if (!soundflag && sndflag) {
            sndflag = false;
            setFx(0, false);
        }
        if (sndflag && !soundpausedflag) {
            t2val = 40;
            soundemeraldupdate();
            soundwobbleupdate();
            soundddieupdate();
            soundbreakupdate();
            soundgoldupdate();
            soundemupdate();
            soundexplodeupdate();
            soundfireupdate();
            soundeatmupdate();
            soundfallupdate();
            sound1upupdate();
            soundbonusupdate();
            if (t2val != 40) {
                setFx(1193181.0 / t2val, true);
            } else {
                setFx(0, false);
            }
        }
    }

    private static double musicPeriodMs() {
        double hz = (tuneno == 2) ? MUSIC_HZ_DIRGE : MUSIC_HZ_NORMAL;
        return 1000.0 / hz;
    }

    private static void musicTick() {
        if (sndflag && !soundpausedflag && musicflag) {
            t0val = 0x7d00;
            musicupdate();
            // Articulate the death dirge: silence the note during its release
            // stage so successive notes don't run together (the original got
            // this gap from the fading musvol envelope). Other tunes are left
            // continuous, which sounds right with the fixed-amplitude synth.
            boolean release = (tuneno == 2) && musicstage == 2;
            musOn = musicplaying && t0val != 0x7d00 && musvol > 1 && !release;
            musFreq = musOn ? 1193181.0 / t0val : 0;
        } else {
            musOn = false;
        }
    }

    public static void sett2val(int t2v) { /* tone emitted by the effect thread */ }

    public static void soundstop() {
        soundfalloff();
        soundwobbleoff();
        for (int i = 0; i < FIREBALLS; i++) soundfireoff(i);
        musicoff();
        soundbonusoff();
        for (int i = 0; i < FIREBALLS; i++) soundexplodeoff(i);
        soundbreakoff();
        soundemoff();
        soundemeraldoff();
        soundgoldoff();
        soundeatmoff();
        soundddieoff();
        sound1upoff();
    }

    // ---------------------------------------------------------- level done jingle
    static boolean soundlevdoneflag = false;
    static int nljpointer = 0, nljnoteduration = 0;
    static final int[] newlevjingle = {0x8e8, 0x712, 0x5f2, 0x7f0, 0x6ac, 0x54c,
            0x712, 0x5f2, 0x4b8, 0x474, 0x474};

    public static void soundlevdone() {
        int timer = 0;
        soundstop();
        nljpointer = 0;
        nljnoteduration = 20;
        soundlevdoneflag = soundpausedflag = true;
        while (soundlevdoneflag && !Inp.escape) {
            fillbuffer();
            if (timerclock == timer) {
                try { Thread.sleep(2); } catch (InterruptedException ignored) {}
                continue;
            }
            soundlevdoneupdate();
            Inp.checkkeyb();
            timer = timerclock;
        }
        soundlevdoneoff();
    }

    static void soundlevdoneoff() {
        soundlevdoneflag = soundpausedflag = false;
    }

    static void soundlevdoneupdate() {
        if (sndflag) {
            if (nljpointer < 11) t2val = newlevjingle[nljpointer];
            t0val = t2val + 35;
            musvol = 50;
            setFx(1193181.0 / t2val, true);
            if (nljnoteduration > 0) {
                nljnoteduration--;
            } else {
                nljnoteduration = 20;
                nljpointer++;
                if (nljpointer > 10) soundlevdoneoff();
            }
        } else {
            soundlevdoneflag = false;
        }
    }

    // ------------------------------------------------------------------ fall
    static boolean soundfallflag = false, soundfallf = false;
    static int soundfallvalue, soundfalln = 0;

    public static void soundfall() { soundfallvalue = 1000; soundfallflag = true; }
    public static void soundfalloff() { soundfallflag = false; soundfalln = 0; }

    static void soundfallupdate() {
        if (soundfallflag) {
            if (soundfalln < 1) {
                soundfalln++;
                if (soundfallf) t2val = soundfallvalue;
            } else {
                soundfalln = 0;
                if (soundfallf) { soundfallvalue += 50; soundfallf = false; }
                else soundfallf = true;
            }
        }
    }

    // ----------------------------------------------------------------- break
    static boolean soundbreakflag = false;
    static int soundbreakduration = 0, soundbreakvalue = 0;

    public static void soundbreak() {
        soundbreakduration = 3;
        if (soundbreakvalue < 15000) soundbreakvalue = 15000;
        soundbreakflag = true;
    }
    static void soundbreakoff() { soundbreakflag = false; }
    static void soundbreakupdate() {
        if (soundbreakflag) {
            if (soundbreakduration != 0) { soundbreakduration--; t2val = soundbreakvalue; }
            else soundbreakflag = false;
        }
    }

    // ---------------------------------------------------------------- wobble
    static boolean soundwobbleflag = false;
    static int soundwobblen = 0;

    public static void soundwobble() { soundwobbleflag = true; }
    public static void soundwobbleoff() { soundwobbleflag = false; soundwobblen = 0; }
    static void soundwobbleupdate() {
        if (soundwobbleflag) {
            soundwobblen++;
            if (soundwobblen > 63) soundwobblen = 0;
            switch (soundwobblen) {
                case 0: t2val = 0x7d0; break;
                case 16: case 48: t2val = 0x9c4; break;
                case 32: t2val = 0xbb8; break;
            }
        }
    }

    // ------------------------------------------------------------------ fire
    static final boolean[] soundfireflag = new boolean[FIREBALLS];
    static final boolean[] sff = new boolean[FIREBALLS];
    static final int[] soundfirevalue = new int[FIREBALLS];
    static final int[] soundfiren = new int[FIREBALLS];
    static int soundfirew = 0;

    public static void soundfire(int n) { soundfirevalue[n] = 500; soundfireflag[n] = true; }
    public static void soundfireoff(int n) { soundfireflag[n] = false; soundfiren[n] = 0; }
    static void soundfireupdate() {
        boolean f = false;
        for (int n = 0; n < FIREBALLS; n++) {
            sff[n] = false;
            if (soundfireflag[n]) {
                if (soundfiren[n] == 1) {
                    soundfiren[n] = 0;
                    soundfirevalue[n] += soundfirevalue[n] / 55;
                    sff[n] = true;
                    f = true;
                    if (soundfirevalue[n] > 30000) soundfireoff(n);
                } else {
                    soundfiren[n]++;
                }
            }
        }
        if (f) {
            int n;
            do {
                n = soundfirew++;
                if (soundfirew == FIREBALLS) soundfirew = 0;
            } while (!sff[n]);
            t2val = soundfirevalue[n] + randnos(soundfirevalue[n] >> 3);
        }
    }

    // --------------------------------------------------------------- explode
    static final boolean[] soundexplodeflag = new boolean[FIREBALLS];
    static final boolean[] sef = new boolean[FIREBALLS];
    static final int[] soundexplodevalue = new int[FIREBALLS];
    static final int[] soundexplodeduration = new int[FIREBALLS];
    static int soundexplodew = 0;

    public static void soundexplode(int n) {
        soundexplodevalue[n] = 1500;
        soundexplodeduration[n] = 10;
        soundexplodeflag[n] = true;
        soundfireoff(n);
    }
    static void soundexplodeoff(int n) { soundexplodeflag[n] = false; }
    static void soundexplodeupdate() {
        boolean f = false;
        for (int n = 0; n < FIREBALLS; n++) {
            sef[n] = false;
            if (soundexplodeflag[n]) {
                if (soundexplodeduration[n] != 0) {
                    soundexplodevalue[n] -= (soundexplodevalue[n] >> 3);
                    soundexplodeduration[n]--;
                    sef[n] = true;
                    f = true;
                } else {
                    soundexplodeflag[n] = false;
                }
            }
        }
        if (f) {
            int n;
            do {
                n = soundexplodew++;
                if (soundexplodew == FIREBALLS) soundexplodew = 0;
            } while (!sef[n]);
            t2val = soundexplodevalue[n];
        }
    }

    // ----------------------------------------------------------------- bonus
    static boolean soundbonusflag = false;
    static int soundbonusn = 0;

    public static void soundbonus() { soundbonusflag = true; }
    public static void soundbonusoff() { soundbonusflag = false; soundbonusn = 0; }
    static void soundbonusupdate() {
        if (soundbonusflag) {
            soundbonusn++;
            if (soundbonusn > 15) soundbonusn = 0;
            if (soundbonusn >= 0 && soundbonusn < 6) t2val = 0x4ce;
            if (soundbonusn >= 8 && soundbonusn < 14) t2val = 0x5e9;
        }
    }

    // -------------------------------------------------------------- em / emerald
    static boolean soundemflag = false;
    public static void soundem() { soundemflag = true; }
    static void soundemoff() { soundemflag = false; }
    static void soundemupdate() {
        if (soundemflag) { t2val = 1000; soundemoff(); }
    }

    static boolean soundemeraldflag = false;
    static int soundemeraldduration, emerfreq, soundemeraldn;
    static final int[] emfreqs = {0x8e8, 0x7f0, 0x712, 0x6ac, 0x5f2, 0x54c, 0x4b8, 0x474};

    public static void soundemerald(int n) {
        emerfreq = emfreqs[n];
        soundemeraldduration = 7;
        soundemeraldn = 0;
        soundemeraldflag = true;
    }
    static void soundemeraldoff() { soundemeraldflag = false; }
    static void soundemeraldupdate() {
        if (soundemeraldflag) {
            if (soundemeraldduration != 0) {
                if (soundemeraldn == 0 || soundemeraldn == 1) t2val = emerfreq;
                soundemeraldn++;
                if (soundemeraldn > 7) { soundemeraldn = 0; soundemeraldduration--; }
            } else {
                soundemeraldoff();
            }
        }
    }

    // ------------------------------------------------------------------ gold
    static boolean soundgoldflag = false, soundgoldf = false;
    static int soundgoldvalue1, soundgoldvalue2, soundgoldduration;

    public static void soundgold() {
        soundgoldvalue1 = 500;
        soundgoldvalue2 = 4000;
        soundgoldduration = 30;
        soundgoldf = false;
        soundgoldflag = true;
    }
    static void soundgoldoff() { soundgoldflag = false; }
    static void soundgoldupdate() {
        if (soundgoldflag) {
            if (soundgoldduration != 0) soundgoldduration--;
            else soundgoldflag = false;
            if (soundgoldf) { soundgoldf = false; t2val = soundgoldvalue1; }
            else { soundgoldf = true; t2val = soundgoldvalue2; }
            soundgoldvalue1 += (soundgoldvalue1 >> 4);
            soundgoldvalue2 -= (soundgoldvalue2 >> 4);
        }
    }

    // ------------------------------------------------------------------ eatm
    static boolean soundeatmflag = false;
    static int soundeatmvalue, soundeatmduration, soundeatmn;

    public static void soundeatm() {
        soundeatmduration = 20;
        soundeatmn = 3;
        soundeatmvalue = 2000;
        soundeatmflag = true;
    }
    static void soundeatmoff() { soundeatmflag = false; }
    static void soundeatmupdate() {
        if (soundeatmflag) {
            if (soundeatmn != 0) {
                if (soundeatmduration != 0) {
                    if ((soundeatmduration % 4) == 1) t2val = soundeatmvalue;
                    if ((soundeatmduration % 4) == 3) t2val = soundeatmvalue - (soundeatmvalue >> 4);
                    soundeatmduration--;
                    soundeatmvalue -= (soundeatmvalue >> 4);
                } else {
                    soundeatmduration = 20;
                    soundeatmn--;
                    soundeatmvalue = 2000;
                }
            } else {
                soundeatmflag = false;
            }
        }
    }

    // ------------------------------------------------------------------ ddie
    static boolean soundddieflag = false;
    static int soundddien, soundddievalue;

    public static void soundddie() { soundddien = 0; soundddievalue = 20000; soundddieflag = true; }
    static void soundddieoff() { soundddieflag = false; }
    static void soundddieupdate() {
        if (soundddieflag) {
            soundddien++;
            if (soundddien == 1) musicoff();
            if (soundddien >= 1 && soundddien <= 10) soundddievalue = 20000 - soundddien * 1000;
            if (soundddien > 10) soundddievalue += 500;
            if (soundddievalue > 30000) soundddieoff();
            t2val = soundddievalue;
        }
    }

    // ------------------------------------------------------------------- 1up
    static boolean sound1upflag = false;
    static int sound1upduration = 0;

    public static void sound1up() { sound1upduration = 96; sound1upflag = true; }
    static void sound1upoff() { sound1upflag = false; }
    static void sound1upupdate() {
        if (sound1upflag) {
            if ((sound1upduration / 3) % 2 != 0) t2val = (sound1upduration << 2) + 600;
            sound1upduration--;
            if (sound1upduration < 1) sound1upflag = false;
        }
    }

    // ----------------------------------------------------------------- music
    static boolean musicplaying = false;
    static int musicp = 0, tuneno = 0, noteduration = 0, notevalue = 0, musicmaxvol = 0,
            musicattackrate = 0, musicsustainlevel = 0, musicdecayrate = 0, musicnotewidth = 0,
            musicreleaserate = 0, musicstage = 0, musicn = 0;

    public static void music(int tune) {
        tuneno = tune;
        musicp = 0;
        noteduration = 0;
        switch (tune) {
            case 0:
                musicmaxvol = 50; musicattackrate = 20; musicsustainlevel = 20;
                musicdecayrate = 10; musicreleaserate = 4;
                break;
            case 1:
                musicmaxvol = 50; musicattackrate = 50; musicsustainlevel = 8;
                musicdecayrate = 15; musicreleaserate = 1;
                break;
            case 2:
                musicmaxvol = 50; musicattackrate = 50; musicsustainlevel = 25;
                musicdecayrate = 5; musicreleaserate = 1;
                break;
        }
        musicplaying = true;
        if (tune == 2) soundddieoff();
    }

    public static void musicoff() { musicplaying = false; musicp = 0; }

    static final int[] bonusjingle = {
        0x11d1,2,0x11d1,2,0x11d1,4,0x11d1,2,0x11d1,2,0x11d1,4,0x11d1,2,0x11d1,2,
        0xd59,4,0xbe4,4,0xa98,4,0x11d1,2,0x11d1,2,0x11d1,4,0x11d1,2,0x11d1,2,
        0x11d1,4,0xd59,2,0xa98,2,0xbe4,4,0xe24,4,0x11d1,4,0x11d1,2,0x11d1,2,
        0x11d1,4,0x11d1,2,0x11d1,2,0x11d1,4,0x11d1,2,0x11d1,2,0xd59,4,0xbe4,4,
        0xa98,4,0xd59,2,0xa98,2,0x8e8,10,0xa00,2,0xa98,2,0xbe4,2,0xd59,4,
        0xa98,4,0xd59,4,0x11d1,2,0x11d1,2,0x11d1,4,0x11d1,2,0x11d1,2,0x11d1,4,
        0x11d1,2,0x11d1,2,0xd59,4,0xbe4,4,0xa98,4,0x11d1,2,0x11d1,2,0x11d1,4,
        0x11d1,2,0x11d1,2,0x11d1,4,0xd59,2,0xa98,2,0xbe4,4,0xe24,4,0x11d1,4,
        0x11d1,2,0x11d1,2,0x11d1,4,0x11d1,2,0x11d1,2,0x11d1,4,0x11d1,2,0x11d1,2,
        0xd59,4,0xbe4,4,0xa98,4,0xd59,2,0xa98,2,0x8e8,10,0xa00,2,0xa98,2,
        0xbe4,2,0xd59,4,0xa98,4,0xd59,4,0xa98,2,0xa98,2,0xa98,4,0xa98,2,
        0xa98,2,0xa98,4,0xa98,2,0xa98,2,0xa98,4,0x7f0,4,0xa98,4,0x7f0,4,
        0xa98,4,0x7f0,4,0xa98,4,0xbe4,4,0xd59,4,0xe24,4,0xfdf,4,0xa98,2,
        0xa98,2,0xa98,4,0xa98,2,0xa98,2,0xa98,4,0xa98,2,0xa98,2,0xa98,4,
        0x7f0,4,0xa98,4,0x7f0,4,0xa98,4,0x7f0,4,0x8e8,4,0x970,4,0x8e8,4,
        0x970,4,0x8e8,4,0xa98,2,0xa98,2,0xa98,4,0xa98,2,0xa98,2,0xa98,4,
        0xa98,2,0xa98,2,0xa98,4,0x7f0,4,0xa98,4,0x7f0,4,0xa98,4,0x7f0,4,
        0xa98,4,0xbe4,4,0xd59,4,0xe24,4,0xfdf,4,0xa98,2,0xa98,2,0xa98,4,
        0xa98,2,0xa98,2,0xa98,4,0xa98,2,0xa98,2,0xa98,4,0x7f0,4,0xa98,4,
        0x7f0,4,0xa98,4,0x7f0,4,0x8e8,4,0x970,4,0x8e8,4,0x970,4,0x8e8,4,
        0x7d64};

    static final int[] backgjingle = {
        0xfdf,2,0x11d1,2,0xfdf,2,0x1530,2,0x1ab2,2,0x1530,2,0x1fbf,4,0xfdf,2,
        0x11d1,2,0xfdf,2,0x1530,2,0x1ab2,2,0x1530,2,0x1fbf,4,0xfdf,2,0xe24,2,
        0xd59,2,0xe24,2,0xd59,2,0xfdf,2,0xe24,2,0xfdf,2,0xe24,2,0x11d1,2,
        0xfdf,2,0x11d1,2,0xfdf,2,0x1400,2,0xfdf,4,0xfdf,2,0x11d1,2,0xfdf,2,
        0x1530,2,0x1ab2,2,0x1530,2,0x1fbf,4,0xfdf,2,0x11d1,2,0xfdf,2,0x1530,2,
        0x1ab2,2,0x1530,2,0x1fbf,4,0xfdf,2,0xe24,2,0xd59,2,0xe24,2,0xd59,2,
        0xfdf,2,0xe24,2,0xfdf,2,0xe24,2,0x11d1,2,0xfdf,2,0x11d1,2,0xfdf,2,
        0xe24,2,0xd59,4,0xa98,2,0xbe4,2,0xa98,2,0xd59,2,0x11d1,2,0xd59,2,
        0x1530,4,0xa98,2,0xbe4,2,0xa98,2,0xd59,2,0x11d1,2,0xd59,2,0x1530,4,
        0xa98,2,0x970,2,0x8e8,2,0x970,2,0x8e8,2,0xa98,2,0x970,2,0xa98,2,
        0x970,2,0xbe4,2,0xa98,2,0xbe4,2,0xa98,2,0xd59,2,0xa98,4,0xa98,2,
        0xbe4,2,0xa98,2,0xd59,2,0x11d1,2,0xd59,2,0x1530,4,0xa98,2,0xbe4,2,
        0xa98,2,0xd59,2,0x11d1,2,0xd59,2,0x1530,4,0xa98,2,0x970,2,0x8e8,2,
        0x970,2,0x8e8,2,0xa98,2,0x970,2,0xa98,2,0x970,2,0xbe4,2,0xa98,2,
        0xbe4,2,0xa98,2,0xd59,2,0xa98,4,0x7f0,2,0x8e8,2,0xa98,2,0xd59,2,
        0x11d1,2,0xd59,2,0x1530,4,0xa98,2,0xbe4,2,0xa98,2,0xd59,2,0x11d1,2,
        0xd59,2,0x1530,4,0xa98,2,0x970,2,0x8e8,2,0x970,2,0x8e8,2,0xa98,2,
        0x970,2,0xa98,2,0x970,2,0xbe4,2,0xa98,2,0xbe4,2,0xd59,2,0xbe4,2,
        0xa98,4,0x7d64};

    static final int[] dirge = {
        0x7d00,2,0x11d1,6,0x11d1,4,0x11d1,2,0x11d1,6,0xefb,4,0xfdf,2,
        0xfdf,4,0x11d1,2,0x11d1,4,0x12e0,2,0x11d1,12,0x7d00,16,0x7d00,16,
        0x7d00,16,0x7d00,16,0x7d00,16,0x7d00,16,0x7d00,16,0x7d00,16,0x7d00,16,
        0x7d00,16,0x7d00,16,0x7d00,16,0x7d64};

    static void musicupdate() {
        if (!musicplaying) return;
        if (noteduration != 0) {
            noteduration--;
        } else {
            musicstage = musicn = 0;
            switch (tuneno) {
                case 0:
                    noteduration = bonusjingle[musicp + 1] * 3;
                    musicnotewidth = noteduration - 3;
                    notevalue = bonusjingle[musicp];
                    musicp += 2;
                    if (bonusjingle[musicp] == 0x7d64) musicp = 0;
                    break;
                case 1:
                    noteduration = backgjingle[musicp + 1] * 6;
                    musicnotewidth = 12;
                    notevalue = backgjingle[musicp];
                    musicp += 2;
                    if (backgjingle[musicp] == 0x7d64) musicp = 0;
                    break;
                case 2:
                    noteduration = dirge[musicp + 1] * 10;
                    musicnotewidth = noteduration - 10;
                    notevalue = dirge[musicp];
                    musicp += 2;
                    if (dirge[musicp] == 0x7d64) musicp = 0;
                    break;
            }
        }
        musicn++;
        wavetype = 1;
        t0val = notevalue;
        if (musicn >= musicnotewidth) musicstage = 2;
        switch (musicstage) {
            case 0:
                if (musvol + musicattackrate >= musicmaxvol) {
                    musicstage = 1;
                    musvol = musicmaxvol;
                    break;
                }
                musvol += musicattackrate;
                break;
            case 1:
                if (musvol - musicdecayrate <= musicsustainlevel) {
                    musvol = musicsustainlevel;
                    break;
                }
                musvol -= musicdecayrate;
                break;
            case 2:
                if (musvol - musicreleaserate <= 1) {
                    musvol = 1;
                    break;
                }
                musvol -= musicreleaserate;
                break;
        }
        if (musvol == 1) t0val = 0x7d00;
    }

    public static void soundpause() { soundpausedflag = true; fxOn = false; musOn = false; }
    public static void soundpauseoff() { soundpausedflag = false; }
    public static void setsoundt2() { /* handled by the effect thread */ }

    public static void initsound() {
        wavetype = 2;
        t0val = 12000;
        musvol = 8;
        t2val = 40;
        sndflag = true;
        timerrate = 0x4000;   // startint8 default int8 rate (~73 Hz when idle)
        soundstop();
        setupsound();
        randvs = Hw.getlrt();
    }

    public static void killsound() {
        stopSoundThreads();
        synthStop();
        fxOn = false;
        musOn = false;
    }

    public static void setupsound() {
        synthStart();
        startSoundThreads();
    }

    public static void soundoff() { fxOn = false; musOn = false; }
    public static void soundkillglob() { /* nothing global to free */ }

    public static void pauseWindowsSoundPlayback() { fxOn = false; musOn = false; }
    public static void resumeWindowsSoundPlayback() { /* resumes on next tick */ }
}
