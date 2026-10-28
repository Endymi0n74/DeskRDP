package com.dualdesk.rdp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.nio.ByteBuffer;

/**
 * Embedded RustDesk session: the core runs in our own process and we draw its frames.
 *
 * This is the difference that matters against handing the session to the installed RustDesk
 * application: here the application knows the session state, its frame rate and its desktop size,
 * because it produces the pixels itself.
 *
 * The library is only shipped for arm64-v8a. When it is absent — another ABI, or a build made
 * without {@code -Pdualdesk.rustdeskLib=...} — the grid falls back to the Intent hand-off, and
 * this activity is never started.
 */
public class RustDeskEmbeddedActivity extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = "DualDeskRustDesk";

    public static final String EXTRA_DEVICE_ID = "com.dualdesk.rdp.extra.DEVICE_ID";
    public static final String EXTRA_PASSWORD = "com.dualdesk.rdp.extra.PASSWORD";

    /** 0 = move only, 1 = left button, 2 = right button (RustDesk's own mask). */
    private static final int MASK_MOVE = 0;
    private static final int MASK_LEFT = 1;
    private static final int MASK_RIGHT = 2;

    private static final long LONG_PRESS_MS = 480;

    private SurfaceView surfaceView;
    private TextView statusView;

    private volatile String sessionId;
    private volatile boolean running;
    private Thread pump;

    private Bitmap frame;
    private int remoteWidth;
    private int remoteHeight;

    private final Paint framePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    // Geometry of the last drawn frame, needed to translate touches into remote coordinates.
    private volatile float drawnScale = 1f;
    private volatile float drawnLeft = 0f;
    private volatile float drawnTop = 0f;

    private float downX;
    private float downY;
    private boolean longPressFired;
    private Runnable longPressRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final String deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);
        final String password = getIntent().getStringExtra(EXTRA_PASSWORD);
        if (deviceId == null || deviceId.trim().isEmpty()) {
            Log.w(TAG, "embedded session without a device id");
            finish();
            return;
        }

        surfaceView = new SurfaceView(this);
        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(14f);
        statusView.setBackgroundColor(0x99000000);
        statusView.setPadding(24, 16, 24, 16);

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(surfaceView, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(statusView, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP | android.view.Gravity.START));
        setContentView(root);

        surfaceView.getHolder().addCallback(this);
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.requestFocus();
        installTouchHandling();

        setStatus("RustDesk: starting the embedded core");
        startSession(deviceId, password);
    }

    private void setStatus(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusView != null) {
                    statusView.setText(text);
                }
            }
        });
    }

    /** Prepares and starts the core, then starts the frame pump once connected. */
    private void startSession(final String deviceId, final String password) {
        if (!RustDeskNative.INSTANCE.load()) {
            setStatus("RustDesk: library absent from this build");
            return;
        }

        final String homeDir = getFilesDir().getAbsolutePath();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    setStatus("RustDesk " + RustDeskNative.INSTANCE.version() + ": opening session");

                    int prepared = RustDeskNative.INSTANCE.init(homeDir);
                    if (prepared != 0) {
                        setStatus("RustDesk: core refused to start (code " + prepared + ")");
                        return;
                    }

                    String id = RustDeskNative.INSTANCE.connect(deviceId, password == null ? "" : password);
                    if (id == null) {
                        setStatus("RustDesk: session refused for " + deviceId);
                        return;
                    }
                    sessionId = id;

                    int launch = RustDeskNative.INSTANCE.start(id);
                    if (launch != 0) {
                        setStatus("RustDesk: connection not started (code " + launch + ")");
                        return;
                    }

                    running = true;
                    pump = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            pumpFrames();
                        }
                    }, "dualdesk-rustdesk-pump");
                    pump.start();
                } catch (Throwable t) {
                    Log.w(TAG, "embedded session failed to start", t);
                    setStatus("RustDesk: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        }, "dualdesk-rustdesk-start").start();
    }

    /**
     * Follows the core and draws each frame it publishes.
     *
     * No fixed frame rate: a frame is drawn when the core has one, which is why a still desktop
     * costs nothing instead of repeating the same image thirty times a second.
     */
    private void pumpFrames() {
        long frames = 0;
        long framesSinceReport = 0;
        long lastReport = System.currentTimeMillis();
        boolean requested = false;
        String current = sessionId;

        while (running && current != null) {
            int state = RustDeskNative.INSTANCE.state(current);
            if (state == 3) {
                setStatus("RustDesk: session closed by the host");
                break;
            }
            if (state == 2 && !requested) {
                // The host captures nothing until it is asked to: without this the session is
                // connected, silent and frameless.
                RustDeskNative.INSTANCE.requestDisplay(current, 0);
                requested = true;
                setStatus("RustDesk: connected, waiting for the first frame");
            }

            if (requested) {
                ByteBuffer buffer = RustDeskNative.INSTANCE.rgbaBuffer(current, 0);
                if (buffer != null) {
                    int width = RustDeskNative.INSTANCE.rgbaWidth(current, 0);
                    int height = RustDeskNative.INSTANCE.rgbaHeight(current, 0);
                    if (width > 0 && height > 0) {
                        if (frame == null || frame.getWidth() != width || frame.getHeight() != height) {
                            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                            remoteWidth = width;
                            remoteHeight = height;
                            Log.i(TAG, "remote desktop: " + width + "x" + height);
                        }
                        frame.copyPixelsFromBuffer(buffer);
                        RustDeskNative.INSTANCE.rgbaConsume(current, 0);
                        drawFrame();
                        frames++;
                        framesSinceReport++;
                    } else {
                        RustDeskNative.INSTANCE.rgbaConsume(current, 0);
                    }
                }
            }

            long now = System.currentTimeMillis();
            long elapsed = now - lastReport;
            if (elapsed >= 1000) {
                int fps = (int) (framesSinceReport * 1000 / elapsed);
                setStatus("RustDesk " + remoteWidth + "x" + remoteHeight
                        + " — " + fps + " fps — " + frames + " frames");
                framesSinceReport = 0;
                lastReport = now;
            }

            try {
                Thread.sleep(4);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void drawFrame() {
        Bitmap image = frame;
        SurfaceHolder holder = surfaceView.getHolder();
        if (image == null || !holder.getSurface().isValid()) {
            return;
        }
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas == null) {
                return;
            }
            canvas.drawColor(Color.BLACK);
            float scale = Math.min(
                    canvas.getWidth() / (float) image.getWidth(),
                    canvas.getHeight() / (float) image.getHeight());
            float drawnWidth = image.getWidth() * scale;
            float drawnHeight = image.getHeight() * scale;
            float left = (canvas.getWidth() - drawnWidth) / 2f;
            float top = (canvas.getHeight() - drawnHeight) / 2f;

            srcRect.set(0, 0, image.getWidth(), image.getHeight());
            dstRect.set(left, top, left + drawnWidth, top + drawnHeight);
            canvas.drawBitmap(image, srcRect, dstRect, framePaint);

            drawnScale = scale;
            drawnLeft = left;
            drawnTop = top;
        } catch (Throwable t) {
            Log.w(TAG, "could not present the frame", t);
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas);
                } catch (Throwable ignored) {
                    // Surface destroyed in between: the session does not depend on it.
                }
            }
        }
    }

    // ------------------------------------------------------------------ input

    /**
     * Pointer mapping: one finger moves the pointer, a brief touch clicks left, a long press
     * clicks right, a drag after the long press drags with the right button held.
     */
    private void installTouchHandling() {
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                final String current = sessionId;
                if (current == null || remoteWidth <= 0 || remoteHeight <= 0) {
                    return true;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        longPressFired = false;
                        longPressRunnable = new Runnable() {
                            @Override
                            public void run() {
                                longPressFired = true;
                                sendMouse(current, MASK_RIGHT, downX, downY);
                            }
                        };
                        surfaceView.postDelayed(longPressRunnable, LONG_PRESS_MS);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - downX) > 8 || Math.abs(event.getY() - downY) > 8) {
                            cancelLongPress();
                            sendMouse(current, longPressFired ? MASK_RIGHT : MASK_MOVE,
                                    event.getX(), event.getY());
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        boolean wasLong = longPressFired;
                        cancelLongPress();
                        if (wasLong) {
                            sendMouse(current, MASK_MOVE, event.getX(), event.getY());
                        } else {
                            sendMouse(current, MASK_LEFT, event.getX(), event.getY());
                            sendMouse(current, MASK_MOVE, event.getX(), event.getY());
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void cancelLongPress() {
        if (longPressRunnable != null) {
            surfaceView.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    /** Converts a view coordinate into a remote one, using the geometry of the last frame. */
    private void sendMouse(String id, int mask, float viewX, float viewY) {
        float scale = drawnScale;
        if (scale <= 0f) {
            return;
        }
        int x = (int) ((viewX - drawnLeft) / scale);
        int y = (int) ((viewY - drawnTop) / scale);
        x = Math.max(0, Math.min(remoteWidth - 1, x));
        y = Math.max(0, Math.min(remoteHeight - 1, y));
        RustDeskNative.INSTANCE.mouse(id, mask, x, y);
    }

    /** Physical keyboard: key codes are translated to the names the core expects. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String name = vkNameFor(keyCode);
        String current = sessionId;
        if (name == null || current == null) {
            return super.onKeyDown(keyCode, event);
        }
        RustDeskNative.INSTANCE.key(current, name, true);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        String name = vkNameFor(keyCode);
        String current = sessionId;
        if (name == null || current == null) {
            return super.onKeyUp(keyCode, event);
        }
        RustDeskNative.INSTANCE.key(current, name, false);
        return true;
    }

    /** Only the keys listed here reach the host; an unmapped key does nothing at all. */
    private static String vkNameFor(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER: return "VK_RETURN";
            case KeyEvent.KEYCODE_DEL: return "VK_BACK";
            case KeyEvent.KEYCODE_TAB: return "VK_TAB";
            case KeyEvent.KEYCODE_ESCAPE: return "VK_ESCAPE";
            case KeyEvent.KEYCODE_SPACE: return "VK_SPACE";
            case KeyEvent.KEYCODE_DPAD_UP: return "VK_UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "VK_DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "VK_LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "VK_RIGHT";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "VK_DELETE";
            case KeyEvent.KEYCODE_MOVE_HOME: return "VK_HOME";
            case KeyEvent.KEYCODE_MOVE_END: return "VK_END";
            case KeyEvent.KEYCODE_PAGE_UP: return "VK_PRIOR";
            case KeyEvent.KEYCODE_PAGE_DOWN: return "VK_NEXT";
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return "VK_SHIFT";
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT: return "VK_CONTROL";
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT: return "VK_MENU";
            default: break;
        }
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return "VK_" + (char) ('A' + (keyCode - KeyEvent.KEYCODE_A));
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return "VK_" + (char) ('0' + (keyCode - KeyEvent.KEYCODE_0));
        }
        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12) {
            return "VK_F" + (keyCode - KeyEvent.KEYCODE_F1 + 1);
        }
        return null;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        drawFrame();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        drawFrame();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // The session survives a surface loss: only drawing stops.
    }

    @Override
    protected void onDestroy() {
        running = false;
        String current = sessionId;
        sessionId = null;
        if (pump != null) {
            pump.interrupt();
            pump = null;
        }
        if (current != null) {
            RustDeskNative.INSTANCE.close(current);
        }
        super.onDestroy();
    }

    /** Builds the intent that opens an embedded session for [deviceId]. */
    public static Intent intentFor(Context context, String deviceId, String password) {
        Intent intent = new Intent(context, RustDeskEmbeddedActivity.class);
        intent.putExtra(EXTRA_DEVICE_ID, deviceId);
        intent.putExtra(EXTRA_PASSWORD, password);
        return intent;
    }
}
