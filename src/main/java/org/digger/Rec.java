package org.digger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;

import static org.digger.Def.*;

/** Game recording / playback (DRF format), ported from record.c. */
public final class Rec {
    private Rec() {}

    public static boolean playing = false, savedrf = false, gotname = false,
            gotgame = false, drfvalid = true, kludge = false;

    static String rname = "";

    static final char[] recb = new char[(int) MAX_REC_BUFFER];
    static int recp = 0;
    static int reccc = 0, recrl = 0, rlleft = 0;
    static char recd, rld;

    // playback buffer
    static String plb = "";
    static int plp = 0;

    private static final String DEFAULTSN = "DIGGER.DRF";

    static void mprintf(String s) {
        for (int i = 0; i < s.length(); i++) {
            recb[recp++] = s.charAt(i);
            if (recp > MAX_REC_BUFFER - 80) {
                recp = 0;
            }
        }
    }

    public static void recstart() {
        recp = 0;
    }

    public static void recname(String name) {
        gotname = true;
        rname = name;
    }

    static void makedir(int[] dir, boolean[] fire, char d) {
        if (d >= 'A' && d <= 'Z') {
            fire[0] = true;
            d -= 'A' - 'a';
        } else {
            fire[0] = false;
        }
        switch (d) {
            case 's': dir[0] = DIR_NONE; break;
            case 'r': dir[0] = DIR_RIGHT; break;
            case 'u': dir[0] = DIR_UP; break;
            case 'l': dir[0] = DIR_LEFT; break;
            case 'd': dir[0] = DIR_DOWN; break;
        }
    }

    public static void playgetdir(int[] dir, boolean[] fire) {
        if (rlleft > 0) {
            makedir(dir, fire, rld);
            rlleft--;
        } else {
            if (plp < plb.length() && (plb.charAt(plp) == 'E' || plb.charAt(plp) == 'e')) {
                Inp.escape = true;
                return;
            }
            rld = plp < plb.length() ? plb.charAt(plp++) : 's';
            while (plp < plb.length() && plb.charAt(plp) >= '0' && plb.charAt(plp) <= '9') {
                rlleft = rlleft * 10 + (plb.charAt(plp++) - '0');
            }
            makedir(dir, fire, rld);
            if (rlleft > 0) {
                rlleft--;
            }
        }
    }

    static char maked(int dir, boolean fire) {
        char d;
        if (dir == DIR_NONE) {
            d = 's';
        } else {
            d = "ruld".charAt(dir >> 1);
        }
        if (fire) {
            d += 'A' - 'a';
        }
        return d;
    }

    static void putrun() {
        if (recrl > 1) {
            mprintf("" + recd + recrl);
        } else {
            mprintf("" + recd);
        }
        reccc++;
        if (recrl > 1) {
            reccc++;
            if (recrl >= 10) {
                reccc++;
                if (recrl >= 100) reccc++;
            }
        }
        if (reccc >= 60) {
            mprintf("\n");
            reccc = 0;
        }
    }

    public static void recputdir(int dir, boolean fire) {
        char d = maked(dir, fire);
        if (recrl == 0) {
            recd = d;
        }
        if (recd != d) {
            putrun();
            recd = d;
            recrl = 1;
        } else {
            if (recrl == 999) {
                putrun();
                recrl = 0;
            }
            recrl++;
        }
    }

    public static void recinit() {
        recp = 0;
        drfvalid = true;
        mprintf("DRF\n");
        if (kludge) {
            mprintf("AJ DOS 19981125\n");
        } else {
            mprintf(DIGGER_VERSION + "\n");
        }
        if (Main.diggers > 1) {
            mprintf("M" + Main.diggers);
            if (Main.gauntlet) mprintf("G" + Main.gtime);
        } else if (Main.gauntlet) {
            mprintf("G" + Main.gtime);
        } else {
            mprintf("" + Main.nplayers);
        }
        if (Main.startlev > 1) {
            mprintf("I" + Main.startlev);
        }
        mprintf("\n" + Score.bonusscore + "\n");
        for (int l = 0; l < 8; l++) {
            for (int y = 0; y < MHEIGHT; y++) {
                for (int x = 0; x < MWIDTH; x++) {
                    mprintf("" + (char) Main.leveldat[l][y][x]);
                }
                mprintf("\n");
            }
        }
        reccc = recrl = 0;
    }

    public static void recputrand(long randv) {
        mprintf(String.format("%08X\n", randv & 0xffffffffL));
        reccc = recrl = 0;
    }

