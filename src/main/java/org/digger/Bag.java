package org.digger;

import static org.digger.Def.*;

/** Gold and money bags, ported from bags.c. */
public final class Bag {
    private Bag() {}

    static final class BagData {
        int x, y, h, v, xr, yr, dir, wt, gt, fallh;
        boolean wobbling, unfallen, exist;
        void copyFrom(BagData o) {
            x = o.x; y = o.y; h = o.h; v = o.v; xr = o.xr; yr = o.yr; dir = o.dir;
            wt = o.wt; gt = o.gt; fallh = o.fallh;
            wobbling = o.wobbling; unfallen = o.unfallen; exist = o.exist;
        }
    }

    static final BagData[] bagdat1 = newArray();
    static final BagData[] bagdat2 = newArray();
    static final BagData[] bagdat = newArray();

    private static BagData[] newArray() {
        BagData[] a = new BagData[BAGS];
        for (int i = 0; i < BAGS; i++) a[i] = new BagData();
        return a;
    }

    static int pushcount = 0, goldtime = 0;

    static final int[] wblanim = {2, 0, 1, 0};

    public static void initbags() {
        int bag, x, y;
        pushcount = 0;
        goldtime = 150 - Main.levof10() * 10;
        for (bag = 0; bag < BAGS; bag++) {
            bagdat[bag].exist = false;
        }
        bag = 0;
        for (x = 0; x < MWIDTH; x++) {
            for (y = 0; y < MHEIGHT; y++) {
                if (Main.getlevch(x, y, Main.levplan()) == 'B') {
                    if (bag < BAGS) {
                        BagData b = bagdat[bag];
                        b.exist = true;
                        b.gt = 0;
                        b.fallh = 0;
                        b.dir = DIR_NONE;
                        b.wobbling = false;
                        b.wt = 15;
                        b.unfallen = true;
                        b.x = x * 20 + 12;
                        b.y = y * 18 + 18;
                        b.h = x;
                        b.v = y;
                        b.xr = 0;
                        b.yr = 0;
                        bag++;
                    }
                }
            }
        }
        for (int i = 0; i < BAGS; i++) {
            if (Main.curplayer == 0) bagdat1[i].copyFrom(bagdat[i]);
            else bagdat2[i].copyFrom(bagdat[i]);
        }
    }

    public static void drawbags() {
        for (int bag = 0; bag < BAGS; bag++) {
            if (Main.curplayer == 0) bagdat[bag].copyFrom(bagdat1[bag]);
            else bagdat[bag].copyFrom(bagdat2[bag]);
            if (bagdat[bag].exist) {
                Spr.movedrawspr(bag + FIRSTBAG, bagdat[bag].x, bagdat[bag].y);
            }
        }
    }

    public static void cleanupbags() {
        Snd.soundfalloff();
        for (int bag = 0; bag < BAGS; bag++) {
            BagData b = bagdat[bag];
            if (b.exist && ((b.h == 7 && b.v == 9) || b.xr != 0 || b.yr != 0
                    || b.gt != 0 || b.fallh != 0 || b.wobbling)) {
                b.exist = false;
                Spr.erasespr(bag + FIRSTBAG);
            }
            if (Main.curplayer == 0) bagdat1[bag].copyFrom(b);
            else bagdat2[bag].copyFrom(b);
        }
    }

    public static void dobags() {
        boolean soundfalloffflag = true, soundwobbleoffflag = true;
        for (int bag = 0; bag < BAGS; bag++) {
            BagData b = bagdat[bag];
            if (b.exist) {
                if (b.gt != 0) {
                    if (b.gt == 1) {
                        Snd.soundbreak();
                        Draw.drawgold(bag, 4, b.x, b.y);
                        Main.incpenalty();
                    }
                    if (b.gt == 3) {
                        Draw.drawgold(bag, 5, b.x, b.y);
                        Main.incpenalty();
                    }
                    if (b.gt == 5) {
                        Draw.drawgold(bag, 6, b.x, b.y);
                        Main.incpenalty();
                    }
                    b.gt++;
                    if (b.gt == goldtime) {
                        removebag(bag);
                    } else if (b.v < MHEIGHT - 1 && b.gt < goldtime - 10) {
                        if ((Mon.getfield(b.h, b.v + 1) & 0x2000) == 0) {
                            b.gt = goldtime - 10;
                        }
                    }
                } else {
                    updatebag(bag);
                }
            }
        }
        for (int bag = 0; bag < BAGS; bag++) {
            BagData b = bagdat[bag];
            if (b.dir == DIR_DOWN && b.exist) {
                soundfalloffflag = false;
            }
            if (b.dir != DIR_DOWN && b.wobbling && b.exist) {
                soundwobbleoffflag = false;
            }
        }
        if (soundfalloffflag) Snd.soundfalloff();
        if (soundwobbleoffflag) Snd.soundwobbleoff();
    }

