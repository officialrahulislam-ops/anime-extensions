package eu.kanade.tachiyomi.animeextension.en.sevenmmtv

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reads the server buttons (SP / SW / VH) of a 7MMTV detail page and decodes the embed URLs.
 *
 * Verified against the saved AKDL-372 page: the page's own script decodes each id by
 * splitting on a letter, reading base-N numbers, XOR-ing with a constant, base64-decoding,
 * then AES-128-CBC decrypting. The constants are read from the page, never hard-coded.
 */
object SiteDecoder {
    class Server(val label: String, val embedUrl: String)

    private val entryRegex = Regex(
        """mvarr\['(\d+_\d+)'\]=\[\['([^']*)','([^']*)','([^']*)','([^']*)','([^']*)'""",
    )
    private val buttonRegex = Regex("""function\s+jfun_show_([A-Za-z0-9]+)\(\)""")
    private val keyUseRegex = Regex("""jfun_show_mvinnerHTML\(\\*'(\d+_\d+)\\*'\)""")

    private fun intVar(html: String, name: String) =
        Regex("""\b$name\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull()

    private fun strVar(html: String, name: String) =
        Regex("""\b$name\s*=\s*'([^']*)'""").find(html)?.groupValues?.get(1)

    fun parseServers(html: String): List<Server> {
        var radix = intVar(html, "hcdeedg252") ?: return emptyList()
        val xorKey = intVar(html, "hadeedg252") ?: return emptyList()
        val key = strVar(html, "argdeqweqweqwe") ?: return emptyList()
        val iv = strVar(html, "hdddedg252") ?: return emptyList()
        if (radix > 25) radix %= 25
        val separator = (radix + 97).toChar()

        // mvarr entries: key -> embed URL
        val embeds = HashMap<String, String>()
        for (m in entryRegex.findAll(html)) {
            val (entryKey, _, encoded, _, base, suffix) = m.destructured
            val url = runCatching {
                val b64 = encoded.split(separator)
                    .joinToString("") { (it.toInt(radix) xor xorKey).toChar().toString() }
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key.toByteArray(), "AES"),
                    IvParameterSpec(iv.toByteArray()),
                )
                val plain = String(cipher.doFinal(Base64.decode(b64, Base64.DEFAULT)), Charsets.UTF_8)
                val fullBase = if (base.startsWith("//")) "https:$base" else base
                fullBase + plain + suffix
            }.getOrNull()
            if (url != null) embeds[entryKey] = url
        }

        // button label -> entry key (first CD only)
        val buttons = buttonRegex.findAll(html).toList()
        val servers = mutableListOf<Server>()
        for ((i, b) in buttons.withIndex()) {
            val end = buttons.getOrNull(i + 1)?.range?.first ?: minOf(html.length, b.range.last + 2500)
            val body = html.substring(b.range.last, end)
            val entryKey = keyUseRegex.find(body)?.groupValues?.get(1) ?: continue
            val url = embeds[entryKey] ?: continue
            servers += Server(b.groupValues[1], url)
        }
        return servers
    }
}
