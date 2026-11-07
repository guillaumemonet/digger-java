package org.digger;


import static org.digger.Def.*;

/**
 * Keyboard input, ported from input.c. Held-key detection comes from the Swing
 * EDT (Hw.isHeld); typed keys come from Hw.getkey/kbhit.
 *
 * <p>Default keys: Player 1 = arrow keys + F1 (fire). Player 2 = WASD + Ctrl
 * (fire). T cheat, +/- speed, F7 music, F9 sound, F10 quit, Space pause,
 * Enter/any key to start.
 */
public final class Inp {
    private Inp() {}

    // Sentinel akeypressed codes for non-character control keys.
    public static final int KC_MUSIC = 1007;
    public static final int KC_SOUND = 1009;
    public static final int KC_EXIT = 1010;
    public static final int KC_SAVEDRF = 1008;

    public static boolean escape = false, firepflag = false, fire2pflag = false;
    public static int akeypressed;
    public static boolean pausef = false;

    static boolean arightpressed, auppressed, aleftpressed, adownpressed, af1pressed;
    static boolean aright2pressed, aup2pressed, aleft2pressed, adown2pressed, af12pressed;
    static boolean start = false;

    static int dynamicdir = -1, dynamicdir2 = -1, staticdir = -1, staticdir2 = -1;
    static int keydir = 0, keydir2 = 0;
    static boolean joyflag = false;

    public static final boolean[] krdf = new boolean[17];

    // keycodes[i][0] = held key (VK) for movement/fire rows 0..9, or the
    // akeypressed match code for control rows 10..16. Other columns unused (-2).
    public static final int[][] keycodes = {
            {Keys.VK_RIGHT, -2, -2, -2, -2},  // 0 P1 Right
            {Keys.VK_UP, -2, -2, -2, -2},     // 1 P1 Up
            {Keys.VK_LEFT, -2, -2, -2, -2},   // 2 P1 Left
            {Keys.VK_DOWN, -2, -2, -2, -2},   // 3 P1 Down
            {Keys.VK_F1, -2, -2, -2, -2},     // 4 P1 Fire
            {Keys.VK_D, -2, -2, -2, -2},      // 5 P2 Right
            {Keys.VK_W, -2, -2, -2, -2},      // 6 P2 Up
            {Keys.VK_A, -2, -2, -2, -2},      // 7 P2 Left
            {Keys.VK_S, -2, -2, -2, -2},      // 8 P2 Down
            {Keys.VK_CONTROL, -2, -2, -2, -2},// 9 P2 Fire
            {'T', -2, -2, -2, -2},                // 10 Cheat
            {'+', -2, -2, -2, -2},                // 11 Accelerate
            {'-', -2, -2, -2, -2},                // 12 Brake
            {KC_MUSIC, -2, -2, -2, -2},           // 13 Music
            {KC_SOUND, -2, -2, -2, -2},           // 14 Sound
            {KC_EXIT, -2, -2, -2, -2},            // 15 Exit
            {32, -2, -2, -2, -2},                 // 16 Pause
    };

    private static boolean rightpressed() { return Hw.isHeld(keycodes[0][0]); }
    private static boolean uppressed() { return Hw.isHeld(keycodes[1][0]); }
    private static boolean leftpressed() { return Hw.isHeld(keycodes[2][0]); }
    private static boolean downpressed() { return Hw.isHeld(keycodes[3][0]); }
    private static boolean f1pressed() { return Hw.isHeld(keycodes[4][0]); }
    private static boolean right2pressed() { return Hw.isHeld(keycodes[5][0]); }
    private static boolean up2pressed() { return Hw.isHeld(keycodes[6][0]); }
    private static boolean left2pressed() { return Hw.isHeld(keycodes[7][0]); }
    private static boolean down2pressed() { return Hw.isHeld(keycodes[8][0]); }
    private static boolean f12pressed() { return Hw.isHeld(keycodes[9][0]); }

    public static void checkkeyb() {
        if (leftpressed()) aleftpressed = true;
        if (rightpressed()) arightpressed = true;
        if (uppressed()) auppressed = true;
        if (downpressed()) adownpressed = true;
        if (f1pressed()) af1pressed = true;
        if (left2pressed()) aleft2pressed = true;
        if (right2pressed()) aright2pressed = true;
        if (up2pressed()) aup2pressed = true;
        if (down2pressed()) adown2pressed = true;
        if (f12pressed()) af12pressed = true;

        while (Hw.kbhit()) {
            akeypressed = Hw.getkey();
            int k = 0;
            for (int i = 10; i < 17; i++) {
                for (int j = 0; j < 5; j++) {
                    if (akeypressed == keycodes[i][j]) {
                        k = i;
                    }
                }
            }
            switch (k) {
                case 10: // Cheat
                    if (Rec.playing) {
                        Rec.playing = false;
                        Rec.drfvalid = false;
                    }
                    break;
                case 11: // speed up
                    if (Dig.ftime > 10000L) Dig.ftime -= 10000L;
                    break;
                case 12: // speed down
                    Dig.ftime += 10000L;
                    break;
                case 13: // music
                    Snd.musicflag = !Snd.musicflag;
                    break;
                case 14: // sound
                    Snd.soundflag = !Snd.soundflag;
                    break;
                case 15: // exit
                    escape = true;
                    break;
                case 16: // pause
                    pausef = true;
                    break;
            }
            if (akeypressed == KC_SAVEDRF) {
                Rec.savedrf = true;
            }
            if (akeypressed != 27 && akeypressed != 'n' && akeypressed != 'N') {
                start = true;
            }
        }
    }

