package com.dualdesk.rustdesk

/**
 * What the hand-off asks of the outside world.
 *
 * Every Android call lives behind this interface, so the order of the strategies can be
 * exercised on a plain JVM with a fake transport — a device cannot prove an ordering that
 * never happens, because on the test phones the deep link always succeeds.
 */
interface RustDeskTransport {

    val installed: Boolean

    /** True when the installed application accepted the `rustdesk://` link. */
    fun openDeepLink(deviceId: String): Boolean

    /** True when the launcher activity started. */
    fun openActivity(): Boolean

    /** What the clipboard could take, so the message can match what really happened. */
    fun copyForPaste(deviceId: String, password: String?): RustDeskClipboard
}

/** Result of a clipboard attempt: the identifier always goes in, the password when there is one. */
enum class RustDeskClipboard {
    PASSWORD_AND_ID,
    ID_ONLY,

    /** No clipboard service: nothing was copied and the message must not claim otherwise. */
    UNAVAILABLE,
}

/** Message to show, resolved to a string by the Android layer. */
enum class RustDeskMessage {
    NONE,
    NO_DEVICE_ID,
    NOT_INSTALLED,
    NO_RESPONSE,
    CLIPBOARD_WITH_PASSWORD,
    CLIPBOARD_ID_ONLY,
    CLIPBOARD_UNAVAILABLE,
}

data class RustDeskChainResult(
    val launched: Boolean,
    val strategy: String,
    val message: RustDeskMessage,
)

/**
 * The hand-off order, and nothing else: deep link, then activity, with the clipboard as the
 * only channel that survives the jump between the two applications.
 *
 * Kept free of Android imports on purpose (see [RustDeskTransport]).
 */
object RustDeskLaunchChain {

    const val STRATEGY_DEEP_LINK = "deep link rustdesk://"
    const val STRATEGY_ACTIVITY_AND_CLIPBOARD = "activity + clipboard"
    const val STRATEGY_ACTIVITY = "explicit activity"
    const val STRATEGY_NONE = "none"

    fun launch(
        deviceId: String,
        password: String?,
        transport: RustDeskTransport,
    ): RustDeskChainResult {
        if (deviceId.isBlank()) {
            return RustDeskChainResult(false, STRATEGY_NONE, RustDeskMessage.NO_DEVICE_ID)
        }
        if (!transport.installed) {
            return RustDeskChainResult(false, STRATEGY_NONE, RustDeskMessage.NOT_INSTALLED)
        }
        if (transport.openDeepLink(deviceId)) {
            return RustDeskChainResult(true, STRATEGY_DEEP_LINK, RustDeskMessage.NONE)
        }

        val clipboard = transport.copyForPaste(deviceId, password)
        if (!transport.openActivity()) {
            return RustDeskChainResult(false, STRATEGY_NONE, RustDeskMessage.NO_RESPONSE)
        }

        val withPassword = clipboard == RustDeskClipboard.PASSWORD_AND_ID
        val message = when (clipboard) {
            RustDeskClipboard.PASSWORD_AND_ID -> RustDeskMessage.CLIPBOARD_WITH_PASSWORD
            RustDeskClipboard.ID_ONLY -> RustDeskMessage.CLIPBOARD_ID_ONLY
            RustDeskClipboard.UNAVAILABLE -> RustDeskMessage.CLIPBOARD_UNAVAILABLE
        }
        return RustDeskChainResult(
            launched = true,
            strategy = if (withPassword) STRATEGY_ACTIVITY_AND_CLIPBOARD else STRATEGY_ACTIVITY,
            message = message,
        )
    }
}
