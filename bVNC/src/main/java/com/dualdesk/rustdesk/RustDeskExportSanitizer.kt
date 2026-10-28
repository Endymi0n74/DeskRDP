package com.dualdesk.rustdesk

/**
 * Rewrites the RustDesk passwords of the settings document before it is written to a file.
 *
 * A stored password is sealed under a key of this device's keystore (see [RustDeskSecretCodec]),
 * and that key never leaves the device — not even across a reinstall, since uninstalling the
 * application destroys its keys. Writing that seal to a file meant to move between devices would
 * carry a value nothing can open. Two behaviours follow from it:
 *
 *  - without a passphrase, the password is **removed** from the document ([blankRustDeskPasswords]);
 *  - with one, it is **re-sealed under the passphrase** ([protectRustDeskPasswords]) and becomes
 *    readable on any device the file reaches, by whoever knows the phrase.
 *
 * The document produced by `SqliteElement.exportDbAsXmlToStream` is a list of
 * `<row ATTRIBUTE="value" ...>` elements, one per database record: the columns are attributes, and
 * their values are escaped, so a raw `<` or `"` never appears inside one. That is what makes a
 * textual pass safe here — no XML parser is needed to find an attribute value's end, and no row can
 * be confused with another. RustDesk seals are recognisable by their `ks1:` prefix; an access saved
 * before sealing existed (or on API 21/22, where no keystore key can be generated) carries a clear
 * password instead, which is why the row's connection type is checked as well.
 *
 * The result is verified before it is returned: if a device seal (`ks1:`) survived the pass, or a
 * RustDesk password is neither empty nor portable, the document is refused (null) rather than
 * written. A file that silently kept a secret the caller did not ask to carry would be worse than
 * no export.
 */
object RustDeskExportSanitizer {

    private const val ROW_PREFIX = "<row"
    private const val PASSWORD_ATTRIBUTE = "PASSWORD"
    private const val CONNECTION_TYPE_ATTRIBUTE = "CONNECTIONTYPE"
    private const val RUSTDESK_CONNECTION_TYPE = "6"

    /** Turns a stored password into what the file should carry, or null to leave it out. */
    fun interface PasswordExporter {
        fun export(stored: String): String?
    }

    /** The document to write, and what became of its RustDesk passwords. */
    class Sanitized(
        val document: String,
        val passwordsCarried: Int,
        val passwordsRemoved: Int,
    )

    /** Removes every RustDesk password: the export carries none. */
    @JvmStatic
    fun blankRustDeskPasswords(document: String): Sanitized? =
        protectRustDeskPasswords(document, null)

    /**
     * Rewrites every RustDesk password through `exporter`.
     *
     * A password the exporter declines (null) is left out, not kept as is: the exporter is the only
     * code that knows how to make a value readable elsewhere, and a value it could not read is
     * worth nothing in the file.
     *
     * Returns null when the document could not be cleaned, in which case the caller must not write
     * anything: the shape of the file changed and this pass can no longer guarantee what it holds.
     */
    @JvmStatic
    fun protectRustDeskPasswords(
        document: String,
        exporter: PasswordExporter?,
    ): Sanitized? {
        val output = StringBuilder(document.length)
        var carried = 0
        var removed = 0
        var index = 0
        while (true) {
            val rowStart = document.indexOf(ROW_PREFIX, index)
            if (rowStart < 0) {
                output.append(document, index, document.length)
                break
            }
            output.append(document, index, rowStart)
            val tagEnd = document.indexOf('>', rowStart)
            if (tagEnd < 0) {
                // Truncated document: keep what is there, the verification below refuses it.
                output.append(document, rowStart, document.length)
                break
            }
            val tag = document.substring(rowStart, tagEnd + 1)
            val rewrite = rewriteRow(tag, exporter)
            output.append(rewrite.tag)
            if (rewrite.carried) {
                carried++
            }
            if (rewrite.removed) {
                removed++
            }
            index = tagEnd + 1
        }

        val cleanedDocument = output.toString()
        if (!isClean(cleanedDocument)) {
            return null
        }
        return Sanitized(cleanedDocument, carried, removed)
    }

    private class Rewrite(val tag: String, val carried: Boolean, val removed: Boolean)

