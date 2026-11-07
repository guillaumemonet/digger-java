package org.digger.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import org.digger.Hw;
import org.digger.Inp;
import org.digger.Keys;
import org.digger.Vid;

/**
 * Draws the game's 640x400 indexed framebuffer scaled to the screen and turns
 * touch / keyboard / gamepad input into the key codes the ported game logic
 * expects (see {@link Hw#keyEvent}).
 *
 * <p>Touch layout: a 4-way D-pad bottom-left, FIRE bottom-right, a column of
 * small buttons (pause, players, music, sound) top-right; tapping the playfield
 * acts as Enter ("press any key" / start game). The touch overlay hides itself
 * once a physical keyboard or gamepad is used, and comes back on the next touch.
 */
final class GameView extends View {

    // ---- rendering ----------------------------------------------------------
    private final Bitmap frame = Bitmap.createBitmap(Vid.W, Vid.H, Bitmap.Config.ARGB_8888);
    private final int[] pixels = new int[Vid.W * Vid.H];
    private final Paint blit = new Paint();          // nearest-neighbour: crisp pixels
    private final Rect gameRect = new Rect();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            pollTextInput();
            invalidate();
            postOnAnimation(this);
        }
    };

    // ---- touch controls -----------------------------------------------------
    private static final int T_NONE = 0, T_DPAD = 1, T_FIRE = 2, T_BUTTON = 3, T_SCREEN = 4;

    private static final String[] BTN_LABELS = {"II", "1P/2P", "MUS", "SND"};
    private static final int[] BTN_CODES = {32, 27, Inp.KC_MUSIC, Inp.KC_SOUND};

    private float dpadX, dpadY, dpadR, fireX, fireY, fireR;
    private final RectF[] btnRects = new RectF[BTN_LABELS.length];
    private final boolean[] btnDown = new boolean[BTN_LABELS.length];

    private final int[] pointerTarget = new int[16];
    private final int[] pointerButton = new int[16];
    private int dpadPointer = -1, dpadVk = -1;
    private int firePointer = -1;
    private boolean touchControlsVisible = true;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ---- gamepad analog stick ----------------------------------------------
    private int stickVk = -1;

    // ---- soft keyboard (high-score initials) -------------------------------
    private boolean imeShown = false;

    GameView(Context ctx) {
        super(ctx);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
        blit.setFilterBitmap(false);
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setFakeBoldText(true);
        for (int i = 0; i < btnRects.length; i++) {
            btnRects[i] = new RectF();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestFocus();
        postOnAnimation(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float scale = Math.min(w / (float) Vid.W, h / (float) Vid.H);
        int gw = Math.round(Vid.W * scale), gh = Math.round(Vid.H * scale);
        int gx = (w - gw) / 2, gy = (h - gh) / 2;
        gameRect.set(gx, gy, gx + gw, gy + gh);

        float density = getResources().getDisplayMetrics().density;
        float pad = 16 * density;
        dpadR = Math.min(h * 0.24f, 110 * density);
        fireR = dpadR * 0.6f;
        dpadX = pad + dpadR;
        dpadY = h - pad - dpadR;
        fireX = w - pad - fireR * 1.4f;
        fireY = h - pad - fireR * 1.4f;

        float bw = 64 * density, bh = 36 * density, gap = 8 * density;
        for (int i = 0; i < btnRects.length; i++) {
            float top = pad + i * (bh + gap);
            btnRects[i].set(w - pad - bw, top, w - pad, top + bh);
        }
        text.setTextSize(14 * density);
        stroke.setStrokeWidth(2 * density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        Vid.renderArgb(pixels);
        frame.setPixels(pixels, 0, Vid.W, 0, 0, Vid.W, Vid.H);
        canvas.drawBitmap(frame, null, gameRect, blit);
        if (touchControlsVisible) {
            drawControls(canvas);
        }
    }

    private void drawControls(Canvas c) {
        // D-pad
        fill.setColor(0x40FFFFFF);
        c.drawCircle(dpadX, dpadY, dpadR, fill);
        stroke.setColor(0x80FFFFFF);
        c.drawCircle(dpadX, dpadY, dpadR, stroke);
        float a = dpadR * 0.55f, s = dpadR * 0.22f;
        drawArrow(c, dpadX, dpadY - a, 0, -s, dpadVk == Keys.VK_UP);
        drawArrow(c, dpadX, dpadY + a, 0, s, dpadVk == Keys.VK_DOWN);
        drawArrow(c, dpadX - a, dpadY, -s, 0, dpadVk == Keys.VK_LEFT);
        drawArrow(c, dpadX + a, dpadY, s, 0, dpadVk == Keys.VK_RIGHT);

        // Fire
        fill.setColor(firePointer >= 0 ? 0xA0FF5555 : 0x50FF5555);
        c.drawCircle(fireX, fireY, fireR, fill);
        stroke.setColor(0xA0FFFFFF);
        c.drawCircle(fireX, fireY, fireR, stroke);
        text.setColor(Color.WHITE);
        c.drawText("FIRE", fireX, fireY + text.getTextSize() * 0.35f, text);

        // Small buttons
        for (int i = 0; i < btnRects.length; i++) {
            RectF r = btnRects[i];
            fill.setColor(btnDown[i] ? 0x90FFFFFF : 0x40FFFFFF);
            c.drawRoundRect(r, 8, 8, fill);
            c.drawText(BTN_LABELS[i], r.centerX(), r.centerY() + text.getTextSize() * 0.35f, text);
        }
    }

    private void drawArrow(Canvas c, float x, float y, float dx, float dy, boolean active) {
        fill.setColor(active ? 0xE0FFFFFF : 0x90FFFFFF);
        android.graphics.Path p = new android.graphics.Path();
        // Triangle pointing along (dx,dy).
        p.moveTo(x + dx, y + dy);
        p.lineTo(x - dx - dy, y - dy + dx);
        p.lineTo(x - dx + dy, y - dy - dx);
        p.close();
        c.drawPath(p, fill);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!touchControlsVisible) {
            touchControlsVisible = true;
        }
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                pointerDown(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    if (e.getPointerId(i) == dpadPointer) {
                        updateDpad(e.getX(i), e.getY(i));
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                pointerUp(e.getPointerId(idx));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                for (int id = 0; id < pointerTarget.length; id++) {
                    pointerUp(id);
                }
                break;
            default:
                break;
        }
        return true;
    }

    private void pointerDown(int id, float x, float y) {
        if (id >= pointerTarget.length) return;
        for (int i = 0; i < btnRects.length; i++) {
            if (btnRects[i].contains(x, y)) {
                pointerTarget[id] = T_BUTTON;
                pointerButton[id] = i;
                btnDown[i] = true;
                Hw.pushTyped(BTN_CODES[i]);
                return;
            }
        }
        if (dist(x, y, dpadX, dpadY) <= dpadR * 1.15f) {
            pointerTarget[id] = T_DPAD;
            dpadPointer = id;
            updateDpad(x, y);
            return;
        }
        if (dist(x, y, fireX, fireY) <= fireR * 1.3f) {
            pointerTarget[id] = T_FIRE;
            firePointer = id;
            Hw.keyEvent(Keys.VK_F1, Keys.CHAR_UNDEFINED, true);
            return;
        }
        pointerTarget[id] = T_SCREEN;
        if (Hw.textInput) {
            showIme(true);  // re-open the keyboard if it was dismissed
        } else {
            Hw.keyEvent(Keys.VK_ENTER, '\n', true);
        }
    }

    private void pointerUp(int id) {
        if (id >= pointerTarget.length) return;
        switch (pointerTarget[id]) {
            case T_DPAD:
                setDpadVk(-1);
                dpadPointer = -1;
                break;
            case T_FIRE:
                Hw.keyEvent(Keys.VK_F1, Keys.CHAR_UNDEFINED, false);
                firePointer = -1;
                break;
            case T_BUTTON:
                btnDown[pointerButton[id]] = false;
                break;
            case T_SCREEN:
                Hw.keyEvent(Keys.VK_ENTER, '\n', false);
                break;
            default:
                break;
        }
        pointerTarget[id] = T_NONE;
    }

    private void updateDpad(float x, float y) {
        float dx = x - dpadX, dy = y - dpadY;
        int vk;
        if (Math.hypot(dx, dy) < dpadR * 0.18f) {
            vk = -1;
        } else if (Math.abs(dx) > Math.abs(dy)) {
            vk = dx > 0 ? Keys.VK_RIGHT : Keys.VK_LEFT;
        } else {
            vk = dy > 0 ? Keys.VK_DOWN : Keys.VK_UP;
        }
        setDpadVk(vk);
    }

    private void setDpadVk(int vk) {
        if (vk == dpadVk) return;
        if (dpadVk >= 0) Hw.keyEvent(dpadVk, Keys.CHAR_UNDEFINED, false);
        dpadVk = vk;
        if (vk >= 0) Hw.keyEvent(vk, Keys.CHAR_UNDEFINED, true);
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    // ------------------------------------------------- keyboard and gamepads

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int vk = mapKey(keyCode);
        if (vk < 0) return super.onKeyDown(keyCode, event);
        touchControlsVisible = false;
        if (event.getRepeatCount() == 0) {
            Hw.keyEvent(vk, keyChar(event), true);
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        int vk = mapKey(keyCode);
        if (vk < 0) return super.onKeyUp(keyCode, event);
        Hw.keyEvent(vk, keyChar(event), false);
        return true;
    }

    private static char keyChar(KeyEvent e) {
        int u = e.getUnicodeChar();
        return u > 0 && u < 0xFFFF ? (char) u : Keys.CHAR_UNDEFINED;
    }

    /** Android key code -> {@link Keys} code, or -1 if not used by the game. */
    static int mapKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return Keys.VK_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return Keys.VK_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return Keys.VK_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return Keys.VK_RIGHT;
            case KeyEvent.KEYCODE_F1:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_R1:
                return Keys.VK_F1;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_START:
                return Keys.VK_ENTER;
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                return Keys.VK_ESCAPE;
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_BUTTON_Y:
                return Keys.VK_SPACE;
            case KeyEvent.KEYCODE_DEL: return Keys.VK_BACK_SPACE;
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return Keys.VK_CONTROL;
            case KeyEvent.KEYCODE_F7: return Keys.VK_F7;
            case KeyEvent.KEYCODE_F8: return Keys.VK_F8;
            case KeyEvent.KEYCODE_F9: return Keys.VK_F9;
            case KeyEvent.KEYCODE_F10: return Keys.VK_F10;
            case KeyEvent.KEYCODE_MINUS: return Keys.VK_MINUS;
            case KeyEvent.KEYCODE_PLUS: return Keys.VK_PLUS;
            case KeyEvent.KEYCODE_EQUALS: return Keys.VK_EQUALS;
            case KeyEvent.KEYCODE_NUMPAD_ADD: return Keys.VK_ADD;
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: return Keys.VK_SUBTRACT;
            default:
                if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
                    return 'A' + (keyCode - KeyEvent.KEYCODE_A);
                }
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    return '0' + (keyCode - KeyEvent.KEYCODE_0);
                }
                return -1;
        }
    }

    /** Left analog stick / hat switch of a gamepad -> arrow keys. */
    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                || e.getAction() != MotionEvent.ACTION_MOVE) {
            return super.onGenericMotionEvent(e);
        }
        float x = e.getAxisValue(MotionEvent.AXIS_X), y = e.getAxisValue(MotionEvent.AXIS_Y);
        float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (Math.abs(hx) > Math.abs(x)) x = hx;
        if (Math.abs(hy) > Math.abs(y)) y = hy;
        int vk = -1;
        if (Math.max(Math.abs(x), Math.abs(y)) > 0.5f) {
            if (Math.abs(x) > Math.abs(y)) vk = x > 0 ? Keys.VK_RIGHT : Keys.VK_LEFT;
            else vk = y > 0 ? Keys.VK_DOWN : Keys.VK_UP;
        }
        if (vk != stickVk) {
            touchControlsVisible = false;
            if (stickVk >= 0) Hw.keyEvent(stickVk, Keys.CHAR_UNDEFINED, false);
            stickVk = vk;
            if (vk >= 0) Hw.keyEvent(vk, Keys.CHAR_UNDEFINED, true);
        }
        return true;
    }

    // --------------------------------------------- soft keyboard for initials

    private void pollTextInput() {
        boolean want = Hw.textInput;
        if (want != imeShown) {
            showIme(want);
        }
    }

    private void showIme(boolean show) {
        imeShown = show;
        InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        if (show) {
            requestFocus();
            imm.restartInput(this);
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return Hw.textInput;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo out) {
        out.inputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        out.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_ACTION_DONE;
        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence t, int newCursorPosition) {
                for (int i = 0; i < t.length(); i++) {
                    char c = Character.toUpperCase(t.charAt(i));
                    if (c >= ' ' && c < 127) {
                        Hw.pushTyped(c);
                    }
                }
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int before, int after) {
                for (int i = 0; i < Math.max(1, before); i++) {
                    Hw.pushTyped(8);
                }
                return true;
            }
        };
    }
}
