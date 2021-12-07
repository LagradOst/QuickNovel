package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import khttp.get
import org.jsoup.Jsoup
import java.lang.StringBuilder
import java.net.URI
import kotlin.math.roundToInt

class ReadAnyBookProvider : MainAPI() {

    override val name: String
        get() = "ReadAnyBook"
    override val mainUrl: String
        get() = "https://www.readanybook.com"

    private val containerUrl = "META-INF/container.xml"

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val res = get(url).text
        val doc = Jsoup.parse(res)

        val books = doc.select("div.book-preview")
        return books.map {
            val main = it.select("div.preview-name > a")
            val bookName = main.text()
            val bookUrl = main.attr("href")
            val posterUrl = fixUrl(it.select("img").attr("data-src"))

            SearchResponse(
                bookName,
                bookUrl,
                posterUrl,
                null,
                null,
                this.name
            )
        }
    }

    override fun load(url: String): LoadResponse {
        val res = get(url).text
        val doc = Jsoup.parse(res)

        val name = doc.select("div.book-name").text()
        val data = doc.select("div.links-row").attr("data-link") + containerUrl

        val authors = doc.select("div.details-row span.authors-list span")
            .joinToString(separator = ", ") { it.text() }

        val posterUrl = fixUrl(doc.select("a.gallery-item").attr("href"))

        val rating =
            doc.select("input[name=all-rating]").attr("value")?.toDoubleOrNull()?.times(100)
                ?.roundToInt()
        val ratingCount = doc.select("span[itemprop=ratingCount]").text()?.toIntOrNull()

        val synopsis =
            doc.select("span.visible-text").text() + doc.select("span.toggle-text").text()

        val tags = doc.select("span.categories-list.details-info").map {
            it.text()
        }

        val chapterData = ChapterData(
            name,
            data,
            null,
            null
        )

        return LoadResponse(
            url,
            name,
            listOf(chapterData),
            authors,
            posterUrl,
            rating,
            ratingCount,
            null,
            synopsis,
            tags,
            null
        )
    }


    private fun scrapeOPF(url: String): String? {
        val html = get(url).text
        // Cuts off everything after the last "/"
        // Used for relative paths
        val mainPath = url.substring(0, url.lastIndexOf("/") + 1)

        val totalHtml = StringBuilder()

        val doc = Jsoup.parse(html)
        val spine = doc.select("spine > itemref")
        val manifest = doc.select("manifest > item")

        // Uses spine order
        spine.forEach {
            val id = it.attr("idref")
            val found = manifest.firstOrNull { it.attr("id") == id } ?: return@forEach

            // Doesn't parse images
            if (found.attr("media-type").contains("html")) {
                val href = found.attr("href")
                if (href.isNullOrBlank()) return@forEach

                val pageUrl = if (href.startsWith("http") || href.startsWith("www.")) {
                    href
                } else mainPath + href

                val subHtml = get(pageUrl).text

                totalHtml.append(subHtml)
            }
        }

        var string = totalHtml.toString()

        val whitelisted = listOf(
            "image/",
            "text/css",
            "application/x-dtbncx+xml",
            "application/vnd.adobe-page-template+xml",
            "font/otf",
            "application/vnd.ms-opentype"
        )
        // Adds images
        manifest.forEach { element ->
            if (whitelisted.any { element.attr("media-type").contains(it, ignoreCase = true) }) {
                val href = element.attr("href")
                if (href.isNullOrBlank() || href.startsWith("http") || href.startsWith("www.")) return@forEach
//                println("REPLACE $href $mainPath")
                string = string.replace(href, mainPath + href)

                // Semi shitty solution
                // images/image.jpg -> https://files.readanybook.com/992751/epub/need-to-know.epub/OEBPS/images/image.jpg
                if (href.startsWith("OEBPS/")) {
                    // " at start ensures the above replace doesn't get replaced again
                    // src="images/image.jpg"
                    string =
                        string.replace("\"" + href.removePrefix("OEBPS/"), "\"" + mainPath + href)
                }
            }
        }

        return string //.textClean.also { println(it?.substring(0, 1000)) }
    }

    override fun loadHtml(url: String): String? {
        val container = get(url).text
        val doc = Jsoup.parse(container)
        val root = doc.select("rootfile[full-path]")

        val rootPath = root.attr("full-path")
//        val type = root.attr("media-type")

        val mainUrl = url.removeSuffix(containerUrl)

        // Haven't seen other formats.
//        if (type.contains("oebps")) {
        return scrapeOPF(mainUrl + rootPath)
//        }

//        return super.loadHtml(url)
    }
}