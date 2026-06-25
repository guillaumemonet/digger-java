package org.digger;

import static org.digger.Def.*;

/** High-level drawing routines and the dig field, ported from drawing.c. */
public final class Draw {
    private Draw() {}

    public static final int[] field1 = new int[MSIZE];
    public static final int[] field2 = new int[MSIZE];
    public static final int[] field = new int[MSIZE];

    static final int[] bitmasks = {0xfffe, 0xfffd, 0xfffb, 0xfff7, 0xffef, 0xffdf,
            0xffbf, 0xff7f, 0xfeff, 0xfdff, 0xfbff, 0xf7ff};

    static final int[] monspr = new int[MONSTERS];
    static final int[] monspd = new int[MONSTERS];
    static final int[] digspr = new int[DIGGERS];
    static final int[] digspd = new int[DIGGERS];
    static final int[] firespr = new int[FIREBALLS];

    public static int fireheight;

    public static void outtext(String p, int x, int y, int c) {
        for (int i = 0; i < p.length(); i++) {
            Vid.gwrite(x, y, p.charAt(i), c);
            x += 12;
        }
    }

    public static void makefield() {
        for (int x = 0; x < MWIDTH; x++) {
            for (int y = 0; y < MHEIGHT; y++) {
                field[y * MWIDTH + x] = -1;
                int c = Main.getlevch(x, y, Main.levplan());
                if (c == 'S' || c == 'V') {
                    field[y * MWIDTH + x] &= 0xd03f;
                }
                if (c == 'S' || c == 'H') {
                    field[y * MWIDTH + x] &= 0xdfe0;
                }
                if (Main.curplayer == 0) {
                    field1[y * MWIDTH + x] = field[y * MWIDTH + x];
                } else {
                    field2[y * MWIDTH + x] = field[y * MWIDTH + x];
                }
            }
        }
    }

    public static void drawstatics() {
        for (int x = 0; x < MWIDTH; x++) {
            for (int y = 0; y < MHEIGHT; y++) {
                field[y * MWIDTH + x] = (Main.curplayer == 0)
                        ? field1[y * MWIDTH + x] : field2[y * MWIDTH + x];
            }
        }
        Spr.setretr(true);
        Vid.gpal(0);
        Vid.ginten(0);
        drawbackg(Main.levplan());
        drawfield();
    }

    public static void savefield() {
        for (int x = 0; x < MWIDTH; x++) {
            for (int y = 0; y < MHEIGHT; y++) {
                if (Main.curplayer == 0) {
                    field1[y * MWIDTH + x] = field[y * MWIDTH + x];
                } else {
                    field2[y * MWIDTH + x] = field[y * MWIDTH + x];
                }
            }
        }
    }

    static void drawfield() {
        for (int x = 0; x < MWIDTH; x++) {
            for (int y = 0; y < MHEIGHT; y++) {
                if ((field[y * MWIDTH + x] & 0x2000) == 0) {
                    int xp = x * 20 + 12;
                    int yp = y * 18 + 18;
                    if ((field[y * MWIDTH + x] & 0xfc0) != 0xfc0) {
                        field[y * MWIDTH + x] &= 0xd03f;
                        drawbottomblob(xp, yp - 15);
                        drawbottomblob(xp, yp - 12);
                        drawbottomblob(xp, yp - 9);
                        drawbottomblob(xp, yp - 6);
                        drawbottomblob(xp, yp - 3);
                        drawtopblob(xp, yp + 3);
                    }
                    if ((field[y * MWIDTH + x] & 0x1f) != 0x1f) {
                        field[y * MWIDTH + x] &= 0xdfe0;
                        drawrightblob(xp - 16, yp);
                        drawrightblob(xp - 12, yp);
                        drawrightblob(xp - 8, yp);
                        drawrightblob(xp - 4, yp);
                        drawleftblob(xp + 4, yp);
                    }
                    if (x < 14) {
                        if ((field[y * MWIDTH + x + 1] & 0xfdf) != 0xfdf) {
                            drawrightblob(xp, yp);
                        }
                    }
                    if (y < 9) {
                        if ((field[(y + 1) * MWIDTH + x] & 0xfdf) != 0xfdf) {
                            drawbottomblob(xp, yp);
                        }
                    }
                }
            }
        }
    }

