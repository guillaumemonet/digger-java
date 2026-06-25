package org.digger;

import static org.digger.Def.*;

/** Program shell and game loop, ported from main.c. */
public final class Main {
    private Main() {}

    static final int[] gamedatLevel = new int[2];
    static final boolean[] gamedatLevdone = new boolean[2];

    public static final char[] pldispbuf = new char[14];

    public static int curplayer = 0, nplayers = 1, penalty = 0, diggers = 1, startlev = 1;

    public static boolean levnotdrawn = false, alldead = false, unlimlives = false, started;

    public static String levfname = "";
    public static boolean levfflag = false;
    public static int gtime = 0;
    public static boolean gauntlet = false, timeout = false, synchvid = false;

    public static long randv;

    private static final String[][] LEVELS = {
        {"S   B     HHHHS","V  CC  C  V B  ","VB CC  C  V    ","V  CCB CB V CCC",
         "V  CC  C  V CCC","HH CC  C  V CCC"," V    B B V    "," HHHH     V    ",
         "C   V     V   C","CC  HHHHHHH  CC"},
        {"SHHHHH  B B  HS"," CC  V       V "," CC  V CCCCC V ","BCCB V CCCCC V ",
         "CCCC V       V ","CCCC V B  HHHH "," CC  V CC V    "," BB  VCCCCV CC ",
         "C    V CC V CC ","CC   HHHHHH    "},
        {"SHHHHB B BHHHHS","CC  V C C V BB ","C   V C C V CC "," BB V C C VCCCC",
         "CCCCV C C VCCCC","CCCCHHHHHHH CC "," CC  C V C  CC "," CC  C V C     ",
         "C    C V C    C","CC   C H C   CC"},
        {"SHBCCCCBCCCCBHS","CV  CCCCCCC  VC","CHHH CCCCC HHHC","C  V  CCC  V  C",
         "   HHH C HHH   ","  B  V B V  B  ","  C  VCCCV  C  "," CCC HHHHH CCC ",
         "CCCCC CVC CCCCC","CCCCC CHC CCCCC"},
        {"SHHHHHHHHHHHHHS","VBCCCCBVCCCCCCV","VCCCCCCV CCBC V","V CCCC VCCBCCCV",
         "VCCCCCCV CCCC V","V CCCC VBCCCCCV","VCCBCCCV CCCC V","V CCBC VCCCCCCV",
         "VCCCCCCVCCCCCCV","HHHHHHHHHHHHHHH"},
        {"SHHHHHHHHHHHHHS","VCBCCV V VCCBCV","VCCC VBVBV CCCV","VCCCHH V HHCCCV",
         "VCC V CVC V CCV","VCCHH CVC HHCCV","VC V CCVCC V CV","VCHHBCCVCCBHHCV",
         "VCVCCCCVCCCCVCV","HHHHHHHHHHHHHHH"},
        {"SHCCCCCVCCCCCHS"," VCBCBCVCBCBCV ","BVCCCCCVCCCCCVB","CHHCCCCVCCCCHHC",
         "CCV CCCVCCC VCC","CCHHHCCVCCHHHCC","CCCCV CVC VCCCC","CCCCHH V HHCCCC",
         "CCCCCV V VCCCCC","CCCCCHHHHHCCCCC"},
        {"HHHHHHHHHHHHHHS","V CCBCCCCCBCC V","HHHCCCCBCCCCHHH","VBV CCCCCCC VBV",
         "VCHHHCCCCCHHHCV","VCCBV CCC VBCCV","VCCCHHHCHHHCCCV","VCCCC V V CCCCV",
         "VCCCCCV VCCCCCV","HHHHHHHHHHHHHHH"}};

    public static final byte[][][] leveldat = new byte[8][MHEIGHT][MWIDTH];
    static {
        for (int l = 0; l < 8; l++) {
            for (int y = 0; y < MHEIGHT; y++) {
                for (int x = 0; x < MWIDTH; x++) {
                    leveldat[l][y][x] = (byte) LEVELS[l][y].charAt(x);
                }
            }
        }
    }

