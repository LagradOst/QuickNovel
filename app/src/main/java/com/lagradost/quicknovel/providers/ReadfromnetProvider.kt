package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max


class ReadfromnetProvider : MainAPI() {
    override val name = "ReadFrom.Net"
    override val mainUrl = "https://readfrom.net/"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_wuxiaworldonline

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("All", "allbooks"),
        Pair("Romance", "romance"),
        Pair("Fiction", "fiction"),
        Pair("Fantasy", "fantasy"),
        Pair("Young Adult", "young-adult"),
        Pair("Contemporary", "contemporary"),
        Pair("Mystery & Thrillers", "mystery-thrillers"),
        Pair("Science Fiction & Fantasy", "science-fiction-fantasy"),
        Pair("Paranormal", "paranormal"),
        Pair("Historical Fiction", "historical-fiction"),
        Pair("Mystery", "mystery"),
        //Pair("Editor's choice", "Editor's choice"),
        Pair("Science Fiction", "science-fiction"),
        Pair("Literature & Fiction", "literature-fiction"),
        Pair("Thriller", "thriller"),
        Pair("Horror", "horror"),
        Pair("Suspense", "suspense"),
        Pair("Nonfiction", "non-fiction"),
        Pair("Children's Books", "children-s-books"),
        Pair("Historical", "historical"),
        Pair("History", "history"),
        Pair("Crime", "crime"),
        Pair("Ebooks", "ebooks"),
        Pair("Children's", "children-s"),
        Pair("Chick Lit", "chick-lit"),
        Pair("Short Stories", "short-stories"),
        Pair("Nonfiction", "nonfiction"),
        Pair("Humor", "humor"),
        Pair("Poetry", "poetry"),
        Pair("Erotica", "erotica"),
        Pair("Humor and Comedy", "humor-and-comedy"),
        Pair("Classics", "classics"),
        Pair("Gay and Lesbian", "gay-and-lesbian"),
        Pair("Biography", "biography"),
        Pair("Childrens", "childrens"),
        Pair("Memoir", "memoir"),
        Pair("Adult Fiction", "adult-fiction"),
        Pair("Biographies & Memoirs", "biographies-memoirs"),
        Pair("New Adult", "new-adult"),
        Pair("Gay & Lesbian", "gay-lesbian"),
        Pair("Womens Fiction", "womens-fiction"),
        Pair("Science", "science"),
        Pair("Historical Romance", "historical-romance"),
        Pair("Cultural", "cultural"),
        Pair("Vampires", "vampires"),
        Pair("Urban Fantasy", "urban-fantasy"),
        Pair("Sports", "sports"),
        Pair("Religion & Spirituality", "religion-spirituality"),
        Pair("Paranormal Romance", "paranormal-romance"),
        Pair("Dystopia", "dystopia"),
        Pair("Politics", "politics"),
        Pair("Travel", "travel"),
        Pair("Christian Fiction", "christian-fiction"),
        Pair("Philosophy", "philosophy"),
        Pair("Religion", "religion"),
        Pair("Autobiography", "autobiography"),
        Pair("M M Romance", "m-m-romance"),
        Pair("Cozy Mystery", "cozy-mystery"),
        Pair("Adventure", "adventure"),
        Pair("Comics & Graphic Novels", "comics-graphic-novels"),
        Pair("Business", "business"),
        Pair("Polyamorous", "polyamorous"),
        Pair("Reverse Harem", "reverse-harem"),
        Pair("War", "war"),
        Pair("Writing", "writing"),
        Pair("Self Help", "self-help"),
        Pair("Music", "music"),
        Pair("Art", "art"),
        Pair("Language", "language"),
        Pair("Westerns", "westerns"),
        Pair("BDSM", "bdsm"),
        Pair("Middle Grade", "middle-grade"),
        Pair("Western", "western"),
        Pair("Psychology", "psychology"),
        Pair("Comics", "comics"),
        Pair("Romantic Suspense", "romantic-suspense"),
        Pair("Shapeshifters", "shapeshifters"),
        Pair("Spirituality", "spirituality"),
        Pair("Picture Books", "picture-books"),
        Pair("Holiday", "holiday"),
        Pair("Animals", "animals"),
        Pair("Anthologies", "anthologies"),
        Pair("Menage", "menage"),
        Pair("Zombies", "zombies"),
        Pair("Realistic Fiction", "realistic-fiction"),
        Pair("Reference", "reference"),
        Pair("LGBT", "lgbt"),
        Pair("Lesbian Fiction", "lesbian-fiction"),
        Pair("Food and Drink", "food-and-drink"),
        Pair("Mystery Thriller", "mystery-thriller"),
        Pair("Outdoors & Nature", "outdoors-nature"),
        Pair("Christmas", "christmas"),
        Pair("Sequential Art", "sequential-art"),
        Pair("Novels", "novels"),
        Pair("Military Fiction", "military-fiction"),
    )

    override fun loadMainPage(page: Int, mainCategory: String?, orderBy: String?, tag: String?): HeadMainPageResponse {
        val url = mainUrl+tag
        val response = khttp.get(url)

        val document = Jsoup.parse(response.text)

        val headers = document.select("div.box_in")
            if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val name = h.selectFirst("h2").text()
            val cUrl = h.selectFirst(" div > h2.title > a ").attr("href")

            val posterUrl = h.selectFirst("div > a.highslide > img").attr("src")

            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrl(posterUrl),
                    null,
                    null,
                    this.name
                )
            )
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override fun loadHtml(url: String): String? {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("#textToRead").html()
    }


    override fun search(query: String): List<SearchResponse> {
        val response =
            khttp.get("https://readfrom.net/build_in_search/?q=$query") // AJAX, MIGHT ADD QUICK SEARCH

        val document = Jsoup.parse(response.text)


        val headers = document.select("div.box_in")
        if (headers.size <= 3) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()

        for (h in headers.take(headers.size-3) ){
            val name = h.selectFirst(" div > h2.title > a > b").text()
            val cUrl = mainUrl.substringBeforeLast("/")+h.selectFirst(" div > h2.title > a ").attr("href")

            val posterUrl = h.selectFirst("div > a.highslide > img").attr("src")

            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrl(posterUrl),
                    null,
                    null,
                    this.name
                )
            )
        }
        return returnValue
    }

    override fun load(url: String): LoadResponse {
        val response = khttp.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst(" h2 ").text().substringBefore(", page").substringBefore("#")

        val author = document.selectFirst("#dle-speedbar > div > div > ul > li:nth-child(3) > a > span").text()

        val posterUrl = document.selectFirst("div.box_in > center:nth-child(1) > div > a > img").attr("src")

        val data: ArrayList<ChapterData> = ArrayList()
        val chapters = document.select("div.splitnewsnavigation2.ignore-select > center > div > a")
        data.add(ChapterData("page 1", url.substringBeforeLast("/")+"/page,1,"+url.substringAfterLast("/"), null, null))
        for (c in 0..(chapters.size/2)) {
            if (chapters[c].attr("href").contains("category").not()) {
                val cUrl = chapters[c].attr("href")
                val cName = "page "+chapters[c].text()
                data.add(ChapterData(cName, cUrl, null, null, null ))
            }
        }
        data.sortWith { first, second ->
            if (first.name.substringAfter(" ") != second.name.substringAfter(" ")) {
                first.name.substringAfter(" ").toInt() - second.name.substringAfter(" ").toInt()
            } else {
                first.name.compareTo(second.name)
            }
        }



        return LoadResponse(
            url,
            name,
            data,
            author,
            fixUrl(posterUrl),
            null,
            null,
            null,
            null,
            null,
            null,
        )
    }
}