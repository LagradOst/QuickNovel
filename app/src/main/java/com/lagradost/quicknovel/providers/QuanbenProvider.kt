package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.nodes.Document
import java.net.URLEncoder

open class QuanbenProvider : MainAPI() {
    override val name = "Quanben"
    override val mainUrl = "https://www.quanben.io"
    override val iconId = R.drawable.icon_quanben
    override val hasMainPage = true
    override val lang = "zh"
    override val tags = listOf(
        "All" to "",
        "玄幻" to "xuanhuan",
        "都市" to "dushi",
        "言情" to "yanqing",
        "穿越" to "chuanyue",
        "青春" to "qingchun",
        "仙侠" to "xianxia",
        "灵异" to "lingyi",
        "悬疑" to "xuanyi",
        "历史" to "lishi",
        "军事" to "junshi",
        "游戏" to "youxi",
        "竞技" to "jingji",
        "科幻" to "kehuan",
        "职场" to "zhichang",
        "官场" to "guanchang",
        "现言" to "xianyan",
        "耽美" to "danmei",
        "其它" to "qita"
    )

    private fun getStandardNovelPath(url: String?): String? {
        if (url == null) return null
        val absoluteUrl = fixUrl(url)
        val regex = Regex("""^https?://www\.quanben\.io(/(amp)?)?/(n/[^/]+/)$""")
        val match = regex.find(absoluteUrl)
        return match?.groupValues?.get(3)?.trim('/')
    }

    private fun parseNovels(document: Document): List<SearchResponse> {
        val novels = mutableListOf<SearchResponse>()

        // Type 1: list2 blocks
        document.select("div.list2").forEach { el ->
            val titleEl = el.selectFirst("h3 > a") ?: return@forEach
            val name = titleEl.text().trim()
            val href = titleEl.attr("href")
            val path = getStandardNovelPath(href) ?: return@forEach

            val img = el.selectFirst("img")
            val posterUrl = fixUrlNull(img?.attr("src"))

            novels.add(newSearchResponse(name, "$mainUrl/$path/") {
                this.posterUrl = posterUrl
            })
        }

        // Type 2: ul.list items
        document.select("ul.list").forEach { ul ->
            val firstLi = ul.selectFirst("li") ?: return@forEach
            val a = firstLi.selectFirst("a") ?: return@forEach
            val href = a.attr("href")
            val path = getStandardNovelPath(href) ?: return@forEach

            val name = a.text().trim().ifEmpty { firstLi.selectFirst("span.author")?.text()?.trim() ?: "Unknown" }

            if (novels.none { it.url.contains(path) }) {
                novels.add(newSearchResponse(name, "$mainUrl/$path/") {
                    this.posterUrl = null
                })
            }
        }

        return novels
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = if (tag.isNullOrBlank()) {
            mainUrl
        } else {
            if (page <= 1) "$mainUrl/c/$tag.html" else "$mainUrl/c/${tag}_$page.html"
        }

        val document = app.get(url).document
        return HeadMainPageResponse(url, parseNovels(document))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$mainUrl/index.php?c=book&a=search&keywords=$encoded"
        val document = app.get(url).document
        return parseNovels(document)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val name = document.selectFirst("meta[property=og:novel:book_name]")?.attr("content")
            ?: document.selectFirst("div.list2 h3")?.text()?.trim()
            ?: throw ErrorLoadingException("Title not found")

        val slug = Regex("""n/([^/]+)""").find(url)?.groupValues?.get(1) ?: throw ErrorLoadingException("Slug not found")
        val mirrorUrl = "https://quanben5.com/n/$slug/xiaoshuo.html"
        val chapters = mutableListOf<ChapterData>()
        runCatching {
            val mirrorDoc = app.get(mirrorUrl).document
            mirrorDoc.select("ul li a").forEachIndexed { index, el ->
                val chName = el.text().trim()
                if (chName.isNotEmpty()) {
                    val chNumber = index + 1
                    chapters.add(newChapterData(chName, "$mainUrl/n/$slug/$chNumber.html"))
                }
            }
        }

        return newStreamResponse(name, url, chapters) {
            this.posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: fixUrlNull(document.selectFirst("div.list2 img")?.attr("src"))

            this.author =  document.selectFirst("meta[property=og:novel:author]")?.attr("content")
                ?: document.selectFirst("div.list2 p:contains(作者:) span")?.text()?.trim()

            this.synopsis = document.selectFirst("meta[property=og:description]")?.attr("content")
                ?: document.selectFirst("div.description p")?.text()?.trim()
                        ?: document.selectFirst("div.description")?.text()?.trim()

            this.tags = (document.selectFirst("meta[property=og:novel:category]")
                ?.attr("content")
                ?:document.selectFirst("div.list2 p:contains(类别:) span")
                ?.text()?.trim())
                ?.split(",")?.map { it.trim() }

            setStatus(document.selectFirst("meta[property=og:novel:status]")?.attr("content"))
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val content = document.selectFirst("#contentbody, #content, .content") ?: return null
        content.select("script, style, ins, iframe, [class*=ads], [id*=ads], [class*=google], [id*=google], [class*=recommend], div[align=center]").remove()
        return content.html().trim()
    }
}
