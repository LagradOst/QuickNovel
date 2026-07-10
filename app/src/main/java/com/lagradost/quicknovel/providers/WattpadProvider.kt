package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.providers.WtrLabProvider.ReviewResponse
import com.lagradost.quicknovel.util.AppUtils.parseJson
import com.lagradost.quicknovel.util.AppUtils.toJson
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import kotlin.collections.mapNotNull

class WattpadProvider : MainAPI() {
    override val mainUrl = "https://www.wattpad.com"
    override val name = "Wattpad"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_wattpad
    override val iconBackgroundId = R.color.wattpadColor
    override val rateLimitTime = 500L
    var novelId = ""

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

        val results = document.stories?.mapNotNull {
            newSearchResponse(it.title ?: return@mapNotNull null, it.url ?: return@mapNotNull null) {
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
        val script_text = Regex("<script>.*?window._+remix.*?</script>").find(response.text)!!.value
        //idk how to parse "routes\/story.$storyid"
        val state = JSONObject(Regex("\\{.*\\}").find(script_text)!!.value)
            .getJSONObject("state")
            .getJSONObject("loaderData")
        val route = state.getString(state.names()!!.getString(state.length() - 1))

        val novel = parseJson<LoadPageResponse>(route)
        val story = novel.story
        val ch =  story.parts?.map{ chap ->
            val href = chap.url
            val name = chap.title
            val date = chap.formattedCreateDate
            newChapterData(
                name = name,
                url = href
            ) { dateOfRelease = date }
        }

        return newStreamResponse(name = novel.title, url = url, data = ch ?: emptyList()) {
            author = story.author?.username
            posterUrl = story.cover
            peopleVoted = story.voteCount
            views = story.voteCount
            synopsis = story.description
            tags = story.tags?.mapNotNull {
                it.trim().takeIf { text ->  text.isNotEmpty() }
            }
            related = getRelated(response.document)
        }
    }


    private fun getRelated(dc: Document): List<SearchResponse>{
        return dc.select("div[data-testid=similar-stories-slide-column] > a").mapNotNull { element ->
            val href = element.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("h5")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(element.selectFirst("img")?.attr("src"))
            }
        }
    }

    override suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean
    ): List<UserReview> {
        if (novelId.isEmpty()) return emptyList()

        val realUrl = "$mainUrl/api/review/get?serie_id=$novelId&page=${page - 1}&sort=most_liked"
        val res = app.get(realUrl).parsedSafe<ReviewResponse>()

        return res?.data?.mapNotNull { item ->
            val reviewTxt = item.comment ?: return@mapNotNull null
            UserReview(
                review = reviewTxt,
                username = item.username ?: "User",
                reviewDate = item.createdAt,
                rating = item.rate?.times(200)
            )
        } ?: emptyList()
    }

    override suspend fun loadHtml(url: String): String {
        val resp = app.get(url)

        val scriptRegex = Regex("window\\.prefetched\\s*=\\s*(\\{.*\\})")
        val scriptText = scriptRegex.find(resp.text)?.groupValues?.get(1)
            ?: throw Exception("Failed to get Data")

        val parsed = parseJson<Map<String, ChapterWrapper>>(scriptText)

        val data = parsed.values.first().data

        val totalPages = data.pages
        val textUrlBase = data.textUrl.text

        val fullHtml = StringBuilder()

        for (page in 1..totalPages) {
            val pageResp = app.get("$textUrlBase$page").text
            fullHtml.append(pageResp)
        }
        return fullHtml.toString()
    }

    // ================== DATA CLASSES ==================
    data class LoadPageResponse(
        val story: Story,
        val title: String
    )
    data class Part(
        val url:String,
        val title: String,
        val formattedCreateDate: String
    )
    data class Author(
        val username: String
    )

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
        val title: String?,
        @JsonProperty("url")
        val url: String?,

        val author: Author?,
        val voteCount: Int?,
        val description: String?,
        val tags: List<String>?,
        val parts: List<Part>?
    )


    data class ChapterWrapper(
        @JsonProperty("data")
        val data: WChapterData
    )

    data class WChapterData(
        @JsonProperty("pages")
        val pages: Int,

        @JsonProperty("text_url")
        val textUrl: TextUrl,

        @JsonProperty("storyText")
        val storyText: String? = null
    )

    data class TextUrl(
        @JsonProperty("text")
        val text: String
    )
}