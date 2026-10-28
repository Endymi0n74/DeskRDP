package com.dualdesk.rustdesk

/**
 * Removes the RustDesk passwords from the settings document before it is written to a file.
 *
 * Why remove them at all: a stored password is sealed under a key of this device's Android
 * Keystore (see [RustDeskSecretCodec]), and that key never leaves the device. Exporting the seal
 * would only carry a value that no other installation can open — and that the same device cannot
 * open either after a reinstall, since uninstalling the application destroys its keystore keys.
 * The honest option is therefore to leave the password where it lives and say so, instead of
 * putting an unreadable blob in a file meant to move between devices.
 *
 * The document produced by `SqliteElement.exportDbAsXmlToStream` is a list of
 * `<row ATTRIBUTE="value" ...>` elements, one per database record: the columns are attributes, and
 * their values are escaped, so a raw `<` or `"` never appears inside one. That is what makes a
 * textual pass safe here — no XML parser is needed to find an attribute value's end, and no row
 * can be confused with another. `RustDeskSecretCodec` values are recognisable by their `ks1:`
 * prefix; a RustDesk access saved before sealing existed (or on API 21/22, where no keystore key
 * can be generated) carries a clear password instead, which is why the row's connection type is
 * checked as well.
 *
 * The result is verified before it is returned: if a sealed value or a non-empty RustDesk password
 * survived the pass, the document is refused (null) rather than written. A file that silently kept
 * a secret would be worse than no export.
 */
object RustDeskExportSanitizer {

    private const val ROW_PREFIX = "<row"
    private const val PASSWORD_ATTRIBUTE = "PASSWORD"
    private const val CONNECTION_TYPE_ATTRIBUTE = "CONNECTIONTYPE"
    private const val RUSTDESK_CONNECTION_TYPE = "6"

    /** The document to write, and how many RustDesk passwords were left out of it. */
    class Sanitized(val document: String, val passwordsRemoved: Int)

    /**
     * Blanks every RustDesk password in `document`.
     *
     * Returns null when the document could not be cleaned, in which case the caller must not write
     * anything: the shape of the file changed and this pass can no longer guarantee the secrets are
     * gone.
     */
    @JvmStatic
    fun blankRustDeskPasswords(document: String): Sanitized? {
        val output = StringBuilder(document.length)
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
            val cleaned = blankInRow(tag)
            output.append(cleaned)
            if (cleaned.length != tag.length) {
                removed++
            }
            index = tagEnd + 1
        }

        val cleanedDocument = output.toString()
        if (!isClean(cleanedDocument)) {
            return null
        }
        return Sanitized(cleanedDocument, removed)
    }

    /** The row with its password attribute emptied, or the row untouched. */
    private fun blankInRow(tag: String): String {
        val holdsSealedPassword = RustDeskSecretCodec.isSealed(attributeValue(tag, PASSWORD_ATTRIBUTE))
        val isRustDesk = attributeValue(tag, CONNECTION_TYPE_ATTRIBUTE) == RUSTDESK_CONNECTION_TYPE
        if (!holdsSealedPassword && !isRustDesk) {
            return tag
        }
        return emptyAttribute(tag, PASSWORD_ATTRIBUTE)
    }

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

    /** Replaces the value of `name` with nothing, keeping the attribute in place. */
    private fun emptyAttribute(tag: String, name: String): String {
        val start = attributeStart(tag, name)
        if (start < 0) {
            return tag
        }
        val valueStart = start + name.length + 2
        val valueEnd = tag.indexOf('"', valueStart)
        if (valueEnd < 0) {
            return tag
        }
        return tag.substring(0, valueStart) + tag.substring(valueEnd)
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
     * True when no RustDesk password is left in the document.
     *
     * Two blunt checks close the ways this pass could silently do nothing: a sealed value left
     * anywhere in the document fails it, and a password attribute found while no row tag was
     * recognised fails it too — that shape is not the one this class knows how to read, so writing
     * the file would mean writing it unchecked.
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
            if (attributeStart(tag, PASSWORD_ATTRIBUTE) >= 0) {
                val value = attributeValue(tag, PASSWORD_ATTRIBUTE)
                if (value == null) {
                    return false
                }
                if (RustDeskSecretCodec.isSealed(value)) {
                    return false
                }
                val isRustDesk =
                    attributeValue(tag, CONNECTION_TYPE_ATTRIBUTE) == RUSTDESK_CONNECTION_TYPE
                if (isRustDesk && value.isNotEmpty()) {
                    return false
                }
            }
            index = tagEnd + 1
        }
    }
}
