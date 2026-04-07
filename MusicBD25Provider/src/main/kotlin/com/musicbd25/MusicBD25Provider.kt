package com.musicbd25

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

class MusicBD25Provider : MainAPI() {

    override var mainUrl = "https://musicbd25.site"
    override var name = "MusicBD25"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasSearch = true

    override val supportedTypes = setOf(
        TvType.Others
    )

    private val ua = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    // সব page এর ভিডিও লোড হবে এই URL pattern থেকে
    override val mainPage = mainPageOf(
        "$mainUrl/site-0.html?to-page=" to "সকল ভিডিও (Latest)",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // URL: site-0.html?to-page=1, site-0.html?to-page=2 ...
        val url = request.data + page
        val doc = app.get(url, headers = ua).document
        val items = parseItems(doc)
        // পরের পেজ আছে কিনা চেক
        val hasNext = items.isNotEmpty()
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun parseItems(doc: Document): List<SearchResponse> {
        // listing page এ যেসব link আছে /page-download/ pattern এ
        return doc.select("a[href*='/page-download/']").mapNotNull { el ->
            val href = el.attr("abs:href").ifBlank { return@mapNotNull null }

            // title: link এর text বা title attribute
            val rawTitle = (el.text().trim().ifBlank {
                el.attr("title").trim()
            }).ifBlank { return@mapNotNull null }

            // title থেকে "world trending video 📈 :" prefix সরানো
            val title = rawTitle
                .replace(Regex("^world trending video[^:]*:\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\(\\s*\\d+\\s*hours? ago\\s*\\)", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { rawTitle }

            // thumbnail: কাছের img tag থেকে
            val poster = el.selectFirst("img")?.let {
                it.attr("src").ifBlank { it.attr("data-src") }
            } ?: el.parent()?.selectFirst("img")?.let {
                it.attr("src").ifBlank { it.attr("data-src") }
            }

            newMovieSearchResponse(title, href, TvType.Others) {
                posterUrl = poster
            }
        }.distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // সাইটের search URL
        val doc = app.get("$mainUrl/?s=$encoded", headers = ua).document
        val results = parseItems(doc)
        // যদি কিছু না পাওয়া যায়, অন্য search pattern try করো
        if (results.isEmpty()) {
            val doc2 = app.get("$mainUrl/search/$encoded/", headers = ua).document
            return parseItems(doc2)
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document

        // Title
        val title = doc.selectFirst("h1, h2, .post-title, title")
            ?.text()?.trim() ?: doc.title().trim()

        // Thumbnail: .thumb img অথবা blogger CDN image
        val poster = doc.selectFirst(".thumb img")?.let {
            it.attr("src").ifBlank { it.attr("data-src") }
        } ?: doc.selectFirst("img[src*='blogger.googleusercontent']")?.attr("src")
        ?: doc.selectFirst("meta[property=og:image]")?.attr("content")

        // Description / File Info
        val plot = doc.selectFirst(".file-info, #file-info, .description, .post-content p")
            ?.text()?.trim()

        return newMovieLoadResponse(title, url, TvType.Others, url) {
            this.posterUrl = poster
            this.plot = plot
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

        // Step 1: filedownload link খোঁজো
        // pattern: href="//something/filedownload/..."
        val downloadHref = doc.select("a[href*='filedownload']")
            .firstOrNull()?.attr("href")
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?: return false

        // Step 2: redirect follow করে final URL বের করো
        val response = app.get(
            downloadHref,
            headers = ua + mapOf("Referer" to mainUrl),
            allowRedirects = false
        )

        val finalUrl = response.headers["location"]
            ?.trim()
            ?.ifBlank { null }
            ?: downloadHref

        if (!finalUrl.startsWith("http")) return false

        // Quality detect করো filename থেকে
        val quality = when {
            finalUrl.contains("1080") -> Qualities.P1080.value
            finalUrl.contains("720")  -> Qualities.P720.value
            finalUrl.contains("480")  -> Qualities.P480.value
            else                      -> Qualities.Unknown.value
        }

        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = finalUrl,
                ExtractorLinkType.VIDEO
            ) {
                this.quality = quality
                this.headers = ua + mapOf("Referer" to mainUrl)
            }
        )
        return true
    }
}