    public static void eatfield(int x, int y, int dir) {
        int h = (x - 12) / 20, xr = ((x - 12) % 20) / 4, v = (y - 18) / 18, yr = ((y - 18) % 18) / 3;
        Main.incpenalty();
        switch (dir) {
            case DIR_RIGHT:
                h++;
                field[v * MWIDTH + h] &= bitmasks[xr];
                if ((field[v * MWIDTH + h] & 0x1f) != 0) break;
                field[v * MWIDTH + h] &= 0xdfff;
                break;
            case DIR_UP:
                yr--;
                if (yr < 0) { yr += 6; v--; }
                field[v * MWIDTH + h] &= bitmasks[6 + yr];
                if ((field[v * MWIDTH + h] & 0xfc0) != 0) break;
                field[v * MWIDTH + h] &= 0xdfff;
                break;
            case DIR_LEFT:
                xr--;
                if (xr < 0) { xr += 5; h--; }
                field[v * MWIDTH + h] &= bitmasks[xr];
                if ((field[v * MWIDTH + h] & 0x1f) != 0) break;
                field[v * MWIDTH + h] &= 0xdfff;
                break;
            case DIR_DOWN:
                v++;
                field[v * MWIDTH + h] &= bitmasks[6 + yr];
                if ((field[v * MWIDTH + h] & 0xfc0) != 0) break;
                field[v * MWIDTH + h] &= 0xdfff;
                break;
        }
    }

    public static void creatembspr() {
        for (int i = 0; i < BAGS; i++) {
            Spr.createspr(FIRSTBAG + i, 62, 4, 15, 0, 0);
        }
        for (int i = 0; i < MONSTERS; i++) {
            Spr.createspr(FIRSTMONSTER + i, 71, 4, 15, 0, 0);
        }
        createdbfspr();
        for (int i = 0; i < MONSTERS; i++) {
            monspr[i] = 0;
            monspd[i] = 1;
        }
    }

    public static void initmbspr() {
        for (int i = 0; i < BAGS; i++) {
            Spr.initspr(FIRSTBAG + i, 62, 4, 15, 0, 0);
        }
        for (int i = 0; i < MONSTERS; i++) {
            Spr.initspr(FIRSTMONSTER + i, 71, 4, 15, 0, 0);
        }
        initdbfspr();
    }

    public static void drawmon(int n, boolean nobf, int dir, int x, int y) {
        monspr[n] += monspd[n];
        if (monspr[n] == 2 || monspr[n] == 0) {
            monspd[n] = -monspd[n];
        }
        if (monspr[n] > 2) monspr[n] = 2;
        if (monspr[n] < 0) monspr[n] = 0;
        if (nobf) {
            Spr.initspr(FIRSTMONSTER + n, monspr[n] + 69, 4, 15, 0, 0);
        } else {
            switch (dir) {
                case DIR_RIGHT: Spr.initspr(FIRSTMONSTER + n, monspr[n] + 73, 4, 15, 0, 0); break;
                case DIR_LEFT: Spr.initspr(FIRSTMONSTER + n, monspr[n] + 77, 4, 15, 0, 0); break;
            }
        }
        Spr.drawspr(FIRSTMONSTER + n, x, y);
    }