    /** The row with its password either replaced, emptied, or untouched. */
    private fun rewriteRow(tag: String, exporter: PasswordExporter?): Rewrite {
        val stored = attributeValue(tag, PASSWORD_ATTRIBUTE)
        val holdsSealedPassword = RustDeskSecretCodec.isSealed(stored)
        val isRustDesk = attributeValue(tag, CONNECTION_TYPE_ATTRIBUTE) == RUSTDESK_CONNECTION_TYPE
        if (!holdsSealedPassword && !isRustDesk) {
            return Rewrite(tag, false, false)
        }
        if (stored.isNullOrEmpty()) {
            return Rewrite(tag, false, false)
        }

        val portable = exporter?.export(stored)
        if (portable != null && isSafeAttributeValue(portable)) {
            return Rewrite(setAttribute(tag, PASSWORD_ATTRIBUTE, portable), true, false)
        }
        return Rewrite(setAttribute(tag, PASSWORD_ATTRIBUTE, ""), false, true)
    }

    /**
     * True when the text can sit inside a double-quoted attribute without breaking or escaping it:
     * the values this fork writes are hexadecimal with colons, and anything else is a bug worth
     * refusing rather than writing.
     */
    private fun isSafeAttributeValue(value: String): Boolean =
        value.isNotEmpty() && value.none { it == '"' || it == '<' || it == '>' || it == '&' }

    /** The value of `name` in this row, or null when the attribute is absent or unterminated. */
    private fun attributeValue(tag: String, name: String): String? {
        val start = attributeStart(tag, name)
        if (start < 0) {
            return null
        }
        val valueStart = start + name.length + 2
        val valueEnd = tag.indexOf('"', valueStart)
        if (valueEnd < 0) {
            return null
        }
        return tag.substring(valueStart, valueEnd)
    }

    /** Replaces the value of `name`, keeping the attribute in place. */
    private fun setAttribute(tag: String, name: String, value: String): String {
        val start = attributeStart(tag, name)
        if (start < 0) {
            return tag
        }
        val valueStart = start + name.length + 2
        val valueEnd = tag.indexOf('"', valueStart)
        if (valueEnd < 0) {
            return tag
        }
        return tag.substring(0, valueStart) + value + tag.substring(valueEnd)
    }

    /**
     * Position of the `name="` token, only when it starts an attribute: a column whose name merely
     * ends with the searched one must not be mistaken for it.
     */
    private fun attributeStart(tag: String, name: String): Int {
        val needle = "$name=\""
        var from = 0
        while (true) {
            val at = tag.indexOf(needle, from)
            if (at < 0) {
                return -1
            }
            val before = if (at > 0) tag[at - 1] else ' '
            if (before == ' ' || before == '\t' || before == '\n' || before == '\r') {
                return at
            }
            from = at + needle.length
        }
    }

    /**
     * True when nothing this class was meant to handle is left in the document.
     *
     * Three checks close the ways the pass could silently do nothing: a device seal (`ks1:`) left
     * anywhere fails it — that is the value only this device can open; a RustDesk password that is
     * neither empty nor portable fails it; and a password attribute found while no row tag was
     * recognised fails it too, since that shape is not the one this class knows how to read.
     */
    private fun isClean(document: String): Boolean {
        if (document.contains(RustDeskSecretCodec.PREFIX)) {
            return false
        }
        if (!document.contains(ROW_PREFIX) && document.contains("$PASSWORD_ATTRIBUTE=\"")) {
            return false
        }
        var index = 0
        while (true) {
            val rowStart = document.indexOf(ROW_PREFIX, index)
            if (rowStart < 0) {
                return true
            }
            val tagEnd = document.indexOf('>', rowStart)
            if (tagEnd < 0) {
                return false
            }
            val tag = document.substring(rowStart, tagEnd + 1)
            val value = attributeValue(tag, PASSWORD_ATTRIBUTE)
            if (value != null && !isCleanValue(tag, value)) {
                return false
            }
            index = tagEnd + 1
        }
    }

    private fun isCleanValue(tag: String, value: String): Boolean {
        if (value.isEmpty() || RustDeskPortableSeal.isPortable(value)) {
            return true
        }
        // A clear password on a row that is not a RustDesk access belongs to another protocol and
        // is none of this class's business.
        return attributeValue(tag, CONNECTION_TYPE_ATTRIBUTE) != RUSTDESK_CONNECTION_TYPE
    }
}
