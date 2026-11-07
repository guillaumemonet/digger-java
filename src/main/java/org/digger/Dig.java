package org.digger;

import static org.digger.Def.*;

/** Diggers and fireballs, ported from digger.c. */
public final class Dig {
    private Dig() {}

    static final class DigData {
        int x, y, h, v, rx, ry, mdir, dir, bagtime, rechargetime, fx, fy, fdir, expsn,
                deathstage, deathbag, deathani, deathtime, emocttime, emn, msc, lives, ivt;
        boolean notfiring, alive, firepressed, dead, levdone, invin;
    }

    static final DigData[] digdat = new DigData[DIGGERS];
    static {
        for (int i = 0; i < DIGGERS; i++) digdat[i] = new DigData();
    }

    static int startbonustimeleft = 0, bonustimeleft;
    static int emmask = 0;
    static final byte[] emfield = new byte[MSIZE];
    public static boolean bonusvisible = false, bonusmode = false, digvisible;

    public static long curtime, ftime;
    public static long cgtime;

    static final int[] deatharc = {3, 5, 6, 6, 5, 3, 0};
    static final int[] embox = {8, 12, 12, 9, 16, 12, 6, 9};

    public static void initdigger() {
        for (int dig = Main.curplayer; dig < Main.diggers + Main.curplayer; dig++) {
            if (digdat[dig].lives == 0) {
                continue;
            }
            DigData d = digdat[dig];
            d.v = 9;
            d.mdir = 4;
            d.h = (Main.diggers == 1) ? 7 : (8 - dig * 2);
            d.x = d.h * 20 + 12;
            d.dir = (dig == 0) ? DIR_RIGHT : DIR_LEFT;
            d.rx = 0;
            d.ry = 0;
            d.bagtime = 0;
            d.alive = true;
            d.dead = false;
            d.invin = false;
            d.ivt = 0;
            d.deathstage = 1;
            d.y = d.v * 18 + 18;
            Spr.movedrawspr(dig + FIRSTDIGGER - Main.curplayer, d.x, d.y);
            d.notfiring = true;
            d.emocttime = 0;
            d.firepressed = false;
            d.expsn = 0;
            d.rechargetime = 0;
            d.emn = 0;
            d.msc = 1;
        }
        digvisible = true;
        bonusvisible = bonusmode = false;
    }

    public static void newframe() {
        long t;
        Hw.waitWhileSuspended();
        if (Main.synchvid) {
            for (; curtime < ftime; curtime += 17094) {
                Snd.fillbuffer();
                Vid.gretrace();
                Inp.checkkeyb();
            }
            curtime -= ftime;
            Snd.fillbuffer();
        } else {
            do {
                Snd.fillbuffer();
                t = Hw.gethrt();
                Inp.checkkeyb();
                if (curtime + ftime > t && t > curtime) {
                    try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                }
            } while (curtime + ftime > t && t > curtime);
            curtime = t;
        }
    }

    static void drawdig(int n, int d, int x, int y, boolean f) {
        Draw.drawdigger(n - Main.curplayer, d, x, y, f);
        if (digdat[n].invin) {
            digdat[n].ivt--;
            if (digdat[n].ivt == 0) {
                digdat[n].invin = false;
            } else if (digdat[n].ivt % 10 < 5) {
                Spr.erasespr(FIRSTDIGGER + n - Main.curplayer);
            }
        }
    }

