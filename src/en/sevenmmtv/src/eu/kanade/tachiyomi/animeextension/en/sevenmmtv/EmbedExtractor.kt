package eu.kanade.tachiyomi.animeextension.en.sevenmmtv

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Opens an embed page, unpacks its scripts and collects HLS/MP4 URLs.
 * VH (vidhide-style) embeds are verified: the page contains  var links={"hls2":"...m3u8?...","hls3":"...txt"}.
 * SW and SP use the same generic search but their pages have NOT been inspected yet.
 */
class EmbedExtractor(
    private val client: OkHttpClient,
    private val baseHeaders: Headers,
    private val siteUrl: String,
) {
    private val hlsMapRegex = Regex(""""(hls\d+)"\s*:\s*"(https?:[^"]+)"""")
    private val fileRegex = Regex("""file\s*:\s*"(https?:[^"]+?\.(?:m3u8|mp4)[^"]*)"""")
    private val bareRegex = Regex("""["'](https?:[^"'\s\\]+\.m3u8[^"'\s]*)["']""")

    fun videosFrom(label: String, embedUrl: String): List<Video> {
        val requestHeaders = baseHeaders.newBuilder().set("Referer", "$siteUrl/").build()
        val html = client.newCall(GET(embedUrl, requestHeaders)).execute().use { it.body.string() }

        val texts = PackedJs.unpackAll(html) + html
        val found = LinkedHashMap<String, String>() // url -> tag
        for (text in texts) {
            for (m in hlsMapRegex.findAll(text)) found.putIfAbsent(clean(m.groupValues[2]), m.groupValues[1])
            for (m in fileRegex.findAll(text)) found.putIfAbsent(clean(m.groupValues[1]), "file")
            for (m in bareRegex.findAll(text)) found.putIfAbsent(clean(m.groupValues[1]), "hls")
        }
        if (found.isEmpty()) return emptyList()

        val embedHost = embedUrl.toHttpUrl()
        val origin = "${embedHost.scheme}://${embedHost.host}"
        val playHeaders = baseHeaders.newBuilder()
            .set("Referer", "$origin/")
            .set("Origin", origin)
            .build()

        // Prefer standard .m3u8 playlists first, then the others.
        return found.entries
            .sortedBy { if (it.key.substringBefore('?').endsWith(".m3u8")) 0 else 1 }
            .map { (url, tag) ->
                Video(videoUrl = url, videoTitle = "$label $tag", headers = playHeaders)
            }
    }

    private fun clean(url: String) = url.replace("\\/", "/").replace("&amp;", "&")
}