    public static void detectjoy() {
        joyflag = false;
        staticdir = dynamicdir = DIR_NONE;
    }

    public static void flushkeybuf() {
        while (Hw.kbhit()) {
            Hw.getkey();
        }
        aleftpressed = arightpressed = auppressed = adownpressed = af1pressed = false;
        aleft2pressed = aright2pressed = aup2pressed = adown2pressed = af12pressed = false;
    }

    public static void clearfire(int n) {
        if (n == 0) af1pressed = false;
        else af12pressed = false;
    }

    static boolean oupressed, odpressed, olpressed, orpressed;
    static boolean ou2pressed, od2pressed, ol2pressed, or2pressed;

    public static void readdir(int n) {
        boolean u = false, d = false, l = false, r = false;
        boolean u2 = false, d2 = false, l2 = false, r2 = false;
        if (n == 0) {
            firepflag = false;
            if (auppressed || uppressed()) { u = true; auppressed = false; }
            if (adownpressed || downpressed()) { d = true; adownpressed = false; }
            if (aleftpressed || leftpressed()) { l = true; aleftpressed = false; }
            if (arightpressed || rightpressed()) { r = true; arightpressed = false; }
            if (f1pressed() || af1pressed) {
                firepflag = true;
                af1pressed = false;
            }
            if (u && !oupressed) staticdir = dynamicdir = DIR_UP;
            if (d && !odpressed) staticdir = dynamicdir = DIR_DOWN;
            if (l && !olpressed) staticdir = dynamicdir = DIR_LEFT;
            if (r && !orpressed) staticdir = dynamicdir = DIR_RIGHT;
            if ((oupressed && !u && dynamicdir == DIR_UP)
                    || (odpressed && !d && dynamicdir == DIR_DOWN)
                    || (olpressed && !l && dynamicdir == DIR_LEFT)
                    || (orpressed && !r && dynamicdir == DIR_RIGHT)) {
                dynamicdir = DIR_NONE;
                if (u) dynamicdir = staticdir = 2;
                if (d) dynamicdir = staticdir = 6;
                if (l) dynamicdir = staticdir = 4;
                if (r) dynamicdir = staticdir = 0;
            }
            oupressed = u; odpressed = d; olpressed = l; orpressed = r;
            keydir = staticdir;
            if (dynamicdir != DIR_NONE) keydir = dynamicdir;
            staticdir = DIR_NONE;
        } else {
            if (aup2pressed || up2pressed()) { u2 = true; aup2pressed = false; }
            if (adown2pressed || down2pressed()) { d2 = true; adown2pressed = false; }
            if (aleft2pressed || left2pressed()) { l2 = true; aleft2pressed = false; }
            if (aright2pressed || right2pressed()) { r2 = true; aright2pressed = false; }
            if (f12pressed() || af12pressed) {
                fire2pflag = true;
                af12pressed = false;
            } else {
                fire2pflag = false;
            }
            if (u2 && !ou2pressed) staticdir2 = dynamicdir2 = DIR_UP;
            if (d2 && !od2pressed) staticdir2 = dynamicdir2 = DIR_DOWN;
            if (l2 && !ol2pressed) staticdir2 = dynamicdir2 = DIR_LEFT;
            if (r2 && !or2pressed) staticdir2 = dynamicdir2 = DIR_RIGHT;
            if ((ou2pressed && !u2 && dynamicdir2 == DIR_UP)
                    || (od2pressed && !d2 && dynamicdir2 == DIR_DOWN)
                    || (ol2pressed && !l2 && dynamicdir2 == DIR_LEFT)
                    || (or2pressed && !r2 && dynamicdir2 == DIR_RIGHT)) {
                dynamicdir2 = DIR_NONE;
                if (u2) dynamicdir2 = staticdir2 = 2;
                if (d2) dynamicdir2 = staticdir2 = 6;
                if (l2) dynamicdir2 = staticdir2 = 4;
                if (r2) dynamicdir2 = staticdir2 = 0;
            }
            ou2pressed = u2; od2pressed = d2; ol2pressed = l2; or2pressed = r2;
            keydir2 = staticdir2;
            if (dynamicdir2 != DIR_NONE) keydir2 = dynamicdir2;
            staticdir2 = DIR_NONE;
        }
    }

    public static boolean teststart() {
        boolean startf = false;
        if (start) {
            start = false;
            startf = true;
        }
        return startf;
    }

    public static int getdir(int n) {
        int dir = (n == 0) ? keydir : keydir2;
        if (n == 0) {
            if (Rec.playing) {
                int[] dd = {dir};
                boolean[] ff = {firepflag};
                Rec.playgetdir(dd, ff);
                dir = dd[0];
                firepflag = ff[0];
            }
            Rec.recputdir(dir, firepflag);
        } else {
            if (Rec.playing) {
                int[] dd = {dir};
                boolean[] ff = {fire2pflag};
                Rec.playgetdir(dd, ff);
                dir = dd[0];
                fire2pflag = ff[0];
            }
            Rec.recputdir(dir, fire2pflag);
        }
        return dir;
    }

    // Used only by /K keyboard redefinition (not wired to a UI here).
    public static void findkey(int kn) {
        krdf[kn] = true;
    }
}