    public static int getlevch(int x, int y, int l) {
        if ((l == 3 || l == 4) && !levfflag && diggers == 2 && y == 9 && (x == 6 || x == 8)) {
            return 'H';
        }
        // The original reads leveldat[-1] (harmless garbage) when an unused
        // second player's level is still 0; clamp to avoid an out-of-bounds.
        if (l < 1) l = 1;
        return leveldat[l - 1][y][x] & 0xff;
    }

    public static void game() {
        int t, c, i;
        boolean flashplayer = false;
        if (gauntlet) {
            Dig.cgtime = gtime * 1193181L;
            timeout = false;
        }
        Dig.initlives();
        gamedatLevel[0] = startlev;
        if (nplayers == 2) {
            gamedatLevel[1] = startlev;
        }
        alldead = false;
        Vid.gclear();
        curplayer = 0;
        initlevel();
        curplayer = 1;
        initlevel();
        Score.zeroscores();
        Dig.bonusvisible = true;
        if (nplayers == 2) {
            flashplayer = true;
        }
        curplayer = 0;
        while (getalllives() != 0 && !Inp.escape && !timeout) {
            while (!alldead && !Inp.escape && !timeout) {
                Draw.initmbspr();

                if (Rec.playing) {
                    randv = Rec.playgetrand();
                } else {
                    randv = Hw.getlrt();
                }
                Rec.recputrand(randv);
                if (levnotdrawn) {
                    levnotdrawn = false;
                    drawscreen();
                    if (flashplayer) {
                        flashplayer = false;
                        String pl = "PLAYER " + (curplayer == 0 ? "1" : "2");
                        cleartopline();
                        for (t = 0; t < 15; t++) {
                            for (c = 1; c <= 3; c++) {
                                Draw.outtext(pl, 108, 0, c);
                                Score.writecurscore(c);
                                Dig.newframe();
                                if (Inp.escape) {
                                    return;
                                }
                            }
                        }
                        Score.drawscores();
                        for (i = 0; i < diggers; i++) {
                            Score.addscore(i, 0);
                        }
                    }
                } else {
                    initchars();
                }
                Draw.outtext("        ", 108, 0, 3);
                Score.initscores();
                Draw.drawlives();
                Snd.music(1);

                Inp.flushkeybuf();
                for (i = 0; i < diggers; i++) {
                    Inp.readdir(i);
                }
                while (!alldead && !gamedatLevdone[curplayer] && !Inp.escape && !timeout) {
                    penalty = 0;
                    Dig.dodigger();
                    Mon.domonsters();
                    Bag.dobags();
                    if (penalty > 8) {
                        Mon.incmont(penalty - 8);
                    }
                    testpause();
                    checklevdone();
                }
                Dig.erasediggers();
                Snd.musicoff();
                t = 20;
                while ((Bag.getnmovingbags() != 0 || t != 0) && !Inp.escape && !timeout) {
                    if (t != 0) t--;
                    penalty = 0;
                    Bag.dobags();
                    Dig.dodigger();
                    Mon.domonsters();
                    if (penalty < 8) t = 0;
                }
                Snd.soundstop();
                for (i = 0; i < diggers; i++) {
                    Dig.killfire(i);
                }
                Dig.erasebonus();
                Bag.cleanupbags();
                Draw.savefield();
                Mon.erasemonsters();
                Rec.recputeol();
                if (Rec.playing) {
                    Rec.playskipeol();
                }
                if (Inp.escape) {
                    Rec.recputeog();
                }
                if (gamedatLevdone[curplayer]) {
                    Snd.soundlevdone();
                }
                if (Dig.countem() == 0 || gamedatLevdone[curplayer]) {
                    for (i = curplayer; i < diggers + curplayer; i++) {
                        if (Dig.getlives(i) > 0 && !Dig.digalive(i)) {
                            Dig.declife(i);
                        }
                    }
                    Draw.drawlives();
                    gamedatLevel[curplayer]++;
                    if (gamedatLevel[curplayer] > 1000) {
                        gamedatLevel[curplayer] = 1000;
                    }
                    initlevel();
                } else if (alldead) {
                    for (i = curplayer; i < curplayer + diggers; i++) {
                        if (Dig.getlives(i) > 0) {
                            Dig.declife(i);
                        }
                    }
                    Draw.drawlives();
                }
                if ((alldead && getalllives() == 0 && !gauntlet && !Inp.escape) || timeout) {
                    Score.endofgame();
                }
            }
            alldead = false;
            if (nplayers == 2 && Dig.getlives(1 - curplayer) != 0) {
                curplayer = 1 - curplayer;
                flashplayer = levnotdrawn = true;
            }
        }
    }

