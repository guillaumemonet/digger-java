package org.digger;

import java.awt.event.KeyEvent;
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

    /** Fed from the EDT KeyListener. */
    public static synchronized void keyEvent(KeyEvent e, boolean pressed) {
        int vk = e.getKeyCode();
        if (vk >= 0 && vk < held.length) {
            held[vk] = pressed;
        }
        if (pressed) {
            typed.add(mapTyped(e));
            if (typed.size() > 64) {
                typed.poll();
            }
        }
    }

    /** Map a key press to the "akeypressed" code the game logic expects. */
    private static int mapTyped(KeyEvent e) {
        int vk = e.getKeyCode();
        switch (vk) {
            case KeyEvent.VK_ESCAPE: return 27;
            case KeyEvent.VK_ENTER: return 13;
            case KeyEvent.VK_SPACE: return 32;
            case KeyEvent.VK_BACK_SPACE: return 8;
            case KeyEvent.VK_F7: return Inp.KC_MUSIC;
            case KeyEvent.VK_F9: return Inp.KC_SOUND;
            case KeyEvent.VK_F10: return Inp.KC_EXIT;
            case KeyEvent.VK_F8: return Inp.KC_SAVEDRF;
            case KeyEvent.VK_ADD:
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_EQUALS: return '+';
            case KeyEvent.VK_SUBTRACT:
            case KeyEvent.VK_MINUS: return '-';
            default:
                if (vk >= 'A' && vk <= 'Z') return vk;           // upper-case
                if (vk >= '0' && vk <= '9') return vk;
                char c = e.getKeyChar();
                if (c != KeyEvent.CHAR_UNDEFINED && c >= ' ' && c < 127) {
                    return Character.toUpperCase(c);
                }
                return 256 + vk;   // unique non-clashing sentinel
        }
    }
}
