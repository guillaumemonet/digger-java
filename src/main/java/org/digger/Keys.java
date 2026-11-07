package org.digger;

/**
 * Platform-neutral key codes. The values are identical to java.awt.event.KeyEvent
 * VK_* constants so the desktop build can pass AWT codes straight through; other
 * platforms (Android) translate their native key codes to these.
 */
public final class Keys {
    private Keys() {}

    public static final int VK_BACK_SPACE = 8;
    public static final int VK_ENTER = 10;
    public static final int VK_CONTROL = 17;
    public static final int VK_ESCAPE = 27;
    public static final int VK_SPACE = 32;
    public static final int VK_LEFT = 37;
    public static final int VK_UP = 38;
    public static final int VK_RIGHT = 39;
    public static final int VK_DOWN = 40;
    public static final int VK_MINUS = 45;
    public static final int VK_EQUALS = 61;
    public static final int VK_A = 65;
    public static final int VK_D = 68;
    public static final int VK_N = 78;
    public static final int VK_S = 83;
    public static final int VK_T = 84;
    public static final int VK_W = 87;
    public static final int VK_ADD = 107;
    public static final int VK_SUBTRACT = 109;
    public static final int VK_F1 = 112;
    public static final int VK_F7 = 118;
    public static final int VK_F8 = 119;
    public static final int VK_F9 = 120;
    public static final int VK_F10 = 121;
    public static final int VK_PLUS = 521;

    /** Same as KeyEvent.CHAR_UNDEFINED. */
    public static final char CHAR_UNDEFINED = 0xFFFF;
}