    public static void maininit() {
        calibrate();
        Vid.ginit();
        Vid.gpal(0);
        Spr.setretr(true);
        Hw.initkeyb();
        Inp.detectjoy();
        inir();
        Snd.initsound();
        Rec.recstart();
    }

    static int frame;

    public static int mainprog() {
        int t, x = 0;
        Score.loadscores();
        Inp.escape = false;
        do {
            Snd.soundstop();
            Draw.creatembspr();
            Inp.detectjoy();
            Vid.gclear();
            Vid.gtitle();
            Draw.outtext("D I G G E R", 100, 0, 3);
            shownplayers();
            Score.showtable();
            started = false;
            frame = 0;
            Dig.newframe();
            Inp.teststart();
            while (!started && !Inp.escape) {
                started = Inp.teststart();
                if ((Inp.akeypressed == 27 || Inp.akeypressed == 'n' || Inp.akeypressed == 'N')
                        && !gauntlet && diggers == 1) {
                    switchnplayers();
                    shownplayers();
                    Inp.akeypressed = 0;
                }
                if (frame == 0) {
                    for (t = 54; t < 174; t += 12) {
                        Draw.outtext("            ", 164, t, 0);
                    }
                }
                if (frame == 50) { Spr.movedrawspr(FIRSTMONSTER, 292, 63); x = 292; }
                if (frame > 50 && frame <= 77) { x -= 4; Draw.drawmon(0, true, DIR_LEFT, x, 63); }
                if (frame > 77) Draw.drawmon(0, true, DIR_RIGHT, 184, 63);
                if (frame == 83) Draw.outtext("NOBBIN", 216, 64, 2);
                if (frame == 90) { Spr.movedrawspr(FIRSTMONSTER + 1, 292, 82); Draw.drawmon(1, false, DIR_LEFT, 292, 82); x = 292; }
                if (frame > 90 && frame <= 117) { x -= 4; Draw.drawmon(1, false, DIR_LEFT, x, 82); }
                if (frame > 117) Draw.drawmon(1, false, DIR_RIGHT, 184, 82);
                if (frame == 123) Draw.outtext("HOBBIN", 216, 83, 2);
                if (frame == 130) { Spr.movedrawspr(FIRSTDIGGER, 292, 101); Draw.drawdigger(0, DIR_LEFT, 292, 101, true); x = 292; }
                if (frame > 130 && frame <= 157) { x -= 4; Draw.drawdigger(0, DIR_LEFT, x, 101, true); }
                if (frame > 157) Draw.drawdigger(0, DIR_RIGHT, 184, 101, true);
                if (frame == 163) Draw.outtext("DIGGER", 216, 102, 2);
                if (frame == 178) { Spr.movedrawspr(FIRSTBAG, 184, 120); Draw.drawgold(0, 0, 184, 120); }
                if (frame == 183) Draw.outtext("GOLD", 216, 121, 2);
                if (frame == 198) Draw.drawemerald(184, 141);
                if (frame == 203) Draw.outtext("EMERALD", 216, 140, 2);
                if (frame == 218) Draw.drawbonus(184, 158);
                if (frame == 223) Draw.outtext("BONUS", 216, 159, 2);
                Dig.newframe();
                frame++;
                if (frame > 250) frame = 0;
            }
            if (Rec.savedrf) {
                if (Rec.gotgame) {
                    Rec.recsavedrf();
                }
                Rec.savedrf = false;
                continue;
            }
            if (Inp.escape) {
                break;
            }
            Rec.recinit();
            game();
            Rec.gotgame = true;
            if (Rec.gotname) {
                Rec.recsavedrf();
            }
            Rec.savedrf = false;
            Inp.escape = false;
        } while (!Inp.escape);
        finish();
        return 0;
    }

