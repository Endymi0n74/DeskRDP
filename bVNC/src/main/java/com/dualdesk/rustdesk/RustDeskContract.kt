package com.dualdesk.rustdesk

/**
 * Published identifiers of the upstream RustDesk Android client.
 *
 * Verified against rustdesk/rustdesk documentation:
 *  - package and activity: `com.carriez.flutter_hbb` / `.MainActivity` (Flutter client);
 *  - intent launch is the documented integration point ("am start -n
 *    com.carriez.flutter_hbb/.MainActivity");
 *  - the `rustdesk://` deep-link filter has existed since 1.3.2 but is not honoured by every
 *    release, hence the activity fallback in [RustDeskLauncher].
 *
 * Anything changed here is an interface with an external application, not an internal detail:
 * re-verify it against a released RustDesk build.
 */
object RustDeskContract {

    const val PACKAGE = "com.carriez.flutter_hbb"

    const val MAIN_ACTIVITY = "com.carriez.flutter_hbb.MainActivity"

    const val DEEP_LINK_SCHEME = "rustdesk"

    fun deepLinkFor(deviceId: String): String = "$DEEP_LINK_SCHEME://$deviceId"
}
