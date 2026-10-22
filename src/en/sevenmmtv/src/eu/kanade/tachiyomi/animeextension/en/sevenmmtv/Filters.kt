package eu.kanade.tachiyomi.animeextension.en.sevenmmtv

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/** label, list-page path segment, value of the site's search_type field */
class Section(val label: String, val listPath: String?, val searchType: String)

val SECTIONS = listOf(
    Section("Censored", "censored_list", "censored"),
    Section("Uncensored", "uncensored_list", "uncensored"),
    Section("Amateur", "amateurjav_list", "amateurjav"),
    Section("Reducing Mosaic", "reducing-mosaic_list", "reducing-mosaic"),
    Section("Chinese AV", "amateur_list", "amateur"),
    Section("All sections (search only)", null, "searchall"),
)

class SectionFilter : AnimeFilter.Select<String>("Section", SECTIONS.map { it.label }.toTypedArray()) {
    val selected: Section get() = SECTIONS[state]
}

fun buildFilters() = AnimeFilterList(
    AnimeFilter.Header("Without a search term this browses the chosen section."),
    SectionFilter(),
)
