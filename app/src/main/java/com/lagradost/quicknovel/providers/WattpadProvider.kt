package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

class WattpadProvider : MainAPI() {
    override val mainUrl = "https://www.wattpad.com"
    override val name = "Wattpad"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_wattpad
    override val iconBackgroundId = R.color.wattpadColor


    private val langHeaders = mapOf(
        "" to "en-US,en;q=0.9", // English default
        "5" to "es-ES,es;q=0.9",
        "14" to "pl-PL,pl;q=0.9",
        "16" to "ar-EG,ar;q=0.9",
        "24" to "cs-CZ,cs;q=0.9",
        "4" to "de-DE,de;q=0.9",
        "18" to "tl-PH,tl;q=0.9",
        "2" to "fr-FR,fr;q=0.9",
        "21" to "hi-IN,hi;q=0.9",
        "20" to "id-ID,id;q=0.9",
        "3" to "it-IT,it;q=0.9",
        "17" to "he-IL,he;q=0.9",
        "22" to "ms-MY,ms;q=0.9",
        "13" to "nl-NL,nl;q=0.9",
        "6" to "pt-PT,pt;q=0.9",
        "15" to "ro-RO,ro;q=0.9",
        "7" to "ru-RU,ru;q=0.9",
        "23" to "tr-TR,tr;q=0.9",
        "46" to "uk-UA,uk;q=0.9",
        "19" to "vi-VN,vi;q=0.9"
    )

    override val mainCategories = listOf(
        "English" to "",
        "Español" to "5",
        "Polski" to "14",
        "العربية" to "16",
        "Česky" to "24",
        "Deutsch" to "4",
        "Filipino" to "18",
        "Français" to "2",
        "हिन्दी" to "21",
        "Bahasa Indonesia" to "20",
        "Italiano" to "3",
        "עברית" to "17",
        "Bahasa Melayu" to "22",
        "Nederlands" to "13",
        "Português" to "6",
        "Română" to "15",
        "Русский" to "7",
        "Türkçe" to "23",
        "Українська" to "46",
        "Tiếng Việt" to "19"
    )

    private var currentLang = "en-US,en;q=0.9"

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {

        val languageId = mainCategory ?: ""
        currentLang = langHeaders[languageId] ?: "en-US,en;q=0.9"
        val nextUrl =
            "https://api.wattpad.com/v5/hotlist?tags=romance&${if(mainCategory.isNullOrBlank()) "" else "language=$mainCategory&"}offset=${(page * 20) - 20}&limit=20"

        val document = app.get(nextUrl).parsed<Root>()

        val results = document.stories?.map {
            newSearchResponse(it.title, it.url) {
                posterUrl = it.cover
            }
        } ?: emptyList()

        return HeadMainPageResponse(nextUrl, results)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/$query"

        val document = app.get(
            url,
            headers = mapOf("accept-language" to currentLang)
        ).document

        return document.select(".story-card")
            .mapNotNull { element ->
                val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null
                val img = fixUrlNull(
                    element.selectFirst(".story-card-data > .cover > img")?.attr("src")
                )
                val info = element.selectFirst(".story-card-data > .story-info")
                    ?: return@mapNotNull null

                val title = info.selectFirst(".sr-only")?.text()
                    ?: return@mapNotNull null

                SearchResponse(
                    name = title,
                    url = href,
                    posterUrl = img,
                    apiName = name
                )
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val script_regex = Regex("<script>.*?window._+remix.*?<\\/script>")
        val script_text = script_regex.find(response.text)!!.value
        val json_text = Regex("\\{.*\\}").find(script_text)!!.value
        val wattpad_data = JSONObject(json_text)
        val state = wattpad_data.getJSONObject("state").getJSONObject("loaderData")
        val route = state.getJSONObject(state.names()!!.getString(state.length() - 1))
        val story = route.getJSONObject("story")
        val title = story.getString("title");
        val parts = story.getJSONArray("parts")
        val toc = mutableListOf<ChapterData>()
        for (i in 0 until parts.length()) {
            val chap = parts.getJSONObject(i)
            val href = chap.getString("url")
            val name = chap.getString("title")
            val date = chap.getString("formattedCreateDate")
            toc.add(
                newChapterData(
                    name = name,
                    url = href
                ) { dateOfRelease = date })
        }
        val writer = story.getJSONObject("author").getString("username")
        val cover = story.getString("cover")
        val votes = story.getInt("voteCount")
        val viewCount = story.getInt("readCount")
        val desc = story.getString("description")
        val labels = mutableListOf<String>()
        val tag_array = story.getJSONArray("tags")
        for (i in 0 until tag_array.length()) {
            labels.add(tag_array.getString(i))
        }
        return newStreamResponse(name = title, url = url, data = toc) {
            author = writer
            posterUrl = cover
            peopleVoted = votes
            views = viewCount
            synopsis = desc
            tags = labels
        }
    }

    override suspend fun loadHtml(url: String): String {
        val resp = app.get(url)
        val script_regex = Regex("window\\.prefetched\\s*?=\\s*?\\{.*\\}")
        val script_text = script_regex.find(resp.text)!!.value
        val json_text = Regex("\\{.*\\}").find(script_text)!!.value
        val part_data = JSONObject(json_text)
        val key = part_data.names()!!.getString(0)
        val data = part_data.getJSONObject(key).getJSONObject("data")
        val unescaped =
            Parser.unescapeEntities(data.getString("storyText"), true)
        return Jsoup.parse(unescaped)
            .html()
    }

    // ================== DATA CLASSES ==================

    data class Root(
        @JsonProperty("stories")
        val stories: List<Story>?,
        @JsonProperty("total")
        val total: Int,
        @JsonProperty("nextUrl")
        val nextUrl: String?,
    )

    data class Story(
        @JsonProperty("cover")
        val cover: String?,
        @JsonProperty("title")
        val title: String,
        @JsonProperty("url")
        val url: String
    )
}