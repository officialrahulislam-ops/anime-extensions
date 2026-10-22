package eu.kanade.tachiyomi.animeextension.en.sevenmmtv

/**
 * Minimal unpacker for "Dean Edwards" packed scripts: eval(function(p,a,c,k,e,d){...}('payload',radix,count,'dict'.split('|')...
 * Written here so the extension does not depend on any other module.
 */
object PackedJs {
    private val packedRegex = Regex(
        """\}\('((?:[^'\\]|\\.)*)',\s*(\d+),\s*(\d+),\s*'((?:[^'\\]|\\.)*)'\.split\('\|'\)""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val wordRegex = Regex("""\b\w+\b""")

    /** Returns the unpacked text of every packed script found in [html]. */
    fun unpackAll(html: String): List<String> = packedRegex.findAll(html).mapNotNull { m ->
        runCatching {
            val payload = unescape(m.groupValues[1])
            val radix = m.groupValues[2].toInt()
            val dict = unescape(m.groupValues[4]).split("|")
            wordRegex.replace(payload) { word ->
                val index = decode(word.value, radix)
                val replacement = if (index in dict.indices) dict[index] else ""
                replacement.ifEmpty { word.value }
            }
        }.getOrNull()
    }.toList()

    private fun unescape(s: String) = s.replace("\\'", "'").replace("\\\\", "\\")

    private fun decode(token: String, radix: Int): Int {
        var n = 0
        for (ch in token) {
            val v = when (ch) {
                in '0'..'9' -> ch - '0'
                in 'a'..'z' -> ch - 'a' + 10
                in 'A'..'Z' -> ch - 'A' + 36
                else -> return -1
            }
            if (v >= radix) return -1
            n = n * radix + v
        }
        return n
    }
}
