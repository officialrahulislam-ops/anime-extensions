package eu.kanade.tachiyomi.animeextension.en.sevenmmtv

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class SevenMMTV : AnimeHttpSource() {

    override val name = "7MMTV"
    override val baseUrl = "https://7mmtv.sx"
    override val lang = "en"
    override val supportsLatest = false

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    private val extractor by lazy { EmbedExtractor(client, headers, baseUrl) }
    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT) }

    // ---------- Popular (= newest of the Censored section) ----------

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/en/censored_list/all/$page.html", headers)

    override fun popularAnimeParse(response: Response): AnimesPage = parseList(response.asJsoup())

    // ---------- Latest (not supported separately) ----------

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): AnimesPage = throw UnsupportedOperationException()

    // ---------- Search / section browsing ----------

    override fun getFilterList(): AnimeFilterList = buildFilters()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val section = filters.filterIsInstance<SectionFilter>().firstOrNull()?.selected ?: SECTIONS.first()
        if (query.isBlank()) {
            val path = section.listPath ?: SECTIONS.first().listPath
            return GET("$baseUrl/en/$path/all/$page.html", headers)
        }
        val body = FormBody.Builder()
            .add("search_keyword", query)
            .add("search_type", section.searchType)
            .add("op", "search")
            .build()
        // Result-page pagination has not been inspected yet, so only page 1 of a search is loaded.
        val searchHeaders = headers.newBuilder().add(PAGE_HEADER, page.toString()).build()
        return POST("$baseUrl/en/searchform_search/all/index.html", searchHeaders, body)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val page = response.request.header(PAGE_HEADER)?.toIntOrNull() ?: 1
        if (page > 1) return AnimesPage(emptyList(), false)
        val result = parseList(response.asJsoup())
        // Search is a single page, so there is nothing more to load.
        return if (response.request.method == "POST") AnimesPage(result.animes, false) else result
    }

    // ---------- Listing parser ----------

    private fun parseList(document: Document): AnimesPage {
        val cards = document.select("div.col-item div.video")
        val animes = cards.mapNotNull { card ->
            val link = card.selectFirst("h3.video-title a") ?: card.selectFirst("figure.video-preview a")
            link?.let { toAnime(it, card.selectFirst("figure.video-preview img")) }
        }.distinctBy { it.url }.ifEmpty {
            // Fallback for pages (e.g. search results) with slightly different markup.
            document.select("a[href*=_content/]:has(img)").mapNotNull { a ->
                toAnime(a, a.selectFirst("img"))
            }.distinctBy { it.url }
        }
        return AnimesPage(animes, animes.isNotEmpty())
    }

    private fun toAnime(link: Element, img: Element?): SAnime? {
        val href = link.absUrl("href")
        if (!href.contains("_content/")) return null
        val title = link.text().ifEmpty { img?.attr("alt").orEmpty() }
        if (title.isEmpty()) return null
        return SAnime.create().apply {
            setUrlWithoutDomain(href)
            this.title = title
            thumbnail_url = img?.let { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }?.ifEmpty { null }
        }
    }

    // ---------- Details ----------

    private fun Document.attrValues(label: String): List<String> =
        select("strong").firstOrNull { it.text().trim().trimEnd(':') == label }
            ?.parent()?.nextElementSibling()?.select("a")
            ?.map { it.text() }?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun Document.metaRow(): List<String> =
        select("div.fullvideo-details span.text-muted").map { it.text() }

    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.asJsoup()
        val meta = doc.metaRow()
        val genres = doc.select(".categories a").map { it.text() }.filter { it.isNotEmpty() }
        val idols = doc.attrValues("JAV Idols")
        val label = doc.attrValues("Label").firstOrNull()
        val studio = doc.attrValues("Studio").firstOrNull()
        val director = doc.attrValues("Director").firstOrNull()
        val ldJson = doc.select("script[type=application/ld+json]").joinToString("\n") { it.data() }

        return SAnime.create().apply {
            title = doc.selectFirst("h1.fullvideo-title")!!.text()
            thumbnail_url = Regex(""""thumbnailUrl"\s*:\s*"([^"]+)"""").find(ldJson)?.groupValues?.get(1)
            genre = genres.joinToString().ifEmpty { null }
            artist = idols.joinToString().ifEmpty { null }
            author = studio
            status = SAnime.COMPLETED
            description = buildString {
                doc.selectFirst(".fullvideo-text article")?.text()?.takeIf { it.isNotEmpty() }?.let {
                    append(it)
                    append("\n\n")
                }
                meta.getOrNull(0)?.let { append("Code: $it\n") }
                meta.getOrNull(1)?.let { append("Release date: $it\n") }
                meta.getOrNull(2)?.let { append("Duration: ${formatDuration(it)}\n") }
                if (idols.isNotEmpty()) append("Performers: ${idols.joinToString()}\n")
                label?.let { append("Label: $it\n") }
                studio?.let { append("Studio: $it\n") }
                director?.let { append("Director: $it\n") }
            }.trim().ifEmpty { null }
        }
    }

    private fun formatDuration(raw: String): String {
        val minutes = Regex("""(\d+)""").find(raw)?.groupValues?.get(1)
        return if (minutes != null) "$minutes min" else raw
    }

    // ---------- Episodes (every video is a single episode) ----------

    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = response.asJsoup()
        val meta = doc.metaRow()
        return listOf(
            SEpisode.create().apply {
                setUrlWithoutDomain(response.request.url.toString())
                name = meta.getOrNull(0) ?: "Video"
                episode_number = 1f
                date_upload = meta.getOrNull(1)?.let { runCatching { dateFormat.parse(it)?.time }.getOrNull() } ?: 0L
            },
        )
    }

    // ---------- Videos ----------

    override fun videoListParse(response: Response): List<Video> {
        val html = response.body.string()
        var servers = SiteDecoder.parseServers(html)
        if (servers.isEmpty()) {
            // Fallback: the page's JSON-LD names the VH embed directly.
            Regex(""""embedUrl"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)?.let {
                servers = listOf(SiteDecoder.Server("VH", it))
            }
        }
        // VH first (verified), then the rest.
        val ordered = servers.sortedBy { if (it.label == "VH") 0 else 1 }
        val videos = mutableListOf<Video>()
        for (server in ordered) {
            try {
                videos += extractor.videosFrom(server.label, server.embedUrl)
            } catch (e: Exception) {
                Log.e("7MMTV", "Server ${server.label} failed: ${e.message}")
            }
        }
        return videos
    }

    companion object {
        private const val PAGE_HEADER = "X-7MM-Page"
    }
}
