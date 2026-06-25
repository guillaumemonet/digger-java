package org.digger;

import static org.digger.Def.*;

/**
 * Sprite handling and collision detection, ported from sprite.c.
 *
 * <p>Sprites are drawn with background save/restore (geti/puti) so that moving
 * one sprite correctly repaints any others it overlaps. Collisions are
 * computed from sprite bounding boxes into {@link #first}/{@link #coll}.
 */
public final class Spr {
    private Spr() {}

    static boolean retrflag = true;

    static final boolean[] sprrdrwf = new boolean[SPRITES + 1];
    static final boolean[] sprrecf = new boolean[SPRITES + 1];
    static final boolean[] sprenf = new boolean[SPRITES];
    static final int[] sprch = new int[SPRITES + 1];
    static final byte[][] sprmov = new byte[SPRITES][4096];
    static final int[] sprx = new int[SPRITES + 1];
    static final int[] spry = new int[SPRITES + 1];
    static final int[] sprwid = new int[SPRITES + 1];
    static final int[] sprhei = new int[SPRITES + 1];
    static final int[] sprbwid = new int[SPRITES];
    static final int[] sprbhei = new int[SPRITES];
    static final int[] sprnch = new int[SPRITES];
    static final int[] sprnwid = new int[SPRITES];
    static final int[] sprnhei = new int[SPRITES];
    static final int[] sprnbwid = new int[SPRITES];
    static final int[] sprnbhei = new int[SPRITES];

    public static final int[] first = new int[TYPES];
    public static final int[] coll = new int[SPRITES];
    private static final int[] firstt = {FIRSTBONUS, FIRSTBAG, FIRSTMONSTER, FIRSTFIREBALL, FIRSTDIGGER};
    private static final int[] lastt = {LASTBONUS, LASTBAG, LASTMONSTER, LASTFIREBALL, LASTDIGGER};

    public static void setretr(boolean f) {
        retrflag = f;
    }

    public static void createspr(int n, int ch, int wid, int hei, int bwid, int bhei) {
        sprnch[n] = sprch[n] = ch;
        sprnwid[n] = sprwid[n] = wid;
        sprnhei[n] = sprhei[n] = hei;
        sprnbwid[n] = sprbwid[n] = bwid;
        sprnbhei[n] = sprbhei[n] = bhei;
        sprenf[n] = false;
    }

    public static void movedrawspr(int n, int x, int y) {
        sprx[n] = x & -4;
        spry[n] = y;
        sprch[n] = sprnch[n];
        sprwid[n] = sprnwid[n];
        sprhei[n] = sprnhei[n];
        sprbwid[n] = sprnbwid[n];
        sprbhei[n] = sprnbhei[n];
        clearrdrwf();
        setrdrwflgs(n);
        putis();
        Vid.ggeti(sprx[n], spry[n], sprmov[n], sprwid[n], sprhei[n]);
        sprenf[n] = true;
        sprrdrwf[n] = true;
        putims();
    }

    public static void erasespr(int n) {
        if (!sprenf[n]) {
            return;
        }
        Vid.gputi(sprx[n], spry[n], sprmov[n], sprwid[n], sprhei[n]);
        sprenf[n] = false;
        clearrdrwf();
        setrdrwflgs(n);
        putims();
    }

    public static void drawspr(int n, int x, int y) {
        x &= -4;
        clearrdrwf();
        setrdrwflgs(n);
        int t1 = sprx[n], t2 = spry[n], t3 = sprwid[n], t4 = sprhei[n];
        sprx[n] = x;
        spry[n] = y;
        sprwid[n] = sprnwid[n];
        sprhei[n] = sprnhei[n];
        clearrecf();
        setrdrwflgs(n);
        sprhei[n] = t4;
        sprwid[n] = t3;
        spry[n] = t2;
        sprx[n] = t1;
        sprrdrwf[n] = true;
        putis();
        sprenf[n] = true;
        sprx[n] = x;
        spry[n] = y;
        sprch[n] = sprnch[n];
        sprwid[n] = sprnwid[n];
        sprhei[n] = sprnhei[n];
        sprbwid[n] = sprnbwid[n];
        sprbhei[n] = sprnbhei[n];
        Vid.ggeti(sprx[n], spry[n], sprmov[n], sprwid[n], sprhei[n]);
        putims();
        bcollides(n);
    }