    static void updatebag(int bag) {
        int x, h, xr, y, v, yr, wbl;
        BagData b = bagdat[bag];
        x = b.x; h = b.h; xr = b.xr; y = b.y; v = b.v; yr = b.yr;
        switch (b.dir) {
            case DIR_NONE:
                if (y < 180 && xr == 0) {
                    if (b.wobbling) {
                        if (b.wt == 0) {
                            b.dir = DIR_DOWN;
                            Snd.soundfall();
                            break;
                        }
                        b.wt--;
                        wbl = b.wt % 8;
                        if ((wbl & 1) == 0) {
                            Draw.drawgold(bag, wblanim[wbl >> 1], x, y);
                            Main.incpenalty();
                            Snd.soundwobble();
                        }
                    } else if ((Mon.getfield(h, v + 1) & 0xfdf) != 0xfdf) {
                        if (!Dig.checkdiggerunderbag(h, v + 1)) {
                            b.wobbling = true;
                        }
                    }
                } else {
                    b.wt = 15;
                    b.wobbling = false;
                }
                break;
            case DIR_RIGHT:
            case DIR_LEFT:
                if (xr == 0) {
                    if (y < 180 && (Mon.getfield(h, v + 1) & 0xfdf) != 0xfdf) {
                        b.dir = DIR_DOWN;
                        b.wt = 0;
                        Snd.soundfall();
                    } else {
                        baghitground(bag);
                    }
                }
                break;
            case DIR_DOWN:
                if (yr == 0) b.fallh++;
                if (y >= 180) {
                    baghitground(bag);
                } else if ((Mon.getfield(h, v + 1) & 0xfdf) == 0xfdf) {
                    if (yr == 0) baghitground(bag);
                }
                Mon.checkmonscared(b.h);
                break;
        }
        if (b.dir != DIR_NONE) {
            if (b.dir != DIR_DOWN && pushcount != 0) {
                pushcount--;
            } else {
                pushbag(bag, b.dir);
            }
        }
    }

    static void baghitground(int bag) {
        int[] clfirst = new int[TYPES];
        int[] clcoll = new int[SPRITES];
        BagData b = bagdat[bag];
        if (b.dir == DIR_DOWN && b.fallh > 1) {
            b.gt = 1;
        } else {
            b.fallh = 0;
        }
        b.dir = DIR_NONE;
        b.wt = 15;
        b.wobbling = false;
        Draw.drawgold(bag, 0, b.x, b.y);
        System.arraycopy(Spr.first, 0, clfirst, 0, TYPES);
        System.arraycopy(Spr.coll, 0, clcoll, 0, SPRITES);
        Main.incpenalty();
        int i = clfirst[1];
        while (i != -1) {
            removebag(i - FIRSTBAG);
            i = clcoll[i];
        }
    }