    public static void drawmondie(int n, boolean nobf, int dir, int x, int y) {
        if (nobf) {
            Spr.initspr(FIRSTMONSTER + n, 72, 4, 15, 0, 0);
        } else {
            switch (dir) {
                case DIR_RIGHT: Spr.initspr(FIRSTMONSTER + n, 76, 4, 15, 0, 0); break;
                case DIR_LEFT: Spr.initspr(FIRSTMONSTER + n, 80, 4, 14, 0, 0); break;
            }
        }
        Spr.drawspr(FIRSTMONSTER + n, x, y);
    }

    public static void drawgold(int n, int t, int x, int y) {
        Spr.initspr(FIRSTBAG + n, t + 62, 4, 15, 0, 0);
        Spr.drawspr(FIRSTBAG + n, x, y);
    }

    static void drawlife(int t, int x, int y) {
        Spr.drawmiscspr(x, y, t + 110, 4, 12);
    }

    public static void drawemerald(int x, int y) {
        Spr.initmiscspr(x, y, 4, 10);
        Spr.drawmiscspr(x, y, 108, 4, 10);
        Spr.getis();
    }

    public static void eraseemerald(int x, int y) {
        Spr.initmiscspr(x, y, 4, 10);
        Spr.drawmiscspr(x, y, 109, 4, 10);
        Spr.getis();
    }

    static void createdbfspr() {
        for (int i = 0; i < DIGGERS; i++) {
            digspd[i] = 1;
            digspr[i] = 0;
        }
        for (int i = 0; i < FIREBALLS; i++) {
            firespr[i] = 0;
        }
        for (int i = FIRSTDIGGER; i < LASTDIGGER; i++) {
            Spr.createspr(i, 0, 4, 15, 0, 0);
        }
        for (int i = FIRSTBONUS; i < LASTBONUS; i++) {
            Spr.createspr(i, 81, 4, 15, 0, 0);
        }
        for (int i = FIRSTFIREBALL; i < LASTFIREBALL; i++) {
            Spr.createspr(i, 82, 2, 8, 0, 0);
        }
    }

    static void initdbfspr() {
        for (int i = 0; i < DIGGERS; i++) {
            digspd[i] = 1;
            digspr[i] = 0;
        }
        for (int i = 0; i < FIREBALLS; i++) {
            firespr[i] = 0;
        }
        for (int i = FIRSTDIGGER; i < LASTDIGGER; i++) {
            Spr.initspr(i, 0, 4, 15, 0, 0);
        }
        for (int i = FIRSTBONUS; i < LASTBONUS; i++) {
            Spr.initspr(i, 81, 4, 15, 0, 0);
        }
        for (int i = FIRSTFIREBALL; i < LASTFIREBALL; i++) {
            Spr.initspr(i, 82, 2, 8, 0, 0);
        }
    }

    public static void drawrightblob(int x, int y) {
        Spr.initmiscspr(x + 16, y - 1, 2, 18);
        Spr.drawmiscspr(x + 16, y - 1, 102, 2, 18);
        Spr.getis();
    }

    public static void drawleftblob(int x, int y) {
        Spr.initmiscspr(x - 8, y - 1, 2, 18);
        Spr.drawmiscspr(x - 8, y - 1, 104, 2, 18);
        Spr.getis();
    }

    public static void drawtopblob(int x, int y) {
        Spr.initmiscspr(x - 4, y - 6, 6, 6);
        Spr.drawmiscspr(x - 4, y - 6, 103, 6, 6);
        Spr.getis();
    }

    public static void drawbottomblob(int x, int y) {
        Spr.initmiscspr(x - 4, y + 15, 6, 6);
        Spr.drawmiscspr(x - 4, y + 15, 105, 6, 6);
        Spr.getis();
    }

    public static void drawfurryblob(int x, int y) {
        Spr.initmiscspr(x - 4, y + 15, 6, 8);
        Spr.drawmiscspr(x - 4, y + 15, 107, 6, 8);
        Spr.getis();
    }

    public static void drawsquareblob(int x, int y) {
        Spr.initmiscspr(x - 4, y + 17, 6, 6);
        Spr.drawmiscspr(x - 4, y + 17, 106, 6, 6);
        Spr.getis();
    }

