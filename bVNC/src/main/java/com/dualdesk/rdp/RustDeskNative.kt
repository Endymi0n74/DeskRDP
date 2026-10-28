package com.dualdesk.rdp

import android.util.Log

/**
 * JNI bridge to the embedded RustDesk core (`librustdesk.so`).
 *
 * The symbol names on the native side are `Java_com_dualdesk_rdp_RustDeskNative_*`, so this
 * package is not a matter of taste: moving the class elsewhere would make every call fail with
 * `UnsatisfiedLinkError`.
 *
 * Loading is explicit and checkable: [load] returns `false` when the library is not in the APK,
 * which the UI must show as such rather than pretending the engine is present. Only arm64-v8a is
 * shipped — on any other ABI the library is simply absent and the caller falls back to handing
 * the session to the RustDesk application.
 */
object RustDeskNative {

    private const val TAG = "DualDeskRustDesk"

    private var loaded: Boolean? = null

    /** Loads the library once; `true` when it is usable. */
    @Synchronized
    fun load(): Boolean {
        loaded?.let { return it }
        val ok = try {
            System.loadLibrary("rustdesk")
            true
        } catch (t: Throwable) {
            Log.i(TAG, "librustdesk not in this APK: ${t.javaClass.simpleName}")
            false
        }
        loaded = ok
        return ok
    }

    val isAvailable: Boolean get() = load()

    external fun version(): String

    /**
     * Prepares the core: logging, and above all its home directory.
     *
     * [homeDir] must be a private application directory (in practice `filesDir`). Without it the
     * core cannot write its configuration and regenerates its key pair on every start, so the
     * device identity would change from one session to the next. Returns 0 on success.
     */
    external fun init(homeDir: String): Int

    /**
     * Sets a core option before a session is opened.
     *
     * Two matter here: `custom-rendezvous-server` (self-hosted server) and `key` (that server's
     * public key). Without them the core queries the public server and never finds a host
     * registered elsewhere. Returns 0 on success.
     */
    external fun setOption(key: String, value: String): Int

    /** Creates the session; returns its identifier, or `null` when the core refuses. */
    external fun connect(peerId: String, password: String): String?

    /** Starts connecting; 0 = started. */
    external fun start(sessionId: String): Int

    /** 0 unknown, 1 connecting, 2 connected, 3 closed. */
    external fun state(sessionId: String): Int

    /**
     * Asks the remote host to stream a display ([display] = 0 for the first one).
     *
     * The host does not start capturing before this request: without it the session stays open
     * and silent, without a single frame. Returns 0 on success.
     */
    external fun requestDisplay(sessionId: String, display: Int): Int

    external fun rgbaPtr(sessionId: String, display: Int): Long

    /**
     * Direct (zero-copy) view on the RGBA frame, valid until [rgbaConsume].
     *
     * Kotlin cannot dereference a raw pointer: this is how the core exposes a direct `ByteBuffer`
     * over its own buffer. Not marking it consumed simply leaves the previous frame in place.
     */
    external fun rgbaBuffer(sessionId: String, display: Int): java.nio.ByteBuffer?

    external fun rgbaWidth(sessionId: String, display: Int): Int

    external fun rgbaHeight(sessionId: String, display: Int): Int

    /** Releases the image so the core can publish a new one. */
    external fun rgbaConsume(sessionId: String, display: Int)

    external fun mouse(sessionId: String, mask: Int, x: Int, y: Int): Boolean

    external fun key(sessionId: String, name: String, down: Boolean): Boolean

    external fun text(sessionId: String, text: String): Boolean

    external fun close(sessionId: String)
}
