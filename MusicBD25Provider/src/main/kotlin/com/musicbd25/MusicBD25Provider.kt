package com.musicbd25

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class MusicBD25Provider : MainAPI() {
    override var mainUrl = "https://musicbd25.site"
    override var name = "MusicBD25"
    override var lang = "bn"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Others)

    private val ua = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/site-0.html?to-page=" to "সকল ভিডিও (Latest)",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val doc = app.get(url, headers = ua).document
        val items = doc.select("a[href*='/page-download/']").mapNotNull { el ->
            val href = el.attr("abs:href").ifBlank { return@mapNotNull null }
            val rawTitle = el.text().trim().ifBlank { el.attr("title").trim() }
            val title = rawTitle
                .replace(Regex("^world trending video[^:]*:\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\(\\s*\\d+\\s*hours? ago\\s*\\)", RegexOption.IGNORE_CASE), "")
                .trim().ifBlank { return@mapNotNull null }
            newMovieSearchResponse(title, href, TvType.Others) { posterUrl = null }
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val doc = app.get("$mainUrl/?s=$encoded", headers = ua).document
        return doc.select("a[href*='/page-download/']").mapNotNull { el ->
            val href = el.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = el.text().trim().ifBlank { return@mapNotNull null }
            newMovieSearchResponse(title, href, TvType.Others)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        val title = doc.selectFirst("h1, h2, title")?.text()?.trim() ?: url.substringAfterLast("/")
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")

        // filedownload link বের করো
        val videoUrl = doc.selectFirst("source[src*='filedownload'], a[href*='filedownload']")
            ?.let {
                val src = it.attr("src").ifBlank { it.attr("href") }
                if (src.startsWith("//")) "https:$src" else src
            } ?: ""

        return newMovieLoadResponse(title, url, TvType.Others, videoUrl) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank() || !data.startsWith("http")) return false
        callback(
            newExtractorLink(name, name, data, ExtractorLinkType.VIDEO) {
                this.quality = Qualities.Unknown.value
                this.headers = ua + mapOf("Referer" to mainUrl)
            }
        )
        return true
    }
}         
