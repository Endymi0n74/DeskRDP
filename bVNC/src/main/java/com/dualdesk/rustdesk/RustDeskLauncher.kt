package com.dualdesk.rustdesk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.undatech.remoteClientUi.R

/**
 * Outcome of a launch attempt, in a shape that is easy to consume from Java.
 *
 * [strategy] names what actually happened (deep link, activity, clipboard) because the
 * difference is invisible to the user and decisive when diagnosing a device where RustDesk
 * answers to none of the standard intents.
 */
data class LaunchOutcome(
    val launched: Boolean,
    val strategy: String,
    val message: String?,
)

/**
 * Hands a RustDesk session to the installed RustDesk application.
 *
 * This class does not speak the RustDesk protocol: it prepares the session (device id, and the
 * password when one is known) and lets the upstream application take over. The alternative —
 * embedding librustdesk through JNI — is a much larger commitment than a launcher, and the
 * launcher is what a connection manager actually needs.
 *
 * The order of the attempts lives in [RustDeskLaunchChain], which is free of Android imports and
 * therefore exercised by plain JVM tests; this class only supplies the Android transport.
 */
class RustDeskLauncher(private val context: Context) {

    fun isInstalled(): Boolean = installedVersion() != null

    fun installedVersion(): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager
                .getPackageInfo(RustDeskContract.PACKAGE, PackageManager.PackageInfoFlags.of(0L))
                .versionName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(RustDeskContract.PACKAGE, 0).versionName
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun launch(deviceId: String, password: String?): LaunchOutcome {
        val result = RustDeskLaunchChain.launch(deviceId, password, androidTransport())
        return LaunchOutcome(result.launched, result.strategy, messageFor(result.message))
    }

    private fun androidTransport(): RustDeskTransport = object : RustDeskTransport {
        override val installed: Boolean get() = isInstalled()

        override fun openDeepLink(deviceId: String): Boolean = this@RustDeskLauncher.openDeepLink(deviceId)

        override fun openActivity(): Boolean = this@RustDeskLauncher.openActivity()

        override fun copyForPaste(deviceId: String, password: String?): RustDeskClipboard =
            this@RustDeskLauncher.copyForPaste(deviceId, password)
    }

    private fun messageFor(message: RustDeskMessage): String? = when (message) {
        RustDeskMessage.NONE -> null
        RustDeskMessage.NO_DEVICE_ID -> context.getString(R.string.rustdesk_no_device_id)
        RustDeskMessage.NOT_INSTALLED -> context.getString(R.string.rustdesk_not_installed)
        RustDeskMessage.NO_RESPONSE -> context.getString(R.string.rustdesk_no_response)
        RustDeskMessage.CLIPBOARD_WITH_PASSWORD -> context.getString(R.string.rustdesk_clipboard_with_password)
        RustDeskMessage.CLIPBOARD_ID_ONLY -> context.getString(R.string.rustdesk_clipboard_id_only)
        RustDeskMessage.CLIPBOARD_UNAVAILABLE -> context.getString(R.string.rustdesk_clipboard_unavailable)
    }

    private fun copyForPaste(deviceId: String, password: String?): RustDeskClipboard {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
            ?: return RustDeskClipboard.UNAVAILABLE
        val withPassword = !password.isNullOrEmpty()
        val payload = if (withPassword) "$deviceId $password" else deviceId
        clipboard.setPrimaryClip(ClipData.newPlainText("Remote access — RustDesk", payload))
        return if (withPassword) RustDeskClipboard.PASSWORD_AND_ID else RustDeskClipboard.ID_ONLY
    }

    private fun openDeepLink(deviceId: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RustDeskContract.deepLinkFor(deviceId)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startIfPossible(intent)
    }

    private fun openActivity(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(RustDeskContract.PACKAGE, RustDeskContract.MAIN_ACTIVITY)
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startIfPossible(intent)) return true

        // Some releases rename the activity: fall back to the launcher intent the application
        // publishes for itself.
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(RustDeskContract.PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return startIfPossible(launchIntent)
    }

    private fun startIfPossible(intent: Intent): Boolean = try {
        if (intent.component == null && intent.resolveActivity(context.packageManager) == null) {
            false
        } else {
            context.startActivity(intent)
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "RustDesk refused the intent: $e")
        false
    }

    private companion object {
        const val TAG = "DualDeskRustDesk"
    }
}
