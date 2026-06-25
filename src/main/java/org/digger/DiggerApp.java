package org.digger;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Swing launcher and window for the Java port of Digger Remastered.
 *
 * <p>Controls: arrow keys move Player 1, F1 fires. WASD + Ctrl for Player 2.
 * Press any key (e.g. Enter) at the title to start; Esc/N toggles the number of
 * players. Space pauses, T cheats (disable playback), +/- adjust speed,
 * F7 toggles music, F9 toggles sound, F8 saves the recording, F10 quits.
 */
public final class DiggerApp {

    static final class ScreenPanel extends JPanel {
        ScreenPanel() {
            setPreferredSize(new Dimension(Vid.W, Vid.H));
            setBackground(Color.BLACK);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Vid.blit(g, getWidth(), getHeight());
        }
    }

    public static void main(String[] args) {
        Assets.load();

        final ScreenPanel[] panelRef = new ScreenPanel[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFrame frame = new JFrame("Digger (Java)");
                frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                ScreenPanel panel = new ScreenPanel();
                panelRef[0] = panel;
                frame.add(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setResizable(true);

                KeyAdapter k = new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) { Hw.keyEvent(e, true); }
                    @Override public void keyReleased(KeyEvent e) { Hw.keyEvent(e, false); }
                };
                panel.addKeyListener(k);
                frame.addKeyListener(k);

                frame.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosing(WindowEvent e) {
                        Inp.escape = true;
                    }
                });
                frame.setVisible(true);
                panel.requestFocusInWindow();

                Timer repaint = new Timer(16, ev -> panel.repaint());
                repaint.start();
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Game logic runs on its own thread; Swing EDT handles paint + input.
        Thread game = new Thread(() -> {
            Main.maininit();
            Main.parsecmd(args);
            Main.mainprog();
            System.exit(0);
        }, "digger-game");
        game.start();

        // Headless verification hook: auto-start a game from the menu.
        if (System.getProperty("digger.autostart") != null) {
            new Thread(() -> {
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                Inp.start = true;
            }, "digger-autostart").start();
        }

        // Headless verification hook: -Ddigger.shot=file.png -Ddigger.shotdelay=ms
        String shot = System.getProperty("digger.shot");
        if (shot != null) {
            long delay = Long.getLong("digger.shotdelay", 2500L);
            new Thread(() -> {
                try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                Vid.writePng(shot);
                System.exit(0);
            }, "digger-shot").start();
        }
    }
}