    public static void dodigger() {
        newframe();
        if (Main.gauntlet) {
            Draw.drawlives();
            if (cgtime < ftime) {
                Main.timeout = true;
            }
            cgtime -= ftime;
        }
        for (int n = Main.curplayer; n < Main.diggers + Main.curplayer; n++) {
            DigData d = digdat[n];
            if (d.expsn != 0) {
                drawexplosion(n);
            } else {
                updatefire(n);
            }
            if (digvisible) {
                if (d.alive) {
                    if (d.bagtime != 0) {
                        drawdig(n, d.mdir, d.x, d.y, d.notfiring && d.rechargetime == 0);
                        Main.incpenalty();
                        d.bagtime--;
                    } else {
                        updatedigger(n);
                    }
                } else {
                    diggerdie(n);
                }
            }
            if (d.emocttime > 0) {
                d.emocttime--;
            }
        }
        if (bonusmode && isalive()) {
            if (bonustimeleft != 0) {
                bonustimeleft--;
                if (startbonustimeleft != 0 || bonustimeleft < 20) {
                    startbonustimeleft--;
                    if ((bonustimeleft & 1) != 0) {
                        Vid.ginten(0);
                        Snd.soundbonus();
                    } else {
                        Vid.ginten(1);
                        Snd.soundbonus();
                    }
                    if (startbonustimeleft == 0) {
                        Snd.music(0);
                        Snd.soundbonusoff();
                        Vid.ginten(1);
                    }
                }
            } else {
                endbonusmode();
                Snd.soundbonusoff();
                Snd.music(1);
            }
        }
        if (bonusmode && !isalive()) {
            endbonusmode();
            Snd.soundbonusoff();
            Snd.music(1);
        }
    }

    public static void updatefire(int n) {
        int pix;
        int[] clfirst = new int[TYPES];
        int[] clcoll = new int[SPRITES];
        boolean clflag;
        DigData d = digdat[n];
        if (d.notfiring) {
            if (d.rechargetime != 0) {
                d.rechargetime--;
            } else if (getfirepflag(n - Main.curplayer)) {
                if (d.alive) {
                    d.rechargetime = Main.levof10() * 3 + 60;
                    d.notfiring = false;
                    switch (d.dir) {
                        case DIR_RIGHT: d.fx = d.x + 8; d.fy = d.y + 4; break;
                        case DIR_UP: d.fx = d.x + 4; d.fy = d.y; break;
                        case DIR_LEFT: d.fx = d.x; d.fy = d.y + 4; break;
                        case DIR_DOWN: d.fx = d.x + 4; d.fy = d.y + 8; break;
                    }
                    d.fdir = d.dir;
                    Spr.movedrawspr(FIRSTFIREBALL + n - Main.curplayer, d.fx, d.fy);
                    Snd.soundfire(n);
                }
            }
        } else {
            pix = 0;
            switch (d.fdir) {
                case DIR_RIGHT:
                    d.fx += 8;
                    pix = Vid.ggetpix(d.fx, d.fy + 4) | Vid.ggetpix(d.fx + 4, d.fy + 4);
                    break;
                case DIR_UP:
                    d.fy -= 7;
                    pix = 0;
                    for (int i = 0; i < 7; i++) pix |= Vid.ggetpix(d.fx + 4, d.fy + i);
                    pix &= 0xc0;
                    break;
                case DIR_LEFT:
                    d.fx -= 8;
                    pix = Vid.ggetpix(d.fx, d.fy + 4) | Vid.ggetpix(d.fx + 4, d.fy + 4);
                    break;
                case DIR_DOWN:
                    d.fy += 7;
                    pix = 0;
                    for (int i = 0; i < 7; i++) pix |= Vid.ggetpix(d.fx, d.fy + i);
                    pix &= 0x3;
                    break;
            }
            Draw.drawfire(n - Main.curplayer, d.fx, d.fy, 0);
            System.arraycopy(Spr.first, 0, clfirst, 0, TYPES);
            System.arraycopy(Spr.coll, 0, clcoll, 0, SPRITES);
            Main.incpenalty();
            int i = clfirst[2];
            while (i != -1) {
                Mon.killmon(i - FIRSTMONSTER);
                Score.scorekill(n);
                d.expsn = 1;
                i = clcoll[i];
            }
            i = clfirst[4];
            while (i != -1) {
                if (i - FIRSTDIGGER + Main.curplayer != n
                        && !digdat[i - FIRSTDIGGER + Main.curplayer].invin
                        && digdat[i - FIRSTDIGGER + Main.curplayer].alive) {
                    killdigger(i - FIRSTDIGGER + Main.curplayer, 3, 0);
                    d.expsn = 1;
                }
                i = clcoll[i];
            }
            clflag = clfirst[0] != -1 || clfirst[1] != -1 || clfirst[2] != -1
                    || clfirst[3] != -1 || clfirst[4] != -1;
            if (clfirst[0] != -1 || clfirst[1] != -1 || clfirst[3] != -1) {
                d.expsn = 1;
                i = clfirst[3];
                while (i != -1) {
                    if (digdat[i - FIRSTFIREBALL + Main.curplayer].expsn == 0) {
                        digdat[i - FIRSTFIREBALL + Main.curplayer].expsn = 1;
                    }
                    i = clcoll[i];
                }
            }
            switch (d.fdir) {
                case DIR_RIGHT:
                    if (d.fx > 296) d.expsn = 1;
                    else if (pix != 0 && !clflag) {
                        d.expsn = 1; d.fx -= 8;
                        Draw.drawfire(n - Main.curplayer, d.fx, d.fy, 0);
                    }
                    break;
                case DIR_UP:
                    if (d.fy < 15) d.expsn = 1;
                    else if (pix != 0 && !clflag) {
                        d.expsn = 1; d.fy += 7;
                        Draw.drawfire(n - Main.curplayer, d.fx, d.fy, 0);
                    }
                    break;
                case DIR_LEFT:
                    if (d.fx < 16) d.expsn = 1;
                    else if (pix != 0 && !clflag) {
                        d.expsn = 1; d.fx += 8;
                        Draw.drawfire(n - Main.curplayer, d.fx, d.fy, 0);
                    }
                    break;
                case DIR_DOWN:
                    if (d.fy > 183) d.expsn = 1;
                    else if (pix != 0 && !clflag) {
                        d.expsn = 1; d.fy -= 7;
                        Draw.drawfire(n - Main.curplayer, d.fx, d.fy, 0);
                    }
                    break;
            }
        }
    }

