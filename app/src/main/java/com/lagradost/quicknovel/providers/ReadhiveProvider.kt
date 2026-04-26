package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.MainActivity.Companion.app

class ReadhiveProvider  :  MainAPI() {
    override val name = "ReadHive"
    override val mainUrl = "https://readhive.org"
    override val iconId = R.drawable.icon_readhive
    override val hasMainPage = true

    override val tags = listOf(
        "All" to "",
        "Abandoned Children" to "abandoned-children",
        "Absent Parents" to "absent-parents",
        "abusement" to "abusement",
        "Abusive Characters" to "abusive-characters",
        "Academy" to "academy",
        "Accelerated Growth" to "accelerated-growth",
        "Acting" to "acting",
        "actor" to "actor",
        "Adapted to Manhwa" to "adapted-to-manhwa",
        "Adopted Children" to "adopted-children",
        "Adopted Protagonist" to "adopted-protagonist",
        "Adultery" to "adultery",
        "Adventure" to "adventure",
        "Age Progression" to "age-progression",
        "Age Regression" to "age-regression",
        "Amnesia" to "amnesia",
        "and Peasants" to "and-peasants",
        "Animal Characteristics" to "animal-characteristics",
        "Appearance Changes" to "appearance-changes",
        "Appearance Different from Actual Age" to "appearance-different-from-actual-age",
        "archduke" to "archduke",
        "Aristocracy" to "aristocracy",
        "Aristocrats" to "aristocrats",
        "Army Building" to "army-building",
        "Arranged Marriage" to "arranged-marriage",
        "Arrogant Characters" to "arrogant-characters",
        "Arrogant ML" to "arrogant-ml",
        "Artifacts" to "artifacts",
        "Artificial Intelligence" to "artificial-intelligence",
        "Artists" to "artists",
        "Assassins" to "assassins",
        "baby" to "baby",
        "Badass FL" to "badass-fl",
        "Badass ML" to "badass-ml",
        "beast" to "beast",
        "Beasts" to "beasts",
        "Beautiful Female Lead" to "beautiful-female-lead",
        "Betrayal" to "betrayal",
        "Bickering Couple" to "bickering-couple",
        "bilateral salvation" to "bilateral-salvation",
        "BL" to "bl",
        "BL. Yaoi" to "bl-yaoi",
        "blood" to "blood",
        "Bloodlines" to "bloodlines",
        "blue-haired-fl" to "blue-haired-fl",
        "Book Possessed" to "book-possessed",
        "boss" to "boss",
        "Boss-subordinate" to "boss-subordinate",
        "Brainwashing" to "brainwashing"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val url = "$mainUrl/${if(!tag.isNullOrBlank()) "genre/$tag/" else ""}page/$page/"
        val document = app.get(url).document

        val returnValue = document.select("a.peer").mapNotNull { card ->
            val href = card?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(card.selectFirst("img")?.attr("src"))
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document//body > div.body > div > div > div.col-lg-8
        val infoDiv = document.selectFirst("main")?: return newStreamResponse(url, url, emptyList())

        val title = infoDiv.selectFirst("h1")?.text() ?: throw Exception("Title not found")

        val chapters = document.select(
            "section.relative.grid.grid-cols-1.lg\\:grid-areas-series__body.lg\\:grid-cols-series.gap-x-4.px-4.py-2.sm\\:px-8 > div.lg\\:grid-in-content.mt-4 > div:nth-child(1) > div:nth-child(3) > div > div > a"
        ).mapNotNull { li ->
            if(li.selectFirst("> span") != null) return@mapNotNull null
            val name = li.selectFirst("div > div > span")?.text()?:return@mapNotNull null
            val url = li.attr("href")?:return@mapNotNull null
            newChapterData(name, url)
        }.reversed()

        return newStreamResponse(title,fixUrl(url), chapters) {
            this.posterUrl = fixUrlNull(infoDiv.selectFirst("img.object-cover")?.attr("src"))
            this.synopsis = document.select(
                "section.relative.grid.grid-cols-1.lg\\:grid-areas-series__body.lg\\:grid-cols-series.gap-x-4.px-4.py-2.sm\\:px-8 div.mb-4 > p"
            ).joinToString("\n") { p -> p.text() }

            this.author = infoDiv.selectFirst("span.leading-7")?.text() ?: ""

            this.tags = infoDiv.select("section.relative.grid.grid-cols-1.lg\\:grid-areas-series__body.lg\\:grid-cols-series.gap-x-4.px-4.py-2.sm\\:px-8 > div.lg\\:grid-in-content.mt-4 > div:nth-child(1) > div:nth-child(2) > div.flex.flex-wrap > a").mapNotNull {
                it.text().trim().takeIf { text ->  !text.isEmpty() }
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val reader = document.selectFirst("main > div.justify-center.flex-grow.mx-auto.prose.md\\:max-w-4xl.lg\\:relative") ?: return null
        return reader.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "$mainUrl/ajax", data = mapOf(
                "search" to query,
                "orderBy" to "recent",
                "post" to "e5100ffd19",
                "action" to "fetch_browse",
            )
        ).parsed<Root>()
        return document.data.posts.map { card ->
            val href = card.permalink.replace("\\","")
            val title = card.title

            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(card.thumbnail.replace("\\",""))
            }

        }
    }

    data class Root(
        @JsonProperty("success")
        val success: Boolean,
        @JsonProperty("data")
        val data: Data,
    )

    data class Data(
        @JsonProperty("posts")
        val posts: List<Post>,
    )

    data class Post(
        @JsonProperty("title")
        val title: String,
        @JsonProperty("thumbnail")
        val thumbnail: String,
        @JsonProperty("permalink")
        val permalink: String,
    )
}