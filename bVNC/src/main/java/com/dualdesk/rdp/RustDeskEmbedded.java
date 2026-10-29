package com.dualdesk.rdp;

import android.content.Context;
import android.content.SharedPreferences;
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

    /** Réglages du serveur de rendez-vous, partagés par l'interface et la session. */
    public static final String SERVER_PREFS = "rustdesk_server";
    public static final String PREF_SERVER = "server";
    public static final String PREF_KEY = "key";

    private RustDeskEmbedded() {
    }

    /**
     * Transmet au cœur le serveur RustDesk configuré, s'il y en a un.
     *
     * Le cœur interroge le serveur public tant qu'on ne lui a rien dit. Avec un serveur privé,
     * il cherche le pair au mauvais endroit et répond « the target device is offline or does
     * not exist » alors que le pair est joignable : il faut `custom-rendezvous-server` et, quand
     * le serveur en impose une, sa clé publique. Les deux sont facultatifs — sans eux, rien ne
     * change pour un utilisateur des serveurs publics.
     */
    public static void applyServerOptions(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE);
        // Toujours transmettre, valeur vide comprise : le cœur écrit ses options dans son
        // dossier personnel et les relit au démarrage suivant. Se contenter d'appeler `setOption`
        // quand le champ est rempli laisse l'ancien serveur actif après un effacement, et l'appel
        // échoue alors contre une adresse que l'interface ne montre plus.
        RustDeskNative.INSTANCE.setOption("custom-rendezvous-server",
                prefs.getString(PREF_SERVER, "").trim());
        RustDeskNative.INSTANCE.setOption("key", prefs.getString(PREF_KEY, "").trim());
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