    static void drawbackg(int l) {
        for (int y = 14; y < 200; y += 4) {
            Snd.fillbuffer();
            for (int x = 0; x < 320; x += 20) {
                Spr.drawmiscspr(x, y, 93 + l, 5, 4);
            }
        }
    }

    public static void drawfire(int n, int x, int y, int t) {
        int nn = (n == 0) ? 0 : 32;
        if (t == 0) {
            firespr[n]++;
            if (firespr[n] > 2) firespr[n] = 0;
            Spr.initspr(FIRSTFIREBALL + n, 82 + firespr[n] + nn, 2, 8, 0, 0);
        } else {
            Spr.initspr(FIRSTFIREBALL + n, 84 + t + nn, 2, 8, 0, 0);
        }
        Spr.drawspr(FIRSTFIREBALL + n, x, y);
    }

    public static void drawbonus(int x, int y) {
        int n = 0;
        Spr.initspr(FIRSTBONUS + n, 81, 4, 15, 0, 0);
        Spr.movedrawspr(FIRSTBONUS + n, x, y);
    }

    public static void drawdigger(int n, int t, int x, int y, boolean f) {
        int nn = (n == 0) ? 0 : 31;
        digspr[n] += digspd[n];
        if (digspr[n] == 2 || digspr[n] == 0) {
            digspd[n] = -digspd[n];
        }
        if (digspr[n] > 2) digspr[n] = 2;
        if (digspr[n] < 0) digspr[n] = 0;
        if (t >= 0 && t <= 6 && (t & 1) == 0) {
            Spr.initspr(FIRSTDIGGER + n, (t + (f ? 0 : 1)) * 3 + digspr[n] + 1 + nn, 4, 15, 0, 0);
            Spr.drawspr(FIRSTDIGGER + n, x, y);
            return;
        }
        if (t >= 10 && t <= 15) {
            Spr.initspr(FIRSTDIGGER + n, 40 + nn - t, 4, 15, 0, 0);
            Spr.drawspr(FIRSTDIGGER + n, x, y);
            return;
        }
        Spr.first[0] = Spr.first[1] = Spr.first[2] = Spr.first[3] = Spr.first[4] = -1;
    }

    public static void drawlives() {
        int l, n, g;
        if (Main.gauntlet) {
            g = (int) (Dig.cgtime / 1193181L);
            outtext(String.format("%3d:%02d", g / 60, g % 60), 124, 0, 3);
            return;
        }
        n = Dig.getlives(0) - 1;
        outtext("     ", 96, 0, 2);
        if (n > 4) {
            drawlife(0, 80, 0);
            outtext("X" + n, 100, 0, 2);
        } else {
            for (l = 1; l < 5; l++) {
                drawlife(n > 0 ? 0 : 2, l * 20 + 60, 0);
                n--;
            }
        }
        if (Main.nplayers == 2) {
            outtext("     ", 164, 0, 2);
            n = Dig.getlives(1) - 1;
            if (n > 4) {
                String buf = n + "X";
                outtext(buf, 220 - buf.length() * 12, 0, 2);
                drawlife(1, 224, 0);
            } else {
                for (l = 1; l < 5; l++) {
                    drawlife(n > 0 ? 1 : 2, 244 - l * 20, 0);
                    n--;
                }
            }
        }
        if (Main.diggers == 2) {
            outtext("     ", 164, 0, 1);
            n = Dig.getlives(1) - 1;
            if (n > 4) {
                String buf = n + "X";
                outtext(buf, 220 - buf.length() * 12, 0, 1);
                drawlife(3, 224, 0);
            } else {
                for (l = 1; l < 5; l++) {
                    drawlife(n > 0 ? 3 : 2, 244 - l * 20, 0);
                    n--;
                }
            }
        }
    }
}
