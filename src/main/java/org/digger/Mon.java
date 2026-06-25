package org.digger;

import static org.digger.Def.*;

/** Nobbins and Hobbins, ported from monster.c. */
public final class Mon {
    private Mon() {}

    static final class MonData {
        int x, y, h, v, xr, yr, dir, hdir, t, hnt, death, bag, dtime, stime, chase;
        boolean flag, nob, alive;
    }

    static final MonData[] mondat = new MonData[MONSTERS];
    static {
        for (int i = 0; i < MONSTERS; i++) mondat[i] = new MonData();
    }

    static int nextmonster = 0, totalmonsters = 0, maxmononscr = 0, nextmontime = 0, mongaptime = 0;
    static int chase = 0;
    static boolean unbonusflag = false;
    static boolean mongotgold = false;

    public static void initmonsters() {
        for (int i = 0; i < MONSTERS; i++) {
            mondat[i].flag = false;
        }
        nextmonster = 0;
        mongaptime = 45 - (Main.levof10() << 1);
        totalmonsters = Main.levof10() + 5;
        switch (Main.levof10()) {
            case 1: maxmononscr = 3; break;
            case 2: case 3: case 4: case 5: case 6: case 7: maxmononscr = 4; break;
            case 8: case 9: case 10: maxmononscr = 5; break;
        }
        nextmontime = 10;
        unbonusflag = true;
    }

    public static void erasemonsters() {
        for (int i = 0; i < MONSTERS; i++) {
            if (mondat[i].flag) {
                Spr.erasespr(i + FIRSTMONSTER);
            }
        }
    }

    public static void domonsters() {
        if (nextmontime > 0) {
            nextmontime--;
        } else {
            if (nextmonster < totalmonsters && nmononscr() < maxmononscr && Dig.isalive() && !Dig.bonusmode) {
                createmonster();
            }
            if (unbonusflag && nextmonster == totalmonsters && nextmontime == 0) {
                if (Dig.isalive()) {
                    unbonusflag = false;
                    Dig.createbonus();
                }
            }
        }
        for (int i = 0; i < MONSTERS; i++) {
            if (mondat[i].flag) {
                if (mondat[i].hnt > 10 - Main.levof10()) {
                    if (mondat[i].nob) {
                        mondat[i].nob = false;
                        mondat[i].hnt = 0;
                    }
                }
                if (mondat[i].alive) {
                    if (mondat[i].t == 0) {
                        monai(i);
                        if (Main.randno(15 - Main.levof10()) == 0) {
                            if (mondat[i].nob && mondat[i].alive) {
                                monai(i);
                            }
                        }
                    } else {
                        mondat[i].t--;
                    }
                } else {
                    mondie(i);
                }
            }
        }
    }

    static void createmonster() {
        for (int i = 0; i < MONSTERS; i++) {
            if (!mondat[i].flag) {
                MonData m = mondat[i];
                m.flag = true;
                m.alive = true;
                m.t = 0;
                m.nob = true;
                m.hnt = 0;
                m.h = 14;
                m.v = 0;
                m.x = 292;
                m.y = 18;
                m.xr = 0;
                m.yr = 0;
                m.dir = DIR_LEFT;
                m.hdir = DIR_LEFT;
                m.chase = chase + Main.curplayer;
                chase = (chase + 1) % Main.diggers;
                nextmonster++;
                nextmontime = mongaptime;
                m.stime = 5;
                Spr.movedrawspr(i + FIRSTMONSTER, m.x, m.y);
                break;
            }
        }
    }

    public static void mongold() {
        mongotgold = true;
    }

