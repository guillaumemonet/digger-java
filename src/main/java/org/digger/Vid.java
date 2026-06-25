package org.digger;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Video layer: the platform implementation of the VGA functions declared in
 * hardware.h / sprite.h (ginit, gclear, gpal, ginten, gputi, ggeti, gputim,
 * ggetpix, gwrite, gtitle).
 *
 * <p>Mirrors win_vid.c: a 640x400 8-bit indexed back buffer where the logical
 * 320x200 game coordinates are doubled. Only the VGA path is implemented (the
 * original could also use CGA).
 */
public final class Vid {
    private Vid() {}

    public static final int W = 640;
    public static final int H = 400;

    /** Indexed back buffer (colour 0-15 per pixel). */
    public static final byte[] fb = new byte[W * H];

    private static int curPalette = 0;   // 0 or 1
    private static int curIntensity = 0; // 0 or 1

    private static final BufferedImage screen =
            new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    private static final int[] screenPix =
            ((DataBufferInt) screen.getRaster().getDataBuffer()).getData();

    public static void ginit() { /* palette set up via Assets.load() already */ }

    public static void gclear() {
        java.util.Arrays.fill(fb, (byte) 0);
    }

    public static void gpal(int pal) {
        curPalette = pal;
    }

    public static void ginten(int inten) {
        curIntensity = inten;
    }

    private static int[] pal() {
        return Assets.palette[curPalette * 2 + curIntensity];
    }

    /** vgagetpix: returns nonzero if there is non-black pixel data ahead. */
    public static int ggetpix(int x, int y) {
        if (x > 319 || y > 199) {
            return 0xff;
        }
        int rval = 0;
        for (int yi = 0; yi < 2; yi++) {
            for (int xi = 0; xi < 8; xi++) {
                int dx = x * 2 + xi, dy = y * 2 + yi;
                if (dx < W && dy < H && fb[dy * W + dx] != 0) {
                    rval |= 0x80 >> xi;
                }
            }
        }
        rval &= 0xee;
        return rval;
    }

    private static void put(int dx, int dy, int color) {
        if (dx >= 0 && dx < W && dy >= 0 && dy < H) {
            fb[dy * W + dx] = (byte) color;
        }
    }

    /** vgaputim: draw sprite character ch at (x,y) with cell size (w,h). */
    public static void gputim(int x, int y, int ch, int w, int h) {
        int yEnd = h * 2, xEnd = w * 8;
        if (ch >= 94 && ch <= 101) {                  // background tiles (opaque)
            byte[] cell = Assets.vgaspr[Assets.tableData[ch]];
            for (int py = 0; py < yEnd; py++) {
                for (int px = 0; px < xEnd; px++) {
                    int b = (px < 32) ? cell[py * 32 + px]
                                      : cell[512 + py * 32 + (px - 32)];
                    put(x * 2 + px, y * 2 + py, b & 0x0f);
                }
            }
            return;
        }
        if (ch >= 102 && ch <= 107) {                 // tunnel-edge blobs
            int maskG = Assets.tableMask[ch];
            byte[] cell = Assets.vgaspr[maskG];
            boolean tall = (maskG == 80 || maskG == 81);
            for (int py = 0; py < yEnd; py++) {
                for (int px = 0; px < xEnd; px++) {
                    int src;
                    if (tall) {                        // 16 wide x 36 tall packing
                        src = (py < 32) ? cell[py * 32 + px]
                                        : cell[(py - 32) * 32 + 16 + px];
                    } else {                           // wide packing (overflow at rows+16)
                        src = (px < 32) ? cell[py * 32 + px]
                                        : cell[(py + 16) * 32 + (px - 32)];
                    }
                    // Carve black where the source nibble is 0; leave dirt elsewhere.
                    if ((src & 0x0f) == 0) {
                        put(x * 2 + px, y * 2 + py, 0);
                    }
                }
            }
            return;
        }
        int dataG = Assets.tableData[ch];
        int maskG = Assets.tableMask[ch];
        byte[] data = dataG == Assets.ZERO ? null : Assets.vgaspr[dataG];
        byte[] mask = maskG == Assets.ZERO ? null : Assets.vgaspr[maskG];
        for (int py = 0; py < yEnd; py++) {
            for (int px = 0; px < xEnd; px++) {
                boolean transparent = false;
                if (mask != null) {
                    int mb = Assets.cellByte(mask, px, py);
                    transparent = (mb & 0x80) != 0;     // 0xff source = transparent
                }
                if (!transparent) {
                    int color = (data == null) ? 0
                            : (Assets.cellByte(data, px, py) & 0x0f);
                    put(x * 2 + px, y * 2 + py, color);
                }
            }
        }
    }

