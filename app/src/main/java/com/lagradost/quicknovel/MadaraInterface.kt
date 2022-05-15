package com.lagradost.quicknovel

import android.net.Uri
import java.util.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Using khttp
fun JConnect(url: String, method: String = "GET"): Document? {
    try {
        var res = null
        if (method == "GET") {
            res = khttp.get(url)
        } else {
            res = khttp.post(url)
        }
        return if (res.statusCode.toInt() == 200) Jsoup.parse(res.text) else null
    } catch (e: Exception) {
        return null
        /*
        // maybe used for show dialog error
        when (e) {
            // - if the request URL is not a HTTP or HTTPS URL
            // or is otherwise malformed
            is MalformedURLException -> ""
            // - if the response is not OK and HTTP response
            // errors are not ignored
            is HttpStatusException -> ""
            // - if the response mime type is not supported
            // and those errors are not ignored
            is UnsupportedMimeTypeException ->
            // - if the connection times out
            is SocketTimeoutException -> ""
            // - on IO error
            is IOException -> ""
            else -> ""
        } */
    }
}

fun String.toRate(multiply: Int): Int? {
    return this
        ?.toFloatOrNull()
        ?.times(multiply)
        ?.toInt() ?: 0
}

fun String.toVote(): Int? {
    val k = this.contains("K", true)
    val nodigit = "[^.0-9]".toRegex()
    return this
        .replace(nodigit, "")
        .toFloatOrNull()
        ?.times(if (k) 1000 else 1)
        ?.toInt() ?: 0
}

fun String.clean(): String {
    val reSpace = "[ ]+".toRegex()
    val reTabN = "[\\n\\t\\r]+".toRegex()
    return this
        .replace(reTabN, "")
        .replace(reSpace, " ")
}

fun String.toUrl(): Uri? = runCatching { Uri.parse(this) }.getOrNull()
fun String.toUrlBuilder(): Uri.Builder? = toUrl()?.buildUpon()
fun String.toUrlBuilderSafe(): Uri.Builder = toUrl()?.buildUpon()!!
fun Uri.Builder.ifCase(case: Boolean, action: Uri.Builder.() -> Uri.Builder) = when {
    case -> action(this)
    else -> this
}

fun Uri.Builder.addPath(vararg path: String) =
    path.fold(this) { builder, s ->
        builder.appendPath(s)
    }

fun Uri.Builder.add(vararg query: Pair<String, Any>) =
    query.fold(this) { builder, s ->
        builder.appendQueryParameter(s.first, s.second.toString())
    }

fun Uri.Builder.add(key: String, value: Any): Uri.Builder =
    appendQueryParameter(key, value.toString())

abstract class MadaraInterface : MainAPI() {
    open override val name = ""
    open override val mainUrl = ""
    open override val iconId = R.drawable.big_icon_boxnovel
    open override val lang = "id"
    override val hasMainPage = true
    open override val iconBackgroundId = R.color.boxNovelColor
    open val novelGenre: String = "novel-genre"
    open val novelTag: String = "novel-tag"
    open val covelAttr: String = "data-src"
    open val novelPath: String = "novel"

    open override val mainCategories: List<Pair<String, String>>
        get() = listOf(
            Pair("All", ""),
            Pair("Novel Tamat", "tamat"),
            Pair("Novel Korea", "novel-korea"),
            Pair("Novel China", "novel-china"),
            Pair("Novel Jepang", "novel-jepang"),
            Pair("Novel HTL (Human Translate)", "htl"),
        )