    public static void finish() {
        Snd.killsound();
        Snd.soundoff();
        Snd.soundkillglob();
        Hw.restorekeyb();
        Vid.graphicsoff();
    }

    static void shownplayers() {
        if (diggers == 2) {
            if (gauntlet) {
                Draw.outtext("TWO PLAYER", 180, 25, 3);
                Draw.outtext("GAUNTLET", 192, 39, 3);
            } else {
                Draw.outtext("TWO PLAYER", 180, 25, 3);
                Draw.outtext("SIMULTANEOUS", 170, 39, 3);
            }
        } else if (gauntlet) {
            Draw.outtext("GAUNTLET", 192, 25, 3);
            Draw.outtext("MODE", 216, 39, 3);
        } else if (nplayers == 1) {
            Draw.outtext("ONE", 220, 25, 3);
            Draw.outtext(" PLAYER ", 192, 39, 3);
        } else {
            Draw.outtext("TWO", 220, 25, 3);
            Draw.outtext(" PLAYERS", 184, 39, 3);
        }
    }

    public static int getalllives() {
        int t = 0;
        for (int i = curplayer; i < diggers + curplayer; i++) {
            t += Dig.getlives(i);
        }
        return t;
    }

    static void switchnplayers() {
        nplayers = 3 - nplayers;
    }

    static void initlevel() {
        gamedatLevdone[curplayer] = false;
        Draw.makefield();
        Dig.makeemfield();
        Bag.initbags();
        levnotdrawn = true;
    }

    static void drawscreen() {
        Draw.creatembspr();
        Draw.drawstatics();
        Bag.drawbags();
        Dig.drawemeralds();
        Dig.initdigger();
        Mon.initmonsters();
    }

    static void initchars() {
        Draw.initmbspr();
        Dig.initdigger();
        Mon.initmonsters();
    }

    static void checklevdone() {
        gamedatLevdone[curplayer] = (Dig.countem() == 0 || Mon.monleft() == 0) && Dig.isalive();
    }

    public static void incpenalty() {
        penalty++;
    }

    public static void cleartopline() {
        Draw.outtext("                          ", 0, 0, 3);
        Draw.outtext(" ", 308, 0, 3);
    }

    public static int levplan() {
        int l = levno();
        if (l > 8) {
            l = (l & 3) + 5;
        }
        return l;
    }

    public static int levof10() {
        if (gamedatLevel[curplayer] > 10) {
            return 10;
        }
        return gamedatLevel[curplayer];
    }

    static int levno() {
        return gamedatLevel[curplayer];
    }

    public static void setdead(boolean df) {
        alldead = df;
    }