    public static void erasediggers() {
        for (int i = 0; i < Main.diggers; i++) {
            Spr.erasespr(FIRSTDIGGER + i);
        }
        digvisible = false;
    }

    public static void drawexplosion(int n) {
        switch (digdat[n].expsn) {
            case 1:
                Snd.soundexplode(n);
                // fall through
            case 2:
            case 3:
                Draw.drawfire(n - Main.curplayer, digdat[n].fx, digdat[n].fy, digdat[n].expsn);
                Main.incpenalty();
                digdat[n].expsn++;
                break;
            default:
                killfire(n);
                digdat[n].expsn = 0;
        }
    }

    public static void killfire(int n) {
        if (!digdat[n].notfiring) {
            digdat[n].notfiring = true;
            Spr.erasespr(FIRSTFIREBALL + n - Main.curplayer);
            Snd.soundfireoff(n);
        }
    }

    public static void updatedigger(int n) {
        int dir, ddir, diggerox, diggeroy, nmon;
        boolean push = true, bagf;
        int[] clfirst = new int[TYPES];
        int[] clcoll = new int[SPRITES];
        DigData d = digdat[n];
        Inp.readdir(n - Main.curplayer);
        dir = Inp.getdir(n - Main.curplayer);
        if (dir == DIR_RIGHT || dir == DIR_UP || dir == DIR_LEFT || dir == DIR_DOWN) {
            ddir = dir;
        } else {
            ddir = DIR_NONE;
        }
        if (d.rx == 0 && (ddir == DIR_UP || ddir == DIR_DOWN)) {
            d.dir = d.mdir = ddir;
        }
        if (d.ry == 0 && (ddir == DIR_RIGHT || ddir == DIR_LEFT)) {
            d.dir = d.mdir = ddir;
        }
        if (dir == DIR_NONE) {
            d.mdir = DIR_NONE;
        } else {
            d.mdir = d.dir;
        }
        if ((d.x == 292 && d.mdir == DIR_RIGHT) || (d.x == 12 && d.mdir == DIR_LEFT)
                || (d.y == 180 && d.mdir == DIR_DOWN) || (d.y == 18 && d.mdir == DIR_UP)) {
            d.mdir = DIR_NONE;
        }
        diggerox = d.x;
        diggeroy = d.y;
        if (d.mdir != DIR_NONE) {
            Draw.eatfield(diggerox, diggeroy, d.mdir);
        }
        switch (d.mdir) {
            case DIR_RIGHT: Draw.drawrightblob(d.x, d.y); d.x += 4; break;
            case DIR_UP: Draw.drawtopblob(d.x, d.y); d.y -= 3; break;
            case DIR_LEFT: Draw.drawleftblob(d.x, d.y); d.x -= 4; break;
            case DIR_DOWN: Draw.drawbottomblob(d.x, d.y); d.y += 3; break;
        }
        if (hitemerald((d.x - 12) / 20, (d.y - 18) / 18, (d.x - 12) % 20, (d.y - 18) % 18, d.mdir)) {
            if (d.emocttime == 0) {
                d.emn = 0;
            }
            Score.scoreemerald(n);
            Snd.soundem();
            Snd.soundemerald(d.emn);
            d.emn++;
            if (d.emn == 8) {
                d.emn = 0;
                Score.scoreoctave(n);
            }
            d.emocttime = 9;
        }
        drawdig(n, d.dir, d.x, d.y, d.notfiring && d.rechargetime == 0);
        System.arraycopy(Spr.first, 0, clfirst, 0, TYPES);
        System.arraycopy(Spr.coll, 0, clcoll, 0, SPRITES);
        Main.incpenalty();

        int i = clfirst[1];
        bagf = false;
        while (i != -1) {
            if (Bag.bagexist(i - FIRSTBAG)) {
                bagf = true;
                break;
            }
            i = clcoll[i];
        }

        if (bagf) {
            if (d.mdir == DIR_RIGHT || d.mdir == DIR_LEFT) {
                push = Bag.pushbags(d.mdir, clfirst, clcoll);
                d.bagtime++;
            } else if (!Bag.pushudbags(clfirst, clcoll)) {
                push = false;
            }
            if (!push) {
                d.x = diggerox;
                d.y = diggeroy;
                drawdig(n, d.mdir, d.x, d.y, d.notfiring && d.rechargetime == 0);
                Main.incpenalty();
                d.dir = reversedir(d.mdir);
            }
        }
        if (clfirst[2] != -1 && bonusmode && d.alive) {
            for (nmon = Mon.killmonsters(clfirst, clcoll); nmon != 0; nmon--) {
                Snd.soundeatm();
                sceatm(n);
            }
        }
        if (clfirst[0] != -1) {
            Score.scorebonus(n);
            initbonusmode();
        }
        d.h = (d.x - 12) / 20;
        d.rx = (d.x - 12) % 20;
        d.v = (d.y - 18) / 18;
        d.ry = (d.y - 18) % 18;
    }

