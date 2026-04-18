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
            val title = el.text().trim()
                .replace(Regex("^world trending video[^:]*:\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\(\\s*\\d+\\s*hours? ago\\s*\\)", RegexOption.IGNORE_CASE), "")
                .trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("div.thumb img, .thumb img")
                ?.attr("src")?.trim()
                ?.let { if (it.startsWith("http")) it else null }
                ?: el.selectFirst("img[src*='blogger.googleusercontent']")?.attr("src")
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
            val poster = el.selectFirst("div.thumb img, .thumb img")
                ?.attr("src")?.trim()
                ?.let { if (it.startsWith("http")) it else null }
            newMovieSearchResponse(title, href, TvType.Others) { posterUrl = poster }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        val rawTitle = doc.selectFirst("title")?.text()?.trim() ?: ""
        val title = rawTitle
            .replace("Download", "")
            .replace(":: Best Download Wap Portal", "")
            .replace(".mp4", "").replace(".MP4", "")
            .trim()
            .ifBlank { url.substringAfterLast("/").replace(".html","").replace("-"," ") }
        val poster = doc.selectFirst("div.thumb img, .thumb img")
            ?.attr("src")?.trim()
            ?.let { if (it.startsWith("http")) it else null }
            ?: doc.selectFirst("img[src*='blogger.googleusercontent']")?.attr("src")
        return newMovieLoadResponse(title, url, TvType.Others, url) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        val doc = app.get(data, headers = ua).document
        val rawSrc = doc.selectFirst("source[src*='filedownload']")?.attr("src")?.trim()
            ?: doc.selectFirst("a[href*='filedownload']")?.attr("href")?.trim()
            ?: return false
        val fileUrl = when {
            rawSrc.startsWith("http") -> rawSrc
            rawSrc.startsWith("//") -> "https:$rawSrc"
            rawSrc.startsWith("/") -> "$mainUrl$rawSrc"
            else -> return false
        }
        callback(newExtractorLink(name, name, fileUrl, ExtractorLinkType.VIDEO) {
    this.quality = Qualities.Unknown.value
    this.referer = data
    this.headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer" to data
    )
})
        return true
    }
}
