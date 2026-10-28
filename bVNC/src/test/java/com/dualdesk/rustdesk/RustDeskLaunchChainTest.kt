package com.dualdesk.rustdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the hand-off order on a plain JVM.
 *
 * Until now only the deep link had ever run, because it succeeds on every test device: the two
 * fallbacks were written but never provoked. A fake transport provokes them, and records the
 * calls, so the order is asserted rather than assumed.
 *
 * This does not prove that RustDesk accepts the activity intent on a real phone — only a device
 * can say that. It proves that DualDesk tries what it claims, in the order it claims, and reports
 * the outcome it actually got.
 */
class RustDeskLaunchChainTest {

    private class FakeTransport(
        override val installed: Boolean = true,
        private val deepLinkAccepted: Boolean = false,
        private val activityAccepted: Boolean = true,
        private val clipboard: RustDeskClipboard = RustDeskClipboard.ID_ONLY,
    ) : RustDeskTransport {

        val calls = mutableListOf<String>()
        var lastDeviceId: String? = null
        var lastPassword: String? = null

        override fun openDeepLink(deviceId: String): Boolean {
            calls += "deepLink"
            return deepLinkAccepted
        }

        override fun openActivity(): Boolean {
            calls += "activity"
            return activityAccepted
        }

        override fun copyForPaste(deviceId: String, password: String?): RustDeskClipboard {
            calls += "clipboard"
            lastDeviceId = deviceId
            lastPassword = password
            return clipboard
        }
    }

    @Test
    fun `an accepted deep link stops the chain there`() {
        val transport = FakeTransport(deepLinkAccepted = true)

        val result = RustDeskLaunchChain.launch("123456789", "secret", transport)

        assertTrue(result.launched)
        assertEquals(RustDeskLaunchChain.STRATEGY_DEEP_LINK, result.strategy)
        assertEquals(RustDeskMessage.NONE, result.message)
        assertEquals(listOf("deepLink"), transport.calls)
        assertNull("the clipboard must not be touched", transport.lastDeviceId)
    }

    @Test
    fun `a refused deep link falls back to the activity, password through the clipboard`() {
        val transport = FakeTransport(
            deepLinkAccepted = false,
            activityAccepted = true,
            clipboard = RustDeskClipboard.PASSWORD_AND_ID,
        )

        val result = RustDeskLaunchChain.launch("123456789", "secret", transport)

        assertTrue(result.launched)
        assertEquals(RustDeskLaunchChain.STRATEGY_ACTIVITY_AND_CLIPBOARD, result.strategy)
        assertEquals(RustDeskMessage.CLIPBOARD_WITH_PASSWORD, result.message)
        assertEquals(listOf("deepLink", "clipboard", "activity"), transport.calls)
        assertEquals("123456789", transport.lastDeviceId)
        assertEquals("secret", transport.lastPassword)
    }

    @Test
    fun `without a password the identifier is still copied and the message says so`() {
        val transport = FakeTransport(clipboard = RustDeskClipboard.ID_ONLY)

        val result = RustDeskLaunchChain.launch("123456789", null, transport)

        assertTrue(result.launched)
        // The strategy names the opening, the message names the clipboard: both happen here.
        assertEquals(RustDeskLaunchChain.STRATEGY_ACTIVITY, result.strategy)
        assertEquals(RustDeskMessage.CLIPBOARD_ID_ONLY, result.message)
        assertEquals(listOf("deepLink", "clipboard", "activity"), transport.calls)
    }

    @Test
    fun `an unavailable clipboard is reported instead of pretending the id was copied`() {
        val transport = FakeTransport(clipboard = RustDeskClipboard.UNAVAILABLE)

        val result = RustDeskLaunchChain.launch("123456789", "secret", transport)

        assertTrue(result.launched)
        assertEquals(RustDeskLaunchChain.STRATEGY_ACTIVITY, result.strategy)
        assertEquals(RustDeskMessage.CLIPBOARD_UNAVAILABLE, result.message)
    }

    @Test
    fun `both openings refused is a failure, after all three attempts`() {
        val transport = FakeTransport(deepLinkAccepted = false, activityAccepted = false)

        val result = RustDeskLaunchChain.launch("123456789", "secret", transport)

        assertFalse(result.launched)
        assertEquals(RustDeskLaunchChain.STRATEGY_NONE, result.strategy)
        assertEquals(RustDeskMessage.NO_RESPONSE, result.message)
        assertEquals(listOf("deepLink", "clipboard", "activity"), transport.calls)
    }

    @Test
    fun `nothing is attempted when RustDesk is absent`() {
        val transport = FakeTransport(installed = false)

        val result = RustDeskLaunchChain.launch("123456789", "secret", transport)

        assertFalse(result.launched)
        assertEquals(RustDeskMessage.NOT_INSTALLED, result.message)
        assertTrue("no intent may be sent to a missing package", transport.calls.isEmpty())
    }

    @Test
    fun `a blank identifier is refused before the installation is even looked up`() {
        val transport = FakeTransport(deepLinkAccepted = true)

        val result = RustDeskLaunchChain.launch("   ", "secret", transport)

        assertFalse(result.launched)
        assertEquals(RustDeskMessage.NO_DEVICE_ID, result.message)
        assertTrue(transport.calls.isEmpty())
    }
}