    static void monai(int mon) {
        int monox, monoy, dir, mdirp1, mdirp2, mdirp3, mdirp4, t;
        int[] clcoll = new int[SPRITES];
        int[] clfirst = new int[TYPES];
        boolean push, bagf;
        MonData mo = mondat[mon];
        monox = mo.x;
        monoy = mo.y;
        if (mo.xr == 0 && mo.yr == 0) {
            if (mo.hnt > 30 + (Main.levof10() << 1)) {
                if (!mo.nob) {
                    mo.hnt = 0;
                    mo.nob = true;
                }
            }
            int dig = mo.chase;
            if (!Dig.digalive(dig)) {
                dig = (Main.diggers - 1) - dig;
            }
            if (Math.abs(Dig.diggery(dig) - mo.y) > Math.abs(Dig.diggerx(dig) - mo.x)) {
                if (Dig.diggery(dig) < mo.y) { mdirp1 = DIR_UP; mdirp4 = DIR_DOWN; }
                else { mdirp1 = DIR_DOWN; mdirp4 = DIR_UP; }
                if (Dig.diggerx(dig) < mo.x) { mdirp2 = DIR_LEFT; mdirp3 = DIR_RIGHT; }
                else { mdirp2 = DIR_RIGHT; mdirp3 = DIR_LEFT; }
            } else {
                if (Dig.diggerx(dig) < mo.x) { mdirp1 = DIR_LEFT; mdirp4 = DIR_RIGHT; }
                else { mdirp1 = DIR_RIGHT; mdirp4 = DIR_LEFT; }
                if (Dig.diggery(dig) < mo.y) { mdirp2 = DIR_UP; mdirp3 = DIR_DOWN; }
                else { mdirp2 = DIR_DOWN; mdirp3 = DIR_UP; }
            }
            if (Dig.bonusmode) {
                t = mdirp1; mdirp1 = mdirp4; mdirp4 = t;
                t = mdirp2; mdirp2 = mdirp3; mdirp3 = t;
            }
            dir = Dig.reversedir(mo.dir);
            if (dir == mdirp1) {
                mdirp1 = mdirp2; mdirp2 = mdirp3; mdirp3 = mdirp4; mdirp4 = dir;
            }
            if (dir == mdirp2) {
                mdirp2 = mdirp3; mdirp3 = mdirp4; mdirp4 = dir;
            }
            if (dir == mdirp3) {
                mdirp3 = mdirp4; mdirp4 = dir;
            }
            if (Main.randno(Main.levof10() + 5) == 1) {
                if (Main.levof10() < 6) {
                    t = mdirp1; mdirp1 = mdirp3; mdirp3 = t;
                }
            }
            if (fieldclear(mdirp1, mo.h, mo.v)) dir = mdirp1;
            else if (fieldclear(mdirp2, mo.h, mo.v)) dir = mdirp2;
            else if (fieldclear(mdirp3, mo.h, mo.v)) dir = mdirp3;
            else if (fieldclear(mdirp4, mo.h, mo.v)) dir = mdirp4;
            if (!mo.nob) dir = mdirp1;
            if (mo.dir != dir) mo.t++;
            mo.dir = dir;
        }

        if ((mo.x == 292 && mo.dir == DIR_RIGHT) || (mo.x == 12 && mo.dir == DIR_LEFT)
                || (mo.y == 180 && mo.dir == DIR_DOWN) || (mo.y == 18 && mo.dir == DIR_UP)) {
            mo.dir = DIR_NONE;
        }
        if (mo.dir == DIR_LEFT || mo.dir == DIR_RIGHT) {
            mo.hdir = mo.dir;
        }
        if (!mo.nob) {
            Draw.eatfield(mo.x, mo.y, mo.dir);
        }
        switch (mo.dir) {
            case DIR_RIGHT: if (!mo.nob) Draw.drawrightblob(mo.x, mo.y); mo.x += 4; break;
            case DIR_UP: if (!mo.nob) Draw.drawtopblob(mo.x, mo.y); mo.y -= 3; break;
            case DIR_LEFT: if (!mo.nob) Draw.drawleftblob(mo.x, mo.y); mo.x -= 4; break;
            case DIR_DOWN: if (!mo.nob) Draw.drawbottomblob(mo.x, mo.y); mo.y += 3; break;
        }
        if (!mo.nob) {
            Dig.hitemerald((mo.x - 12) / 20, (mo.y - 18) / 18, (mo.x - 12) % 20, (mo.y - 18) % 18, mo.dir);
        }
        if (!Dig.isalive()) {
            mo.x = monox;
            mo.y = monoy;
        }
        if (mo.stime != 0) {
            mo.stime--;
            mo.x = monox;
            mo.y = monoy;
        }
        if (!mo.nob && mo.hnt < 100) {
            mo.hnt++;
        }
        push = true;
        Draw.drawmon(mon, mo.nob, mo.hdir, mo.x, mo.y);
        System.arraycopy(Spr.first, 0, clfirst, 0, TYPES);
        System.arraycopy(Spr.coll, 0, clcoll, 0, SPRITES);
        Main.incpenalty();

        if (clfirst[2] != -1) {
            mo.t++;
            int i = clfirst[2];
            do {
                int m = i - FIRSTMONSTER;
                if (mo.dir == mondat[m].dir && mondat[m].stime == 0 && mo.stime == 0) {
                    mondat[m].dir = Dig.reversedir(mondat[m].dir);
                }
                if (!Rec.kludge) {
                    Main.incpenalty();
                } else if ((m & 1) == 0) {
                    Main.incpenalty();
                }
                i = clcoll[i];
            } while (i != -1);
            if (Rec.kludge) {
                if (clfirst[0] != -1) {
                    Main.incpenalty();
                }
            }
        }

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
            mo.t++;
            mongotgold = false;
            if (mo.dir == DIR_RIGHT || mo.dir == DIR_LEFT) {
                push = Bag.pushbags(mo.dir, clfirst, clcoll);
                mo.t++;
            } else if (!Bag.pushudbags(clfirst, clcoll)) {
                push = false;
            }
            if (mongotgold) {
                mo.t = 0;
            }
            if (!mo.nob && mo.hnt > 1) {
                Bag.removebags(clfirst, clcoll);
            }
        }

        if (mo.nob && clfirst[2] != -1 && Dig.isalive()) {
            mo.hnt++;
        }

