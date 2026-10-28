package com.dualdesk.rdp;

import android.content.Context;
import android.util.Log;

/**
 * Entry point for the embedded RustDesk session, callable from the connection grid.
 *
 * Kept separate from the activity so the grid can ask one question — "can this build run the
 * session itself?" — without knowing anything about how the session is drawn. When the answer is
 * no, the caller keeps its Intent hand-off untouched.
 */
public final class RustDeskEmbedded {

    private static final String TAG = "DualDeskRustDesk";

    private RustDeskEmbedded() {
    }

    /** True when `librustdesk.so` is in this APK and loadable on this device. */
    public static boolean isAvailable() {
        return RustDeskNative.INSTANCE.load();
    }

    /** The core version, or `null` when the library is absent. */
    public static String version() {
        if (!isAvailable()) {
            return null;
        }
        try {
            return RustDeskNative.INSTANCE.version();
        } catch (Throwable t) {
            Log.w(TAG, "core version unavailable: " + t);
            return null;
        }
    }

    /**
     * Opens an embedded session.
     *
     * Returns `false` — rather than failing silently — when the library is absent or the activity
     * cannot be started, so the caller falls back to handing the session to the RustDesk app.
     */
    public static boolean open(Context context, String deviceId, String password) {
        if (deviceId == null || deviceId.trim().isEmpty() || !isAvailable()) {
            return false;
        }
        try {
            context.startActivity(RustDeskEmbeddedActivity.intentFor(context, deviceId, password));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "could not start the embedded session: " + t);
            return false;
        }
    }
}
