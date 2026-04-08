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
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
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
            val title = (el.selectFirst("span")?.text() ?: el.text())
                .trim().ifBlank { return@mapNotNull null }
            newMovieSearchResponse(title, href, TvType.Others) { posterUrl = null }
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val doc = app.get("$mainUrl/site-1.html?to-search=$encoded", headers = ua).document
        return doc.select("a[href*='/page-download/']").mapNotNull { el ->
            val rawHref = el.attr("href").trim().ifBlank { return@mapNotNull null }
            val href = if (rawHref.startsWith("http")) rawHref else "$mainUrl$rawHref"
            val title = (el.selectFirst("span")?.text() ?: el.text())
                .trim().ifBlank { return@mapNotNull null }
            newMovieSearchResponse(title, href, TvType.Others) { posterUrl = null }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        val title = url.substringAfterLast("/")
            .replace(".html", "").replace("-", " ").trim()
        val poster = doc.selectFirst("div.thumb img, .thumb > img, [class=thumb] img")
            ?.attr("src")?.trim()
            ?.let { if (it.startsWith("http")) it else null }
            ?: doc.selectFirst("img[src*='googleusercontent']")?.attr("src")
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
        if (data.isBlank() || !data.startsWith("http")) return false
        val doc = app.get(data, headers = ua).document
        val rawHref = doc.select("a[href*='filedownload']")
            .firstOrNull()?.attr("href")?.trim() ?: return false
        val downloadUrl = when {
            rawHref.startsWith("http") -> rawHref
            rawHref.startsWith("//")   -> "https:$rawHref"
            rawHref.startsWith("/")    -> "$mainUrl$rawHref"
            else -> return false
        }
        val finalUrl = try {
            app.get(downloadUrl, headers = ua + mapOf("Referer" to mainUrl), allowRedirects = true).url
        } catch (e: Exception) { downloadUrl }
        if (!finalUrl.startsWith("http")) return false
        val quality = when {
            finalUrl.contains("1080") -> Qualities.P1080.value
            finalUrl.contains("720")  -> Qualities.P720.value
            finalUrl.contains("480")  -> Qualities.P480.value
            else                      -> Qualities.Unknown.value
        }
        callback(newExtractorLink(name, name, finalUrl, ExtractorLinkType.VIDEO) {
            this.quality = quality
            this.headers = ua + mapOf("Referer" to mainUrl)
        })
        return true
    }
}
