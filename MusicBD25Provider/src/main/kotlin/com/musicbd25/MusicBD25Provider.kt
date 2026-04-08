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
            val rawHref = el.attr("href").trim().ifBlank { return@mapNotNull null }
            val href = if (rawHref.startsWith("http")) rawHref else "$mainUrl$rawHref"
            val title = el.text().trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("div.thumb img, .thumb img, img")?.let {
                it.attr("src").ifBlank { it.attr("data-src") }
            }?.let { if (it.startsWith("http")) it else null }
            newMovieSearchResponse(title, href, TvType.Others) { posterUrl = poster }
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val doc = app.get("$mainUrl/site-1.html?to-search=$encoded", headers = ua).document
        return doc.select("a[href*='/page-download/']").mapNotNull { el ->
            val rawHref = el.attr("href").trim().ifBlank { return@mapNotNull null }
            val href = if (rawHref.startsWith("http")) rawHref else "$mainUrl$rawHref"
            val title = el.text().trim().ifBlank { return@mapNotNull null }
            newMovieSearchResponse(title, href, TvType.Others)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        val title = doc.selectFirst("h1, h2")?.text()?.trim()
            ?: url.substringAfterLast("/").replace(".html","").replace("-"," ").trim()
        val poster = doc.selectFirst("div.thumb img, .thumb img")
            ?.attr("src")?.trim()
            ?.let { if (it.startsWith("http")) it else null }
            ?: doc.selectFirst("img[src*='blogger.googleusercontent']")?.attr("src")
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")

        // filedownload URL বের করো — এটাই direct MP4
        val rawSrc = doc.selectFirst("source[src*='filedownload']")
            ?.attr("src")?.trim()
            ?: doc.selectFirst("a[href*='filedownload']")
            ?.attr("href")?.trim()
            ?: ""

        val videoUrl = when {
            rawSrc.startsWith("http") -> rawSrc
            rawSrc.startsWith("//")   -> "https:$rawSrc"
            rawSrc.startsWith("/")    -> "$mainUrl$rawSrc"
            else -> ""
        }

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
        callback(newExtractorLink(name, name, data, ExtractorLinkType.VIDEO) {
            this.quality = Qualities.Unknown.value
            this.headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Referer" to mainUrl,
                "Origin" to mainUrl
            )
        })
        return true
    }
}
