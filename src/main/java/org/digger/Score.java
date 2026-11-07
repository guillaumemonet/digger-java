package org.digger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

import static org.digger.Def.*;

/** Scoring and high-score table, ported from scores.c. */
public final class Score {
    private Score() {}

    static final long[] scdatScore = new long[DIGGERS];
    static final long[] scdatNextbs = new long[DIGGERS];

    static final char[] highbuf = new char[10];
    static final long[] scorehigh = new long[12];
    /** scoreinit[0] = current entry; [1..10] = table rows. */
    static final char[][] scoreinit = new char[11][4];

    public static long scoret = 0;
    public static int bonusscore = 20000;

    static final byte[] scorebuf = new byte[512];

    private static final String SFNAME = "DIGGER.SCO";

    static void readscores() {
        scorebuf[0] = 0;
        try {
            if (!Main.levfflag) {
                File f = Hw.file(SFNAME);
                if (f.exists()) {
                    try (FileInputStream in = new FileInputStream(f)) {
                        in.read(scorebuf);
                    }
                }
            } else {
                File f = Hw.file(Main.levfname);
                if (f.exists()) {
                    try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
                        in.seek(1202);
                        in.read(scorebuf);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    static void writescores() {
        try {
            if (!Main.levfflag) {
                try (FileOutputStream out = new FileOutputStream(Hw.file(SFNAME))) {
                    out.write(scorebuf);
                }
            } else {
                try (RandomAccessFile out = new RandomAccessFile(Hw.file(Main.levfname), "rw")) {
                    out.seek(1202);
                    out.write(scorebuf);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void initscores() {
        for (int i = 0; i < Main.diggers; i++) {
            addscore(i, 0);
        }
    }

    public static void loadscores() {
        int p = 0;
        readscores();
        if (Main.gauntlet) p = 111;
        if (Main.diggers == 2) p += 222;
        if (scorebuf[p++] != 's') {
            for (int i = 0; i < 11; i++) {
                scorehigh[i + 1] = 0;
                setInit(i, "...");
            }
        } else {
            for (int i = 1; i < 11; i++) {
                for (int x = 0; x < 3; x++) {
                    scoreinit[i][x] = (char) (scorebuf[p++] & 0xff);
                }
                scoreinit[i][3] = 0;
                p += 2;
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < 6; x++) {
                    highbuf[x] = (char) (scorebuf[p++] & 0xff);
                    sb.append(highbuf[x]);
                }
                scorehigh[i + 1] = atol(sb.toString());
            }
        }
    }

    private static void setInit(int i, String s) {
        for (int x = 0; x < 3; x++) scoreinit[i][x] = x < s.length() ? s.charAt(x) : ' ';
        scoreinit[i][3] = 0;
    }

    public static void zeroscores() {
        scdatScore[0] = scdatScore[1] = 0;
        scdatNextbs[0] = scdatNextbs[1] = bonusscore;
        scoret = 0;
    }

    public static void writecurscore(int col) {
        if (Main.curplayer == 0) {
            writenum(scdatScore[0], 0, 0, 6, col);
        } else if (scdatScore[1] < 100000L) {
            writenum(scdatScore[1], 236, 0, 6, col);
        } else {
            writenum(scdatScore[1], 248, 0, 6, col);
        }
    }

    public static void drawscores() {
        writenum(scdatScore[0], 0, 0, 6, 3);
        if (Main.nplayers == 2 || Main.diggers == 2) {
            if (scdatScore[1] < 100000L) {
                writenum(scdatScore[1], 236, 0, 6, 3);
            } else {
                writenum(scdatScore[1], 248, 0, 6, 3);
            }
        }
    }

    public static void addscore(int n, int score) {
        scdatScore[n] += score;
        if (scdatScore[n] > 999999L) {
            scdatScore[n] = 0;
        }
        if (n == 0) {
            writenum(scdatScore[n], 0, 0, 6, 1);
        } else if (scdatScore[n] < 100000L) {
            writenum(scdatScore[n], 236, 0, 6, 1);
        } else {
            writenum(scdatScore[n], 248, 0, 6, 1);
        }
        if (scdatScore[n] >= scdatNextbs[n] + n && scdatScore[n] < 1000000L) {
            if (Dig.getlives(n) < 5 || Main.unlimlives) {
                if (Main.gauntlet) {
                    Dig.cgtime += 17897715L;
                } else {
                    Dig.addlife(n);
                }
                Draw.drawlives();
            }
            scdatNextbs[n] += bonusscore;
        }
        Main.incpenalty();
        Main.incpenalty();
        Main.incpenalty();
    }

    public static void endofgame() {
        boolean initflag = false;
        for (int i = 0; i < Main.diggers; i++) {
            addscore(i, 0);
        }
        if (Rec.playing || !Rec.drfvalid) {
            return;
        }
        if (Main.gauntlet) {
            Main.cleartopline();
            Draw.outtext("TIME UP", 120, 0, 3);
            for (int i = 0; i < 50 && !Inp.escape; i++) {
                Dig.newframe();
            }
            Draw.outtext("       ", 120, 0, 3);
        }
        for (int i = Main.curplayer; i < Main.curplayer + Main.diggers; i++) {
            scoret = scdatScore[i];
            if (scoret > scorehigh[11]) {
                Vid.gclear();
                drawscores();
                String pl = "PLAYER " + (i == 0 ? "1" : "2");
                System.arraycopy(pl.toCharArray(), 0, Main.pldispbuf, 0, pl.length());
                Draw.outtext(pl, 108, 0, 2);
                Draw.outtext(" NEW HIGH SCORE ", 64, 40, 2);
                getinitials();
                shufflehigh();
                savescores();
                initflag = true;
            }
        }
        if (!initflag && !Main.gauntlet) {
            Main.cleartopline();
            Draw.outtext("GAME OVER", 104, 0, 3);
            for (int i = 0; i < 50 && !Inp.escape; i++) {
                Dig.newframe();
            }
            Draw.outtext("         ", 104, 0, 3);
            Spr.setretr(true);
        }
    }

    public static void showtable() {
        Draw.outtext("HIGH SCORES", 16, 25, 3);
        int col = 2;
        for (int i = 1; i < 11; i++) {
            String hs = new String(scoreinit[i], 0, 3) + " " + numtostring(scorehigh[i + 1]);
            Draw.outtext(hs, 16, 31 + 13 * i, col);
            col = 1;
        }
    }

    static void savescores() {
        int p = 0;
        if (Main.gauntlet) p = 111;
        if (Main.diggers == 2) p += 222;
        scorebuf[p] = 's';
        for (int i = 1; i < 11; i++) {
            String hs = new String(scoreinit[i], 0, 3) + " " + numtostring(scorehigh[i + 1]);
            for (int j = 0; j < 11; j++) {
                scorebuf[p + j + i * 11 - 10] = (byte) (j < hs.length() ? hs.charAt(j) : 0);
            }
        }
        writescores();
    }

    static void getinitials() {
        Snd.pauseWindowsSoundPlayback();
        Dig.newframe();
        Draw.outtext("ENTER YOUR", 100, 70, 3);
        Draw.outtext(" INITIALS", 100, 90, 3);
        Draw.outtext("_ _ _", 128, 130, 3);
        setInit(0, "...");
        Snd.killsound();
        Hw.textInput = true;
        for (int i = 0; i < 3; i++) {
            int k = 0;
            while (k == 0) {
                k = getinitial(i * 24 + 128, 130);
                if (k == 8 || k == 127) {
                    if (i > 0) i--;
                    k = 0;
                }
            }
            Vid.gwrite(i * 24 + 128, 130, k, 3);
            scoreinit[0][i] = (char) k;
        }
        Hw.textInput = false;
        for (int i = 0; i < 20; i++) {
            flashywait(2);
        }
        Snd.setupsound();
        Vid.gclear();
        Vid.gpal(0);
        Vid.ginten(0);
        Spr.setretr(true);
        Rec.recputinit(new String(scoreinit[0], 0, 3));
        Snd.resumeWindowsSoundPlayback();
    }

    static void flashywait(int n) {
        int p = 0;
        Spr.setretr(false);
        for (int i = 0; i < (n << 1); i++) {
            for (int cx = 0; cx < Snd.volume; cx++) {
                Vid.gpal(p = 1 - p);
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
            }
        }
    }

    static int getinitial(int x, int y) {
        Vid.gwrite(x, y, '_', 3);
        while (true) {
            for (int i = 0; i < 40; i++) {
                if (Hw.kbhit()) return Hw.getkey();
                flashywait(2);
            }
            for (int i = 0; i < 40; i++) {
                if (Hw.kbhit()) {
                    Vid.gwrite(x, y, '_', 3);
                    return Hw.getkey();
                }
                flashywait(2);
            }
        }
    }

    static void shufflehigh() {
        int j;
        for (j = 10; j > 1; j--) {
            if (scoret < scorehigh[j]) break;
        }
        for (int i = 10; i > j; i--) {
            scorehigh[i + 1] = scorehigh[i];
            System.arraycopy(scoreinit[i - 1], 0, scoreinit[i], 0, 4);
        }
        scorehigh[j + 1] = scoret;
        System.arraycopy(scoreinit[0], 0, scoreinit[j], 0, 4);
    }

    public static void scorekill(int n) { addscore(n, 250); }
    public static void scorekill2() { addscore(0, 125); addscore(1, 125); }
    public static void scoreemerald(int n) { addscore(n, 25); }
    public static void scoreoctave(int n) { addscore(n, 250); }
    public static void scoregold(int n) { addscore(n, 500); }
    public static void scorebonus(int n) { addscore(n, 1000); }
    public static void scoreeatm(int n, int msc) { addscore(n, msc * 200); }

    static void writenum(long n, int x, int y, int w, int c) {
        int xp = (w - 1) * 12 + x;
        n %= 1000000L;
        while (w > 0) {
            int d = (int) (n % 10);
            if (w > 1 || d > 0) {
                Vid.gwrite(xp, y, d + '0', c);
            }
            n /= 10;
            w--;
            xp -= 12;
        }
    }

    static String numtostring(long n) {
        char[] p = new char[8];
        int x;
        for (x = 0; x < 7; x++) {
            p[6 - x] = (char) ((n % 10L) + '0');
            n /= 10L;
            if (n == 0L) {
                x++;
                break;
            }
        }
        for (; x < 7; x++) {
            p[6 - x] = ' ';
        }
        p[7] = 0;
        return new String(p, 0, 7);
    }

    private static long atol(String s) {
        s = s.trim();
        long v = 0;
        int i = 0;
        boolean neg = false;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
            neg = s.charAt(i) == '-';
            i++;
        }
        for (; i < s.length() && Character.isDigit(s.charAt(i)); i++) {
            v = v * 10 + (s.charAt(i) - '0');
        }
        return neg ? -v : v;
    }
}