    public static void initspr(int n, int ch, int wid, int hei, int bwid, int bhei) {
        sprnch[n] = ch;
        sprnwid[n] = wid;
        sprnhei[n] = hei;
        sprnbwid[n] = bwid;
        sprnbhei[n] = bhei;
    }

    public static void initmiscspr(int x, int y, int wid, int hei) {
        sprx[SPRITES] = x;
        spry[SPRITES] = y;
        sprwid[SPRITES] = wid;
        sprhei[SPRITES] = hei;
        clearrdrwf();
        setrdrwflgs(SPRITES);
        putis();
    }

    public static void getis() {
        for (int i = 0; i < SPRITES; i++) {
            if (sprrdrwf[i]) {
                Vid.ggeti(sprx[i], spry[i], sprmov[i], sprwid[i], sprhei[i]);
            }
        }
        putims();
    }

    public static void drawmiscspr(int x, int y, int ch, int wid, int hei) {
        sprx[SPRITES] = x & -4;
        spry[SPRITES] = y;
        sprch[SPRITES] = ch;
        sprwid[SPRITES] = wid;
        sprhei[SPRITES] = hei;
        Vid.gputim(sprx[SPRITES], spry[SPRITES], sprch[SPRITES], sprwid[SPRITES], sprhei[SPRITES]);
    }

    static void clearrdrwf() {
        clearrecf();
        for (int i = 0; i < SPRITES + 1; i++) {
            sprrdrwf[i] = false;
        }
    }

    static void clearrecf() {
        for (int i = 0; i < SPRITES + 1; i++) {
            sprrecf[i] = false;
        }
    }

    static void setrdrwflgs(int n) {
        if (!sprrecf[n]) {
            sprrecf[n] = true;
            for (int i = 0; i < SPRITES; i++) {
                if (sprenf[i] && i != n) {
                    if (collide(i, n)) {
                        sprrdrwf[i] = true;
                        setrdrwflgs(i);
                    }
                }
            }
        }
    }

    static boolean collide(int bx, int si) {
        if (sprx[bx] >= sprx[si]) {
            if (sprx[bx] > (sprwid[si] << 2) + sprx[si] - 1) {
                return false;
            }
        } else if (sprx[si] > (sprwid[bx] << 2) + sprx[bx] - 1) {
            return false;
        }
        if (spry[bx] >= spry[si]) {
            return spry[bx] <= sprhei[si] + spry[si] - 1;
        }
        return spry[si] <= sprhei[bx] + spry[bx] - 1;
    }

    static boolean bcollide(int bx, int si) {
        if (sprx[bx] >= sprx[si]) {
            if (sprx[bx] + sprbwid[bx] > (sprwid[si] << 2) + sprx[si] - sprbwid[si] - 1) {
                return false;
            }
        } else if (sprx[si] + sprbwid[si] > (sprwid[bx] << 2) + sprx[bx] - sprbwid[bx] - 1) {
            return false;
        }
        if (spry[bx] >= spry[si]) {
            return spry[bx] + sprbhei[bx] <= sprhei[si] + spry[si] - sprbhei[si] - 1;
        }
        return spry[si] + sprbhei[si] <= sprhei[bx] + spry[bx] - sprbhei[bx] - 1;
    }

    static void putims() {
        for (int i = 0; i < SPRITES; i++) {
            if (sprrdrwf[i]) {
                Vid.gputim(sprx[i], spry[i], sprch[i], sprwid[i], sprhei[i]);
            }
        }
    }

    static void putis() {
        for (int i = 0; i < SPRITES; i++) {
            if (sprrdrwf[i]) {
                Vid.gputi(sprx[i], spry[i], sprmov[i], sprwid[i], sprhei[i]);
            }
        }
    }

    static void bcollides(int spr) {
        for (int next = 0; next < TYPES; next++) {
            first[next] = -1;
        }
        for (int next = 0; next < SPRITES; next++) {
            coll[next] = -1;
        }
        for (int i = 0; i < TYPES; i++) {
            int next = -1;
            for (int spc = firstt[i]; spc < lastt[i]; spc++) {
                if (sprenf[spc] && spc != spr) {
                    if (bcollide(spr, spc)) {
                        if (next == -1) {
                            first[i] = next = spc;
                        } else {
                            coll[next = (coll[next] = spc)] = -1;
                        }
                    }
                }
            }
        }
    }
}