    public static void sceatm(int n) {
        Score.scoreeatm(n, digdat[n].msc);
        digdat[n].msc <<= 1;
    }

    public static void diggerdie(int n) {
        int[] clfirst = new int[TYPES];
        int[] clcoll = new int[SPRITES];
        boolean alldead;
        DigData d = digdat[n];
        switch (d.deathstage) {
            case 1:
                if (Bag.bagy(d.deathbag) + 6 > d.y) {
                    d.y = Bag.bagy(d.deathbag) + 6;
                }
                Draw.drawdigger(n - Main.curplayer, 15, d.x, d.y, false);
                Main.incpenalty();
                if (Bag.getbagdir(d.deathbag) + 1 == 0) {
                    Snd.soundddie();
                    d.deathtime = 5;
                    d.deathstage = 2;
                    d.deathani = 0;
                    d.y -= 6;
                }
                break;
            case 2:
                if (d.deathtime != 0) {
                    d.deathtime--;
                    break;
                }
                if (d.deathani == 0) {
                    Snd.music(2);
                }
                Draw.drawdigger(n - Main.curplayer, 14 - d.deathani, d.x, d.y, false);
                System.arraycopy(Spr.first, 0, clfirst, 0, TYPES);
                System.arraycopy(Spr.coll, 0, clcoll, 0, SPRITES);
                Main.incpenalty();
                if (d.deathani == 0 && clfirst[2] != -1) {
                    Mon.killmonsters(clfirst, clcoll);
                }
                if (d.deathani < 4) {
                    d.deathani++;
                    d.deathtime = 2;
                } else {
                    d.deathstage = 4;
                    if (Snd.musicflag || Main.diggers > 1) {
                        d.deathtime = 60;
                    } else {
                        d.deathtime = 10;
                    }
                }
                break;
            case 3:
                d.deathstage = 5;
                d.deathani = 0;
                d.deathtime = 0;
                break;
            case 5:
                if (d.deathani >= 0 && d.deathani <= 6) {
                    Draw.drawdigger(n - Main.curplayer, 15, d.x, d.y - deatharc[d.deathani], false);
                    if (d.deathani == 6 && !isalive()) {
                        Snd.musicoff();
                    }
                    Main.incpenalty();
                    d.deathani++;
                    if (d.deathani == 1) {
                        Snd.soundddie();
                    }
                    if (d.deathani == 7) {
                        d.deathtime = 5;
                        d.deathani = 0;
                        d.deathstage = 2;
                    }
                }
                break;
            case 4:
                if (d.deathtime != 0) {
                    d.deathtime--;
                } else {
                    d.dead = true;
                    alldead = true;
                    for (int i = 0; i < Main.diggers; i++) {
                        if (!digdat[i].dead) {
                            alldead = false;
                            break;
                        }
                    }
                    if (alldead) {
                        Main.setdead(true);
                    } else if (isalive() && d.lives > 0) {
                        if (!Main.gauntlet) {
                            d.lives--;
                        }
                        Draw.drawlives();
                        if (d.lives > 0) {
                            d.v = 9;
                            d.mdir = 4;
                            d.h = (Main.diggers == 1) ? 7 : (8 - n * 2);
                            d.x = d.h * 20 + 12;
                            d.dir = (n == 0) ? DIR_RIGHT : DIR_LEFT;
                            d.rx = 0;
                            d.ry = 0;
                            d.bagtime = 0;
                            d.alive = true;
                            d.dead = false;
                            d.invin = true;
                            d.ivt = 50;
                            d.deathstage = 1;
                            d.y = d.v * 18 + 18;
                            Spr.erasespr(n + FIRSTDIGGER - Main.curplayer);
                            Spr.movedrawspr(n + FIRSTDIGGER - Main.curplayer, d.x, d.y);
                            d.notfiring = true;
                            d.emocttime = 0;
                            d.firepressed = false;
                            d.expsn = 0;
                            d.rechargetime = 0;
                            d.emn = 0;
                            d.msc = 1;
                        }
                        Inp.clearfire(n);
                        if (bonusmode) {
                            Snd.music(0);
                        } else {
                            Snd.music(1);
                        }
                    }
                }
                break;
        }
    }