    /** vgaputi: restore saved background buffer p over (x,y). */
    public static void gputi(int x, int y, byte[] p, int w, int h) {
        int rowBytes = w * 8;
        for (int i = 0; i < h * 2; i++) {
            int dy = y * 2 + i;
            if (dy >= H) break;
            for (int col = 0; col < rowBytes; col++) {
                int dx = x * 2 + col;
                if (dx >= 0 && dx < W && dy >= 0) {
                    fb[dy * W + dx] = p[i * rowBytes + col];
                }
            }
        }
    }

    /** vgageti: save background buffer under (x,y) into p. */
    public static void ggeti(int x, int y, byte[] p, int w, int h) {
        int rowBytes = w * 8;
        for (int i = 0; i < h * 2; i++) {
            int dy = y * 2 + i;
            for (int col = 0; col < rowBytes; col++) {
                int dx = x * 2 + col;
                int idx = i * rowBytes + col;
                if (idx < p.length) {
                    p[idx] = (dx >= 0 && dx < W && dy >= 0 && dy < H)
                            ? fb[dy * W + dx] : 0;
                }
            }
        }
    }

    /** vgawrite: draw a 12x12 (logical) font character at (x,y), colour c. */
    public static void gwrite(int x, int y, int ch, int c) {
        int glyph = (ch >= 0 && ch < 128) ? Assets.ascii2glyph[ch] : -1;
        for (int yi = 0; yi < 24; yi++) {
            for (int xi = 0; xi < 24; xi++) {
                int color;
                if (glyph < 0) {
                    color = 0;
                } else {
                    color = Assets.cellByte(Assets.vgatext[glyph], xi, yi) & 0x0f;
                    color = remapTextColor(color, c);
                }
                put(x * 2 + xi, y * 2 + yi, color);
            }
        }
    }

    private static int remapTextColor(int color, int c) {
        if (color == 10) {
            if (c == 2) return 12;
            if (c == 3) return 14;
            return 10;
        }
        if (color == 12) {
            if (c == 1) return 2;
            if (c == 2) return 4;
            if (c == 3) return 6;
            return 12;
        }
        return color;
    }

    /** Debug: write the current framebuffer to a PNG (for headless verification). */
    public static synchronized void writePng(String path) {
        int[] p = pal();
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int[] op = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < fb.length; i++) {
            op[i] = p[fb[i] & 0x0f];
        }
        try {
            javax.imageio.ImageIO.write(out, "png", new java.io.File(path));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void gtitle() {
        // The original blits a 640x400 title bitmap; we clear to black and let
        // the menu (DIGGER text + animated characters) draw on top.
        gclear();
    }

    public static void gretrace() { /* no-op */ }
    public static void graphicsoff() { /* no-op */ }

    /** Render the indexed back buffer to an ARGB image and draw it scaled. */
    public static synchronized void blit(Graphics g, int destW, int destH) {
        int[] p = pal();
        byte[] f = fb;
        for (int i = 0; i < f.length; i++) {
            screenPix[i] = p[f[i] & 0x0f];
        }
        g.drawImage(screen, 0, 0, destW, destH, null);
    }
}
