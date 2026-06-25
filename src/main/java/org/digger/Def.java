package org.digger;

/**
 * Constants ported from def.h.
 *
 * <p>Faithful translation of Digger Remastered (Copyright (c) Andrew Jenner
 * 1998-2004, GPL v2). Original copyright on the game belongs to Windmill
 * Software.
 */
public final class Def {
    private Def() {}

    // bool semantics in the original: TRUE == -1. In Java we use boolean, but
    // direction constants below match the C #defines exactly.
    public static final int DIR_NONE = -1;
    public static final int DIR_RIGHT = 0;
    public static final int DIR_UP = 2;
    public static final int DIR_LEFT = 4;
    public static final int DIR_DOWN = 6;

    public static final int TYPES = 5;

    public static final int BONUSES = 1;
    public static final int BAGS = 50;
    public static final int MONSTERS = 6;
    public static final int DIGGERS = 2;

    public static final int FIREBALLS = DIGGERS;
    public static final int SPRITES = BONUSES + BAGS + MONSTERS + FIREBALLS + DIGGERS;

    public static final int FIRSTBONUS = 0;
    public static final int LASTBONUS = FIRSTBONUS + BONUSES;
    public static final int FIRSTBAG = LASTBONUS;
    public static final int LASTBAG = FIRSTBAG + BAGS;
    public static final int FIRSTMONSTER = LASTBAG;
    public static final int LASTMONSTER = FIRSTMONSTER + MONSTERS;
    public static final int FIRSTFIREBALL = LASTMONSTER;
    public static final int LASTFIREBALL = FIRSTFIREBALL + FIREBALLS;
    public static final int FIRSTDIGGER = LASTFIREBALL;
    public static final int LASTDIGGER = FIRSTDIGGER + DIGGERS;

    public static final int MWIDTH = 15;
    public static final int MHEIGHT = 10;
    public static final int MSIZE = MWIDTH * MHEIGHT;

    public static final long MAX_REC_BUFFER = 262144L;

    public static final String INI_GAME_SETTINGS = "Game";
    public static final String INI_GRAPHICS_SETTINGS = "Graphics";
    public static final String INI_SOUND_SETTINGS = "Sound";
    public static final String INI_KEY_SETTINGS = "Win Keys";

    public static final int DEFAULT_BUFFER = 2048;
    public static final int DEF_SND_DEV = 1;

    public static final int FILENAME_BUFFER_SIZE = 512;

    public static final String ININAME = "DIGGER.INI";
    public static final String DIGGER_VERSION = "JV JAVA 20260623";
}
