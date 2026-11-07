package org.digger.android;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.window.OnBackInvokedDispatcher;

import org.digger.Assets;
import org.digger.Hw;
import org.digger.Inp;
import org.digger.Main;
import org.digger.Snd;

/**
 * Android entry point. Runs the ported game ({@link Main#mainprog()}) on its own
 * thread, exactly like the desktop launcher, and shows it in a {@link GameView}.
 *
 * <p>The game logic is all static state, so the game thread is started once per
 * process; a recreated activity simply re-attaches a view to it. Back = F10
 * (ends the current game, or quits from the title screen).
 */
public final class DiggerActivity extends Activity {

    private static Thread gameThread;
    private static volatile DiggerActivity current;
    private static volatile boolean gameFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        GameView view = new GameView(this);
        setContentView(view);
        hideSystemBars();

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, DiggerActivity::backPressed);
        }
        startGame();
    }

    private void startGame() {
        if (gameThread != null) return;
        Hw.dataDir = getFilesDir();
        Snd.sinkFactory = AndroidAudioSink::new;
        Assets.load();
        gameThread = new Thread(() -> {
            Main.maininit();
            Main.parsecmd(new String[0]);
            Main.mainprog();
            gameFinished = true;
            DiggerActivity a = current;
            if (a != null) {
                a.runOnUiThread(a::finishAndRemoveTask);
            } else {
                System.exit(0);
            }
        }, "digger-game");
        gameThread.start();
    }

    private static void backPressed() {
        Hw.pushTyped(Inp.KC_EXIT);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Pre-Android 13 back handling (33+ uses OnBackInvokedCallback).
        if (keyCode == KeyEvent.KEYCODE_BACK && Build.VERSION.SDK_INT < 33) {
            backPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AndroidAudioSink.muted = false;
        Hw.setSuspended(false);
    }

    @Override
    protected void onPause() {
        // Freeze the game loop and silence the synth while in the background.
        Hw.setSuspended(true);
        AndroidAudioSink.muted = true;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
        if (gameFinished) {
            // Static game state can't be restarted cleanly: end the process so
            // the next launch starts fresh.
            System.exit(0);
        }
    }
}