    static void testpause() {
        if (Inp.pausef) {
            Inp.pausef = false;
            Snd.soundpause();
            Snd.sett2val(40);
            Snd.setsoundt2();
            cleartopline();
            Draw.outtext("PRESS ANY KEY", 80, 0, 1);
            while (!Hw.kbhit() && !Inp.escape) {
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
            Hw.getkey();
            cleartopline();
            Score.drawscores();
            for (int i = 0; i < diggers; i++) {
                Score.addscore(i, 0);
            }
            Draw.drawlives();
            if (!synchvid) {
                Dig.curtime = Hw.gethrt();
            }
        } else {
            Snd.soundpauseoff();
        }
    }

    static void calibrate() {
        Snd.volume = (int) (Hw.getkips() / 291);
        if (Snd.volume == 0) {
            Snd.volume = 1;
        }
    }

    public static int randno(int n) {
        randv = (randv * 0x15a4e35L + 1) & 0xffffffffL;
        return (int) ((randv & 0x7fffffffL) % n);
    }

    public static void parsecmd(String[] argv) {
        for (int arg = 0; arg < argv.length; arg++) {
            String word = argv[arg];
            if (word.isEmpty()) continue;
            if (word.charAt(0) == '/' || word.charAt(0) == '-') {
                char opt = Character.toUpperCase(word.length() > 1 ? word.charAt(1) : ' ');
                int i = (word.length() > 2 && word.charAt(2) == ':') ? 3 : 2;
                String rest = word.length() > i ? word.substring(i) : "";
                switch (opt) {
                    case 'I': try { startlev = Integer.parseInt(rest.trim()); } catch (Exception ignored) {} break;
                    case 'S': try { Dig.ftime = Long.parseLong(rest.trim()) * 2000L; } catch (Exception ignored) {} break;
                    case 'U': unlimlives = true; break;
                    case 'Q': Snd.soundflag = false; break;
                    case 'M': Snd.musicflag = false; break;
                    case '2': diggers = 2; break;
                    case 'V': synchvid = true; break;
                    case 'L': levfname = rest; levfflag = true; break;
                    case 'R': Rec.recname(rest); break;
                    case 'P': case 'E': Rec.openplay(rest); break;
                    case 'G':
                        gtime = 0;
                        for (int k = 0; k < rest.length() && Character.isDigit(rest.charAt(k)); k++) {
                            gtime = 10 * gtime + (rest.charAt(k) - '0');
                        }
                        if (gtime > 3599) gtime = 3599;
                        if (gtime == 0) gtime = 120;
                        gauntlet = true;
                        break;
                    default: break;
                }
            } else {
                // bare number = speed, else level filename
                boolean num = true;
                for (int j = 0; j < word.length(); j++) {
                    if (!Character.isDigit(word.charAt(j))) { num = false; break; }
                }
                if (num) {
                    try { Dig.ftime = Long.parseLong(word) * 2000L; } catch (Exception ignored) {}
                } else {
                    levfname = word;
                    levfflag = true;
                }
            }
        }
    }

    static void inir() {
        gtime = (int) IniFile.getInt(INI_GAME_SETTINGS, "GauntletTime", 120, ININAME);
        Dig.ftime = IniFile.getInt(INI_GAME_SETTINGS, "Speed", 80000L, ININAME);
        gauntlet = IniFile.getBool(INI_GAME_SETTINGS, "GauntletMode", false, ININAME);
        String players = IniFile.getString(INI_GAME_SETTINGS, "Players", "1", ININAME).toUpperCase();
        if (players.length() >= 2 && players.charAt(0) == '2' && players.charAt(1) == 'S') {
            diggers = 2;
            nplayers = 1;
        } else {
            diggers = 1;
            try { nplayers = Integer.parseInt(players.trim().substring(0, 1)); } catch (Exception e) { nplayers = 1; }
            if (nplayers < 1 || nplayers > 2) nplayers = 1;
        }
        Snd.soundflag = IniFile.getBool(INI_SOUND_SETTINGS, "SoundOn", true, ININAME);
        Snd.musicflag = IniFile.getBool(INI_SOUND_SETTINGS, "MusicOn", true, ININAME);
        synchvid = IniFile.getBool(INI_GRAPHICS_SETTINGS, "Synch", false, ININAME);
        unlimlives = IniFile.getBool(INI_GAME_SETTINGS, "UnlimitedLives", false, ININAME);
        startlev = (int) IniFile.getInt(INI_GAME_SETTINGS, "StartLevel", 1, ININAME);
        if (Dig.ftime <= 0) Dig.ftime = 80000L;
    }
}