    public static void createbonus() {
        bonusvisible = true;
        Draw.drawbonus(292, 18);
    }

    public static void initbonusmode() {
        bonusmode = true;
        erasebonus();
        Vid.ginten(1);
        bonustimeleft = 250 - Main.levof10() * 20;
        startbonustimeleft = 20;
        for (int i = 0; i < Main.diggers; i++) {
            digdat[i].msc = 1;
        }
    }

    public static void endbonusmode() {
        bonusmode = false;
        Vid.ginten(0);
    }

    public static void erasebonus() {
        if (bonusvisible) {
            bonusvisible = false;
            Spr.erasespr(FIRSTBONUS);
        }
        Vid.ginten(0);
    }

    public static int reversedir(int dir) {
        switch (dir) {
            case DIR_RIGHT: return DIR_LEFT;
            case DIR_LEFT: return DIR_RIGHT;
            case DIR_UP: return DIR_DOWN;
            case DIR_DOWN: return DIR_UP;
        }
        return dir;
    }

    public static boolean checkdiggerunderbag(int h, int v) {
        for (int n = Main.curplayer; n < Main.diggers + Main.curplayer; n++) {
            DigData d = digdat[n];
            if (d.alive && (d.mdir == DIR_UP || d.mdir == DIR_DOWN)) {
                if ((d.x - 12) / 20 == h) {
                    if ((d.y - 18) / 18 == v || (d.y - 18) / 18 + 1 == v) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void killdigger(int n, int stage, int bag) {
        if (digdat[n].invin) {
            return;
        }
        if (digdat[n].deathstage < 2 || digdat[n].deathstage > 4) {
            digdat[n].alive = false;
            digdat[n].deathstage = stage;
            digdat[n].deathbag = bag;
        }
    }

    public static void makeemfield() {
        emmask = 1 << Main.curplayer;
        for (int x = 0; x < MWIDTH; x++) {
            for (int y = 0; y < MHEIGHT; y++) {
                if (Main.getlevch(x, y, Main.levplan()) == 'C') {
                    emfield[y * MWIDTH + x] |= emmask;
                } else {
                    emfield[y * MWIDTH + x] &= ~emmask;
                }
            }
        }
    }

    public static void drawemeralds() {
        emmask = 1 << Main.curplayer;
        for (int x = 0; x < MWIDTH; x++) {
            for (int y = 0; y < MHEIGHT; y++) {
                if ((emfield[y * MWIDTH + x] & emmask) != 0) {
                    Draw.drawemerald(x * 20 + 12, y * 18 + 21);
                }
            }
        }
    }

    public static boolean hitemerald(int x, int y, int rx, int ry, int dir) {
        boolean hit = false;
        int r;
        if (dir != DIR_RIGHT && dir != DIR_UP && dir != DIR_LEFT && dir != DIR_DOWN) {
            return hit;
        }
        if (dir == DIR_RIGHT && rx != 0) x++;
        if (dir == DIR_DOWN && ry != 0) y++;
        if (dir == DIR_RIGHT || dir == DIR_LEFT) r = rx; else r = ry;
        if ((emfield[y * MWIDTH + x] & emmask) != 0) {
            if (r == embox[dir]) {
                Draw.drawemerald(x * 20 + 12, y * 18 + 21);
                Main.incpenalty();
            }
            if (r == embox[dir + 1]) {
                Draw.eraseemerald(x * 20 + 12, y * 18 + 21);
                Main.incpenalty();
                hit = true;
                emfield[y * MWIDTH + x] &= ~emmask;
            }
        }
        return hit;
    }

    public static int countem() {
        int n = 0;
        for (int x = 0; x < MWIDTH; x++) {
            for (int y = 0; y < MHEIGHT; y++) {
                if ((emfield[y * MWIDTH + x] & emmask) != 0) {
                    n++;
                }
            }
        }
        return n;
    }

    public static void killemerald(int x, int y) {
        if ((emfield[(y + 1) * MWIDTH + x] & emmask) != 0) {
            emfield[(y + 1) * MWIDTH + x] &= ~emmask;
            Draw.eraseemerald(x * 20 + 12, (y + 1) * 18 + 21);
        }
    }

    static boolean getfirepflag(int n) {
        return n == 0 ? Inp.firepflag : Inp.fire2pflag;
    }

    public static int diggerx(int n) { return digdat[n].x; }
    public static int diggery(int n) { return digdat[n].y; }
    public static boolean digalive(int n) { return digdat[n].alive; }
    public static void digresettime(int n) { digdat[n].bagtime = 0; }

    public static boolean isalive() {
        for (int i = Main.curplayer; i < Main.diggers + Main.curplayer; i++) {
            if (digdat[i].alive) {
                return true;
            }
        }
        return false;
    }

    public static int getlives(int pl) { return digdat[pl].lives; }

    public static void addlife(int pl) {
        digdat[pl].lives++;
        Snd.sound1up();
    }

    public static void initlives() {
        for (int i = 0; i < Main.diggers + Main.nplayers - 1; i++) {
            digdat[i].lives = 3;
        }
    }

    public static void declife(int pl) {
        if (!Main.gauntlet) {
            digdat[pl].lives--;
        }
    }
}