    open override val tags: List<Pair<String, String>>
        get() = listOf(
            Pair("All", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Romance", "romance"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Shounen", "shounen"),
            Pair("School Life", "school-life"),
            Pair("Shoujo", "shoujo"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Josei", "josei"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Mystery", "mystery"),
            Pair("One shot", "one-shot"),
            Pair("Psychological", "psychological"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
        )

    open override val orderBys: List<Pair<String, String>>
        get() = listOf(
            Pair("Nothing", ""),
            Pair("New", "new-manga"),
            Pair("Most Views", "views"),
            Pair("Trending", "trending"),
            Pair("Rating", "rating"),
            Pair("A-Z", "alphabet"),
            Pair("Latest", "latest"),
        )

    open override fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val cek = setOf(null, "")
        val order: String = when {
            mainCategory !in cek -> "$novelTag/$mainCategory"
            tag !in cek -> "$novelGenre/$tag"
            else -> novelPath
        }

        val url = mainUrl.toUrlBuilderSafe()
            ?.addPath(order)
            .ifCase(page > 1) { addPath("page", page.toString()) }
            .ifCase(orderBy !in cek) { add("m_orderby", "$orderBy") }
            .toString()

        val headers = JConnect(url)?.select("div.page-item-detail")
        if (headers == null || headers.size <= 0) {
            return HeadMainPageResponse(url, listOf())
        }

        val returnValue = headers
            .mapNotNull {
                val imageHeader = it.selectFirst("div.item-thumb > a")
                val cName = imageHeader.attr("title")
                val cUrl = imageHeader.attr("href") ?: ""
                val posterUrl = imageHeader.selectFirst("> img")?.attr(covelAttr) ?: ""
                val sum = it.selectFirst("div.item-summary")
                val rating = sum.selectFirst("> div.rating > div.post-total-rating > span.score")
                    ?.text()
                    ?.toRate(200)
                val latestChap =
                    sum.selectFirst("> div.list-chapter > div.chapter-item > span > a").text()
                SearchResponse(cName, cUrl, posterUrl, rating, latestChap, this.name)
            }

        return HeadMainPageResponse(url, returnValue)
    }

    open override fun loadHtml(url: String): String? {
        val res = JConnect(url)!!.selectFirst("div.text-left")
        if (res == null || res.html() == "") {
            return null
        }
        res.select("p")?.forEach {
            if (it.selectFirst("a[href]") is Element) it.remove()
        }
        return res.html()
    }

    open override fun search(query: String): List<SearchResponse> {
        val headers = JConnect("$mainUrl/?s=$query&post_type=wp-manga")
            ?.select("div.c-tabs-item__content")
        if (headers == null || headers.size <= 0) {
            return listOf()
        }
        return headers
            ?.mapNotNull {
                // val head = it.selectFirst("> div > div.tab-summary")
                val title = it.selectFirst("div.post-title > h3 > a")
                val name = title.text()
                val url = title.attr("href")
                val posterUrl = it.selectFirst("div.tab-thumb > a > img")?.attr(covelAttr) ?: ""
                val meta = it.selectFirst("div.tab-meta")
                val rating =
                    meta.selectFirst("div.rating > div.post-total-rating > span.total_votes")
                        .text()
                        .toRate(200)
                val latestChapter = meta.selectFirst("div.latest-chap > span.chapter > a").text()
                SearchResponse(name, url, posterUrl, rating, latestChapter, this.name)
            }
    }

    open override fun load(url: String): LoadResponse {
        val document = JConnect(url)
        val name = document
            ?.selectFirst("div.post-title > h1")
            ?.text()
            ?.clean() ?: ""

        val author = document?.selectFirst("div.author-content > a")
            ?.text() ?: ""

        val posterUrl =
            document?.select("div.summary_image > a > img")?.attr(covelAttr) ?: ""

        val tags = document
            ?.select("div.genres-content > a")
            ?.mapNotNull { it.text() }

        val synopsis = document?.select("div.summary__content")
            ?.text() ?: ""
            //?.mapNotNull { it.text() }
            //?.joinToString("/n") ?: ""

        // ajax/chapters/
        val data = JConnect("${url}ajax/chapters/", "POST")
            ?.select(".wp-manga-chapter > a[href]")
            ?.mapNotNull {
                ChapterData(
                    name = it?.selectFirst("a")?.text()?.clean() ?: "",
                    url = it?.selectFirst("a")?.attr("href") ?: "",
                    dateOfRelease = it.selectFirst("span > i")?.text(),
                    views = 0
                )
            }
            ?.reversed() ?: listOf()

        val rating = document?.selectFirst("span#averagerate")
            ?.text()
            ?.toRate(200)

        val peopleVoted = document?.selectFirst("span#countrate")
            ?.text()
            ?.toVote()

        val views = null

        val status =
            document?.select("div.post-status > div.post-content_item > div.summary-content")
                ?.last()
                ?.text()
                ?.toLowerCase(Locale.getDefault())
                .let {
                    when (it) {
                        "ongoing" -> STATUS_ONGOING
                        "completed" -> STATUS_COMPLETE
                        else -> STATUS_NULL
                    }
                } ?: STATUS_NULL

        return LoadResponse(
            url,
            name,
            data,
            author,
            posterUrl,
            rating,
            peopleVoted,
            views,
            synopsis,
            tags,
            status
        )
    }
}