    static boolean pushbag(int bag, int dir) {
        int x, y, h, v, ox, oy;
        int[] clfirst = new int[TYPES];
        int[] clcoll = new int[SPRITES];
        boolean push = true, digf;
        BagData b = bagdat[bag];
        ox = x = b.x;
        oy = y = b.y;
        h = b.h;
        v = b.v;
        if (b.gt != 0) {
            getgold(bag);
            return true;
        }
        if (b.dir == DIR_DOWN && (dir == DIR_RIGHT || dir == DIR_LEFT)) {
            Draw.drawgold(bag, 3, x, y);
            System.arraycopy(Spr.first, 0, clfirst, 0, TYPES);
            System.arraycopy(Spr.coll, 0, clcoll, 0, SPRITES);
            Main.incpenalty();
            int i = clfirst[4];
            while (i != -1) {
                if (Dig.diggery(i - FIRSTDIGGER + Main.curplayer) >= y) {
                    Dig.killdigger(i - FIRSTDIGGER + Main.curplayer, 1, bag);
                }
                i = clcoll[i];
            }
            if (clfirst[2] != -1) {
                Mon.squashmonsters(bag, clfirst, clcoll);
            }
            return true;
        }
        if ((x == 292 && dir == DIR_RIGHT) || (x == 12 && dir == DIR_LEFT)
                || (y == 180 && dir == DIR_DOWN) || (y == 18 && dir == DIR_UP)) {
            push = false;
        }
        if (push) {
            switch (dir) {
                case DIR_RIGHT: x += 4; break;
                case DIR_LEFT: x -= 4; break;
                case DIR_DOWN:
                    if (b.unfallen) {
                        b.unfallen = false;
                        Draw.drawsquareblob(x, y);
                        Draw.drawtopblob(x, y + 21);
                    } else {
                        Draw.drawfurryblob(x, y);
                    }
                    Draw.eatfield(x, y, dir);
                    Dig.killemerald(h, v);
                    y += 6;
                    break;
            }
            switch (dir) {
                case DIR_DOWN: {
                    Draw.drawgold(bag, 3, x, y);
                    System.arraycopy(Spr.first, 0, clfirst, 0, TYPES);
                    System.arraycopy(Spr.coll, 0, clcoll, 0, SPRITES);
                    Main.incpenalty();
                    int i = clfirst[4];
                    while (i != -1) {
                        if (Dig.diggery(i - FIRSTDIGGER + Main.curplayer) >= y) {
                            Dig.killdigger(i - FIRSTDIGGER + Main.curplayer, 1, bag);
                        }
                        i = clcoll[i];
                    }
                    if (clfirst[2] != -1) {
                        Mon.squashmonsters(bag, clfirst, clcoll);
                    }
                    break;
                }
                case DIR_RIGHT:
                case DIR_LEFT: {
                    b.wt = 15;
                    b.wobbling = false;
                    Draw.drawgold(bag, 0, x, y);
                    System.arraycopy(Spr.first, 0, clfirst, 0, TYPES);
                    System.arraycopy(Spr.coll, 0, clcoll, 0, SPRITES);
                    Main.incpenalty();
                    pushcount = 1;
                    if (clfirst[1] != -1) {
                        if (!pushbags(dir, clfirst, clcoll)) {
                            x = ox; y = oy;
                            Draw.drawgold(bag, 0, ox, oy);
                            Main.incpenalty();
                            push = false;
                        }
                    }
                    int i = clfirst[4];
                    digf = false;
                    while (i != -1) {
                        if (Dig.digalive(i - FIRSTDIGGER + Main.curplayer)) {
                            digf = true;
                        }
                        i = clcoll[i];
                    }
                    if (digf || clfirst[2] != -1) {
                        x = ox; y = oy;
                        Draw.drawgold(bag, 0, ox, oy);
                        Main.incpenalty();
                        push = false;
                    }
                    break;
                }
            }
            if (push) {
                b.dir = dir;
            } else {
                b.dir = Dig.reversedir(dir);
            }
            b.x = x;
            b.y = y;
            b.h = (x - 12) / 20;
            b.v = (y - 18) / 18;
            b.xr = (x - 12) % 20;
            b.yr = (y - 18) % 18;
        }
        return push;
    }

    public static boolean pushbags(int dir, int[] clfirst, int[] clcoll) {
        boolean push = true;
        int next = clfirst[1];
        while (next != -1) {
            if (!pushbag(next - FIRSTBAG, dir)) {
                push = false;
            }
            next = clcoll[next];
        }
        return push;
    }

    public static boolean pushudbags(int[] clfirst, int[] clcoll) {
        boolean push = true;
        int next = clfirst[1];
        while (next != -1) {
            if (bagdat[next - FIRSTBAG].gt != 0) {
                getgold(next - FIRSTBAG);
            } else {
                push = false;
            }
            next = clcoll[next];
        }
        return push;
    }

    static void removebag(int bag) {
        if (bagdat[bag].exist) {
            bagdat[bag].exist = false;
            Spr.erasespr(bag + FIRSTBAG);
        }
    }

    public static boolean bagexist(int bag) {
        return bagdat[bag].exist;
    }

    public static int bagy(int bag) {
        return bagdat[bag].y;
    }

    public static int getbagdir(int bag) {
        if (bagdat[bag].exist) {
            return bagdat[bag].dir;
        }
        return -1;
    }

    public static void removebags(int[] clfirst, int[] clcoll) {
        int next = clfirst[1];
        while (next != -1) {
            removebag(next - FIRSTBAG);
            next = clcoll[next];
        }
    }

    public static int getnmovingbags() {
        int n = 0;
        for (int bag = 0; bag < BAGS; bag++) {
            BagData b = bagdat[bag];
            if (b.exist && b.gt < 10 && (b.gt != 0 || b.wobbling)) {
                n++;
            }
        }
        return n;
    }

    static void getgold(int bag) {
        boolean f = true;
        Draw.drawgold(bag, 6, bagdat[bag].x, bagdat[bag].y);
        Main.incpenalty();
        int i = Spr.first[4];
        while (i != -1) {
            if (Dig.digalive(i - FIRSTDIGGER + Main.curplayer)) {
                Score.scoregold(i - FIRSTDIGGER + Main.curplayer);
                Snd.soundgold();
                Dig.digresettime(i - FIRSTDIGGER + Main.curplayer);
                f = false;
            }
            i = Spr.coll[i];
        }
        if (f) {
            Mon.mongold();
        }
        removebag(bag);
    }
}
