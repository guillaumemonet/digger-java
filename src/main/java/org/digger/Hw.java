package org.digger;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Platform layer for timing and keyboard, replacing the DOS/Windows low-level
 * routines from hardware.h (dospc.asm / win_sys.c).
 *
 * <p>Timing uses System.nanoTime() scaled to the original 1193181 Hz PIT rate
 * so the speed/ftime constants behave as in the original. Keyboard state is fed
 * from the Swing EDT.
 */
public final class Hw {
    private Hw() {}

    private static final double TICKS_PER_NANO = 1193181.0 / 1_000_000_000.0;
    private static long baseNano = System.nanoTime();

    public static void inittimer() {
        baseNano = System.nanoTime();
    }

    /** High resolution timer in 1193181 Hz ticks. */
    public static long gethrt() {
        return (long) ((System.nanoTime() - baseNano) * TICKS_PER_NANO);
    }

    /** Low resolution (BIOS-tick-like) value; only used to seed RNG. */
    public static long getlrt() {
        return gethrt() & 0x7fffffffL;
    }

    /** Calibration: original computes volume = kips/291. Return a sane value. */
    public static long getkips() {
        return 291L * 8L;     // -> volume 8
    }

    public static void olddelay(int t) {
        try { Thread.sleep(t); } catch (InterruptedException ignored) {}
    }

    // ---- Keyboard ----------------------------------------------------------

    private static final boolean[] held = new boolean[1024];
    private static final Deque<Integer> typed = new ArrayDeque<>();

    public static void initkeyb() { /* handled by Swing listener */ }
    public static void restorekeyb() { /* nothing to restore */ }

    public static synchronized boolean isHeld(int vk) {
        return vk >= 0 && vk < held.length && held[vk];
    }

    public static synchronized boolean kbhit() {
        return !typed.isEmpty();
    }

    public static synchronized int getkey() {
        Integer k = typed.poll();
        return k == null ? 0 : k;
    }

    /**
     * Fed from the platform input layer (Swing KeyListener, Android View).
     *
     * @param vk      key code using {@link Keys} (AWT-compatible) values
     * @param keyChar typed character, or {@link Keys#CHAR_UNDEFINED}
     */
    public static synchronized void keyEvent(int vk, char keyChar, boolean pressed) {
        if (vk >= 0 && vk < held.length) {
            held[vk] = pressed;
        }
        if (pressed) {
            pushTyped(mapTyped(vk, keyChar));
        }
    }

    /** Queue an already-mapped "akeypressed" code (e.g. a character from a soft keyboard). */
    public static synchronized void pushTyped(int code) {
        typed.add(code);
        if (typed.size() > 64) {
            typed.poll();
        }
    }

    // ---- Platform hooks ----------------------------------------------------

    /**
     * Directory where DIGGER.INI, DIGGER.SCO and recordings live. {@code null}
     * means the current working directory (desktop default). Android sets this
     * to the app's private files directory.
     */
    public static volatile java.io.File dataDir = null;

    /** Resolve a data file name against {@link #dataDir}. Absolute paths pass through. */
    public static java.io.File file(String name) {
        java.io.File f = new java.io.File(name);
        if (dataDir == null || f.isAbsolute()) {
            return f;
        }
        return new java.io.File(dataDir, name);
    }

    /**
     * True while the game is waiting for typed text (high-score initials), so
     * a touch platform can raise its on-screen keyboard.
     */
    public static volatile boolean textInput = false;

    private static final Object suspendLock = new Object();
    private static boolean suspended = false;

    /**
     * Freeze / resume the game loop (Android calls this when the app goes to the
     * background). Only affects wall-clock pacing, never game logic.
     */
    public static void setSuspended(boolean s) {
        synchronized (suspendLock) {
            suspended = s;
            suspendLock.notifyAll();
        }
    }

    /** Called once per frame from the game thread; blocks while suspended. */
    static void waitWhileSuspended() {
        synchronized (suspendLock) {
            while (suspended) {
                try {
                    suspendLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Map a key press to the "akeypressed" code the game logic expects. */
    private static int mapTyped(int vk, char c) {
        switch (vk) {
            case Keys.VK_ESCAPE: return 27;
            case Keys.VK_ENTER: return 13;
            case Keys.VK_SPACE: return 32;
            case Keys.VK_BACK_SPACE: return 8;
            case Keys.VK_F7: return Inp.KC_MUSIC;
            case Keys.VK_F9: return Inp.KC_SOUND;
            case Keys.VK_F10: return Inp.KC_EXIT;
            case Keys.VK_F8: return Inp.KC_SAVEDRF;
            case Keys.VK_ADD:
            case Keys.VK_PLUS:
            case Keys.VK_EQUALS: return '+';
            case Keys.VK_SUBTRACT:
            case Keys.VK_MINUS: return '-';
            default:
                if (vk >= 'A' && vk <= 'Z') return vk;           // upper-case
                if (vk >= '0' && vk <= '9') return vk;
                if (c != Keys.CHAR_UNDEFINED && c >= ' ' && c < 127) {
                    return Character.toUpperCase(c);
                }
                return 256 + vk;   // unique non-clashing sentinel
        }
    }
}
