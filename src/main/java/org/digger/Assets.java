package org.digger;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the original Digger binary sprite/font assets (vga.spr, vgatext.spr)
 * and exposes the {@code vgatable} sprite-index mapping and the 16-colour VGA
 * palettes.
 *
 * <p>This is the runtime equivalent of MKG.C: instead of generating C source,
 * we decode the raw 32x32, 1-byte-per-pixel sprite cells directly. A source
 * byte of 0xFF means "transparent"; otherwise the low nibble is the colour
 * index (0-15).
 */
public final class Assets {
    private Assets() {}

    public static final int NSPR = 107;        // sprite cells in vga.spr
    public static final int CELL = 1024;        // 32x32 bytes per cell

    /** Raw sprite cells: vgaspr[g] = 1024 bytes (row-major 32x32). */
    public static byte[][] vgaspr;
    /** Raw font glyph cells in clist order. */
    public static byte[][] vgatext;

    // clist from MKG.C: order glyphs appear in vgatext.spr.
    private static final String CLIST =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.:_ ";
    /** ASCII code -> glyph index in vgatext (or -1). */
    public static final int[] ascii2glyph = new int[128];

    /** vgatable: for sprite char index ch (0..119), data/mask graphic indices. */
    public static final int[] tableData = new int[120];
    public static final int[] tableMask = new int[120];

    public static final int ZERO = -1;          // vgazero480 (all black / opaque)

    /** Four palettes [pal*2+inten][colour] = packed 0xRRGGBB. */
    public static final int[][] palette = new int[4][16];

    // win_vid.c palette tables, stored as {blue, green, red} triples.
    private static final int[][] PAL1 = {
            {0,0,0},{128,0,0},{0,128,0},{128,128,0},{0,0,128},{128,0,128},
            {0,64,128},{128,128,128},{64,64,64},{255,0,0},{0,255,0},{255,255,0},
            {0,0,255},{255,0,255},{0,255,255},{255,255,255}};
    private static final int[][] PAL1I = {
            {0,0,0},{255,0,0},{0,255,0},{255,255,0},{0,0,255},{255,0,255},
            {0,128,255},{192,192,192},{128,128,128},{255,128,128},{128,255,128},
            {255,255,128},{128,128,255},{255,128,255},{128,255,255},{255,255,255}};
    private static final int[][] PAL2 = {
            {0,0,0},{0,128,0},{0,0,128},{0,64,128},{128,0,0},{128,128,0},
            {128,0,128},{128,128,128},{64,64,64},{0,255,0},{0,0,255},{0,255,255},
            {255,0,0},{255,255,0},{255,0,255},{255,255,255}};
    private static final int[][] PAL2I = {
            {0,0,0},{0,255,0},{0,0,255},{0,128,255},{255,0,0},{255,255,0},
            {255,0,255},{192,192,192},{128,128,128},{128,255,128},{128,128,255},
            {128,255,255},{255,128,128},{255,255,128},{255,128,255},{255,255,255}};

    public static void load() {
        vgaspr = readCells("/assets/vga.spr", NSPR);
        // vgatext.spr holds 40 glyph cells (length of CLIST).
        vgatext = readCells("/assets/vgatext.spr", CLIST.length());
        buildFontMap();
        buildTable();
        buildPalettes();
    }

    private static byte[][] readCells(String resource, int count) {
        byte[][] cells = new byte[count][CELL];
        try (InputStream in = Assets.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new RuntimeException("Missing asset: " + resource);
            }
            DataInputStream dis = new DataInputStream(in);
            for (int i = 0; i < count; i++) {
                dis.readFully(cells[i]);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed reading " + resource, e);
        }
        return cells;
    }

    private static void buildFontMap() {
        for (int i = 0; i < ascii2glyph.length; i++) {
            ascii2glyph[i] = -1;
        }
        for (int i = 0; i < CLIST.length(); i++) {
            char c = CLIST.charAt(i);
            ascii2glyph[c] = i;
            // The original maps case-insensitively to the same (upper-case) glyph.
            if (c >= 'A' && c <= 'Z') {
                ascii2glyph[Character.toLowerCase(c)] = i;
            }
        }
    }

    private static void buildPalettes() {
        int[][][] all = {PAL1, PAL1I, PAL2, PAL2I};
        for (int p = 0; p < 4; p++) {
            for (int c = 0; c < 16; c++) {
                int b = all[p][c][0], g = all[p][c][1], r = all[p][c][2];
                palette[p][c] = (r << 16) | (g << 8) | b;
            }
        }
    }

    // Sample a normal sprite/font cell byte at device pixel (px,py).
    public static int cellByte(byte[] cell, int px, int py) {
        return cell[py * 32 + px] & 0xff;
    }

    /**
     * Transcription of the {@code vgatable[]} from MKG.C. Each ch maps to a
     * data graphic and a mask graphic (gnames index), or ZERO for vgazero480.
     */
    private static void buildTable() {
        int[][] t = {
            {ZERO,86},{25,25},{23,23},{21,21},{26,26},{24,24},{22,22},
            {37,37},{35,35},{33,33},{38,38},{36,36},{34,34},
            {31,31},{29,29},{27,27},{32,32},{30,30},{28,28},
            {43,43},{41,41},{39,39},{44,44},{42,42},{40,40},
            {74,74},{73,73},{72,72},{71,71},{70,70},{69,69},
            {ZERO,86},
            {49,49},{47,47},{45,45},{50,50},{48,48},{46,46},
            {61,61},{59,59},{57,57},{62,62},{60,60},{58,58},
            {55,55},{53,53},{51,51},{56,56},{54,54},{52,52},
            {67,67},{65,65},{63,63},{68,68},{66,66},{64,64},
            {75,75},{73,73},{72,72},{71,71},{70,70},{69,69},
            {1,1},{2,2},{3,3},{4,4},{5,5},{6,6},{7,7},
            {10,10},{9,9},{8,8},{11,11},
            {12,12},{13,13},{14,14},{15,15},
            {16,16},{17,17},{18,18},{19,19},
            {20,86},
            {95,95},{96,96},{97,97},{98,98},{99,99},{100,100},
            {95,95},{96,96},{97,97},{98,98},{99,99},{100,100},
            {87,ZERO},{88,ZERO},{89,ZERO},{90,ZERO},{91,ZERO},{92,ZERO},{93,ZERO},{94,ZERO},
            {ZERO,80},{ZERO,82},{ZERO,81},{ZERO,83},{ZERO,85},{ZERO,84},
            {0,0},{ZERO,0},
            {76,76},{77,77},{ZERO,78},{79,79},
            {101,101},{102,102},{103,103},{104,104},{105,105},{106,106},
        };
        for (int ch = 0; ch < 120; ch++) {
            tableData[ch] = t[ch][0];
            tableMask[ch] = t[ch][1];
        }
    }

    /** Mask mode for a mask graphic index: blob sprites (80-85) use the &15 rule. */
    public static boolean isBlobMask(int maskG) {
        return maskG >= 80 && maskG <= 85;
    }
}