    public static long playgetrand() {
        long r = 0;
        if (plp < plb.length() && plb.charAt(plp) == '*') {
            plp += 4;
        }
        for (int i = 0; i < 8; i++) {
            char p = plp < plb.length() ? plb.charAt(plp++) : '0';
            if (p >= '0' && p <= '9') r |= (long) (p - '0') << ((7 - i) << 2);
            if (p >= 'A' && p <= 'F') r |= (long) (p - 'A' + 10) << ((7 - i) << 2);
            if (p >= 'a' && p <= 'f') r |= (long) (p - 'a' + 10) << ((7 - i) << 2);
        }
        return r;
    }

    public static void recputinit(String init) {
        mprintf("*" + (init + "...").substring(0, 3) + "\n");
    }

    public static void recputeol() {
        if (recrl > 0) putrun();
        if (reccc > 0) mprintf("\n");
        mprintf("EOL\n");
    }

    public static void recputeog() {
        mprintf("EOG\n");
    }

    public static void playskipeol() {
        plp += 3;
    }

    public static void openplay(String name) {
        int origgtime = Main.gtime;
        boolean origg = Main.gauntlet;
        int origstartlev = Main.startlev, orignplayers = Main.nplayers, origdiggers = Main.diggers;
        try (BufferedReader in = new BufferedReader(new FileReader(name))) {
            rlleft = 0;
            Main.gauntlet = false;
            Main.startlev = 1;
            Main.nplayers = 1;
            Main.diggers = 1;
            String id = in.readLine();
            if (id == null || id.length() < 3 || id.charAt(0) != 'D' || id.charAt(1) != 'R' || id.charAt(2) != 'F') {
                Inp.escape = true;
                return;
            }
            String ver = in.readLine();
            if (ver != null && ver.length() >= 8 && atol(ver.substring(7)) <= 19981125L) {
                kludge = true;
            }
            String mode = in.readLine();
            int x = 0;
            if (mode != null && mode.length() > 0) {
                if (mode.charAt(0) == '1') { Main.nplayers = 1; x = 1; }
                else if (mode.charAt(0) == '2') { Main.nplayers = 2; x = 1; }
                else {
                    if (mode.charAt(0) == 'M') { Main.diggers = mode.charAt(1) - '0'; x = 2; }
                    else x = 0;
                    if (x < mode.length() && mode.charAt(x) == 'G') {
                        Main.gauntlet = true;
                        x++;
                        Main.gtime = atoi(mode.substring(x));
                        while (x < mode.length() && mode.charAt(x) >= '0' && mode.charAt(x) <= '9') x++;
                    }
                }
                if (x < mode.length() && mode.charAt(x) == 'U') x++;
                if (x < mode.length() && mode.charAt(x) == 'I') Main.startlev = atoi(mode.substring(x + 1));
            }
            String bs = in.readLine();
            Score.bonusscore = atoi(bs);
            for (int n = 0; n < 8; n++) {
                for (int y = 0; y < 10; y++) {
                    String line = in.readLine();
                    if (line == null) line = "";
                    for (int xx = 0; xx < 15; xx++) {
                        Main.leveldat[n][y][xx] = (byte) (xx < line.length() ? line.charAt(xx) : ' ');
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                if (c >= ' ') sb.append((char) c);
            }
            plb = sb.toString();
            plp = 0;
        } catch (Exception e) {
            Inp.escape = true;
            return;
        }
        playing = true;
        recinit();
        Main.game();
        gotgame = true;
        playing = false;
        Main.gauntlet = origg;
        Main.gtime = origgtime;
        kludge = false;
        Main.startlev = origstartlev;
        Main.diggers = origdiggers;
        Main.nplayers = orignplayers;
    }

    public static void recsavedrf() {
        if (!drfvalid) return;
        String nambuf;
        if (gotname) {
            nambuf = rname;
        } else if (Main.nplayers == 2) {
            nambuf = DEFAULTSN;
        } else {
            char[] init = new char[3];
            for (int j = 0; j < 3; j++) {
                char ic = Score.scoreinit[0][j];
                init[j] = ((ic >= 'A' && ic <= 'Z') || (ic >= 'a' && ic <= 'z')) ? ic : '_';
            }
            String is = new String(init);
            nambuf = is + Score.scoret + ".drf";
        }
        try (FileWriter out = new FileWriter(nambuf)) {
            for (int i = 0; i < recp; i++) {
                out.write(recb[i]);
            }
        } catch (Exception ignored) {
            return;
        }
        gotgame = false;
    }

    private static long atol(String s) {
        s = s.trim();
        long v = 0;
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            v = v * 10 + (s.charAt(i++) - '0');
        }
        return v;
    }

    private static int atoi(String s) {
        return (int) atol(s);
    }
}