        if (!push) {
            mo.x = monox;
            mo.y = monoy;
            Draw.drawmon(mon, mo.nob, mo.hdir, mo.x, mo.y);
            Main.incpenalty();
            if (mo.nob) {
                mo.hnt++;
            }
            if ((mo.dir == DIR_UP || mo.dir == DIR_DOWN) && mo.nob) {
                mo.dir = Dig.reversedir(mo.dir);
            }
        }

        if (clfirst[4] != -1 && Dig.isalive()) {
            if (Dig.bonusmode) {
                Mon.killmon(mon);
                i = clfirst[4];
                while (i != -1) {
                    if (Dig.digalive(i - FIRSTDIGGER + Main.curplayer)) {
                        Dig.sceatm(i - FIRSTDIGGER + Main.curplayer);
                    }
                    i = clcoll[i];
                }
                Snd.soundeatm();
            } else {
                i = clfirst[4];
                while (i != -1) {
                    if (Dig.digalive(i - FIRSTDIGGER + Main.curplayer)) {
                        Dig.killdigger(i - FIRSTDIGGER + Main.curplayer, 3, 0);
                    }
                    i = clcoll[i];
                }
            }
        }

        mo.h = (mo.x - 12) / 20;
        mo.v = (mo.y - 18) / 18;
        mo.xr = (mo.x - 12) % 20;
        mo.yr = (mo.y - 18) % 18;
    }

    static void mondie(int mon) {
        MonData mo = mondat[mon];
        switch (mo.death) {
            case 1:
                if (Bag.bagy(mo.bag) + 6 > mo.y) {
                    mo.y = Bag.bagy(mo.bag);
                }
                Draw.drawmondie(mon, mo.nob, mo.hdir, mo.x, mo.y);
                Main.incpenalty();
                if (Bag.getbagdir(mo.bag) == -1) {
                    mo.dtime = 1;
                    mo.death = 4;
                }
                break;
            case 4:
                if (mo.dtime != 0) {
                    mo.dtime--;
                } else {
                    killmon(mon);
                    if (Main.diggers == 2) {
                        Score.scorekill2();
                    } else {
                        Score.scorekill(Main.curplayer);
                    }
                }
                break;
        }
    }

    static boolean fieldclear(int dir, int x, int y) {
        switch (dir) {
            case DIR_RIGHT:
                if (x < 14) if ((getfield(x + 1, y) & 0x2000) == 0)
                    if ((getfield(x + 1, y) & 1) == 0 || (getfield(x, y) & 0x10) == 0) return true;
                break;
            case DIR_UP:
                if (y > 0) if ((getfield(x, y - 1) & 0x2000) == 0)
                    if ((getfield(x, y - 1) & 0x800) == 0 || (getfield(x, y) & 0x40) == 0) return true;
                break;
            case DIR_LEFT:
                if (x > 0) if ((getfield(x - 1, y) & 0x2000) == 0)
                    if ((getfield(x - 1, y) & 0x10) == 0 || (getfield(x, y) & 1) == 0) return true;
                break;
            case DIR_DOWN:
                if (y < 9) if ((getfield(x, y + 1) & 0x2000) == 0)
                    if ((getfield(x, y + 1) & 0x40) == 0 || (getfield(x, y) & 0x800) == 0) return true;
                break;
        }
        return false;
    }

    public static void checkmonscared(int h) {
        for (int m = 0; m < MONSTERS; m++) {
            if (h == mondat[m].h && mondat[m].dir == DIR_UP) {
                mondat[m].dir = DIR_DOWN;
            }
        }
    }

    public static void killmon(int mon) {
        if (mondat[mon].flag) {
            mondat[mon].flag = mondat[mon].alive = false;
            Spr.erasespr(mon + FIRSTMONSTER);
            if (Dig.bonusmode) {
                totalmonsters++;
            }
        }
    }

    public static void squashmonsters(int bag, int[] clfirst, int[] clcoll) {
        int next = clfirst[2];
        while (next != -1) {
            int m = next - FIRSTMONSTER;
            if (mondat[m].y >= Bag.bagy(bag)) {
                squashmonster(m, 1, bag);
            }
            next = clcoll[next];
        }
    }

    public static int killmonsters(int[] clfirst, int[] clcoll) {
        int next = clfirst[2], n = 0;
        while (next != -1) {
            int m = next - FIRSTMONSTER;
            killmon(m);
            n++;
            next = clcoll[next];
        }
        return n;
    }

    static void squashmonster(int mon, int death, int bag) {
        mondat[mon].alive = false;
        mondat[mon].death = death;
        mondat[mon].bag = bag;
    }

    public static int monleft() {
        return nmononscr() + totalmonsters - nextmonster;
    }

    static int nmononscr() {
        int n = 0;
        for (int i = 0; i < MONSTERS; i++) {
            if (mondat[i].flag) n++;
        }
        return n;
    }

    public static void incmont(int n) {
        if (n > MONSTERS) n = MONSTERS;
        for (int m = 1; m < n; m++) {
            mondat[m].t++;
        }
    }

    public static int getfield(int x, int y) {
        return Draw.field[y * 15 + x];
    }
}
