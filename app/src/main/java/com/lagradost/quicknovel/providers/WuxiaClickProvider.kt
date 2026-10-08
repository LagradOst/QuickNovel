package com.lagradost.quicknovel.providers

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newReview
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.util.AppUtils.parseJson
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class WuxiaClickProvider : MainAPI() {
    override val name = "WuxiaClick"
    override val mainUrl = "https://wuxia.click"
    override val iconId = R.drawable.icon_wuxiaclick
    override val iconBackgroundId = R.color.wuxiacliColor
    override val hasReviews = true

    override val hasMainPage = true

    //category/
    override val mainCategories = listOf(
        "All" to "",
        "Mature" to "mature",
        "Psychological" to "psychological",
        "Tragedy" to "tragedy",
        "Mystery" to "mystery",
        "Seinen" to "seinen",
        "Harem" to "harem",
        "Mecha" to "mecha",
        "Xuanhuan" to "xuanhuan",
        "Josei" to "josei",
        "Horror" to "horror",
        "Adult" to "adult",
        "Sci-fi" to "sci-fi",
        "Action" to "action",
        "Smut" to "smut",
        "Drama" to "drama",
        "Yaoi" to "yaoi",
        "School life" to "school life",
        "Comedy" to "comedy",
        "Gender bender" to "gender bender",
        "Adventure" to "adventure",
        "Shounen" to "shounen",
        "Romance" to "romance",
        "Fantasy" to "fantasy",
        "Xianxia" to "xianxia",
        "Martial arts" to "martial arts",
        "Shounen ai" to "shounen ai",
        "Supernatural" to "supernatural",
        "Slice of life" to "slice of life",
        "Ecchi" to "ecchi",
        "Sports" to "sports",
        "Shoujo" to "shoujo",
        "Historical" to "historical",
        "Wuxia" to "wuxia",
        "Yuri" to "yuri",
        "Shoujo ai" to "shoujo ai",
        "Mtl" to "mtl"
    )

    override val orderBys = listOf(
        "Name" to "name",
        "Translated chapters" to "num_of_chaps",
        "Rating" to "rating",
        "Old" to "created_at",
        "New" to "created_at"
    )

    //tag/
    override val tags = listOf(
        "All" to "",
        "shotacon" to "shotacon",
        "handsome male lead" to "handsome male lead",
        "male protagonist" to "male protagonist",
        "beautiful female lead" to "beautiful female lead",
        "weak to strong" to "weak to strong",
        "transmigration" to "transmigration",
        "calm protagonist" to "calm protagonist",
        "clever protagonist" to "clever protagonist",
        "female protagonist" to "female protagonist",
        "love interest falls in love first" to "love interest falls in love first",
        "hard-working protagonist" to "hard-working protagonist",
        "cultivation" to "cultivation",
        "strong love interests" to "strong love interests",
        "modern day" to "modern day",
        "nobles" to "nobles",
        "schemes and conspiracies" to "schemes and conspiracies",
        "wealthy characters" to "wealthy characters",
        "cunning protagonist" to "cunning protagonist",
        "misunderstandings" to "misunderstandings",
        "royalty" to "royalty",
        "devoted love interests" to "devoted love interests",
        "multiple realms" to "multiple realms",
        "character growth" to "character growth",
        "arrogant characters" to "arrogant characters",
        "determined protagonist" to "determined protagonist",
        "reincarnation" to "reincarnation",
        "past plays a big role" to "past plays a big role",
        "romantic subplot" to "romantic subplot",
        "magic" to "magic",
        "time skip" to "time skip",
        "dragons" to "dragons",
        "adapted to manhua" to "adapted to manhua",
        "demons" to "demons",
        "aristocracy" to "aristocracy",
        "alchemy" to "alchemy",
        "hiding true identity" to "hiding true identity",
        "special abilities" to "special abilities",
        "multiple pov" to "multiple pov",
        "slow romance" to "slow romance",
        "wars" to "wars",
        "gods" to "gods",
        "ruthless protagonist" to "ruthless protagonist",
        "lucky protagonist" to "lucky protagonist",
        "monsters" to "monsters",
        "hiding true abilities" to "hiding true abilities",
        "overpowered protagonist" to "overpowered protagonist",
        "polygamy" to "polygamy",
        "game elements" to "game elements",
        "possessive characters" to "possessive characters",
        "older love interests" to "older love interests",
        "sword and magic" to "sword and magic",
        "doting love interests" to "doting love interests",
        "beast companions" to "beast companions",
        "caring protagonist" to "caring protagonist",
        "death of loved ones" to "death of loved ones",
        "loyal subordinates" to "loyal subordinates",
        "artifacts" to "artifacts",
        "shameless protagonist" to "shameless protagonist",
        "sword wielder" to "sword wielder",
        "revenge" to "revenge",
        "second chance" to "second chance",
        "fantasy world" to "fantasy world",
        "tragic past" to "tragic past",
        "cold love interests" to "cold love interests",
        "immortals" to "immortals",
        "marriage" to "marriage",
        "body tempering" to "body tempering",
        "strength-based social hierarchy" to "strength-based social hierarchy",
        "european ambience" to "european ambience",
        "adapted to manhwa" to "adapted to manhwa",
        "betrayal" to "betrayal",
        "pregnancy" to "pregnancy",
        "genius protagonist" to "genius protagonist",
        "underestimated protagonist" to "underestimated protagonist",
        "first-time interc**rse" to "first-time interc**rse",
        "hidden abilities" to "hidden abilities",
        "kingdoms" to "kingdoms",
        "confident protagonist" to "confident protagonist",
        "protagonist strong from the start" to "protagonist strong from the start",
        "bloodlines" to "bloodlines",
        "power couple" to "power couple",
        "friendship" to "friendship",
        "manipulative characters" to "manipulative characters",
        "strong to stronger" to "strong to stronger",
        "depictions of cruelty" to "depictions of cruelty",
        "charismatic protagonist" to "charismatic protagonist",
        "fast cultivation" to "fast cultivation",
        "unique cultivation technique" to "unique cultivation technique",
        "world travel" to "world travel",
        "academy" to "academy",
        "dense protagonist" to "dense protagonist",
        "elves" to "elves",
        "previous life talent" to "previous life talent",
        "poor to rich" to "poor to rich",
        "alternate world" to "alternate world",
        "cold protagonist" to "cold protagonist",
        "politics" to "politics",
        "pill concocting" to "pill concocting",
        "fast learner" to "fast learner",
        "obsessive love" to "obsessive love",
        "r*pe" to "r*pe",
        "long separations" to "long separations",
        "money grubber" to "money grubber",
        "transported to another world" to "transported to another world",
        "cheats" to "cheats",
        "cautious protagonist" to "cautious protagonist",
        "mature protagonist" to "mature protagonist",
        "familial love" to "familial love",
        "arranged marriage" to "arranged marriage",
        "magical space" to "magical space",
        "comedic undertone" to "comedic undertone",
        "mysterious past" to "mysterious past",
        "male yandere" to "male yandere",
        "mysterious family background" to "mysterious family background",
        "famous protagonist" to "famous protagonist",
        "magic formations" to "magic formations",
        "family conflict" to "family conflict",
        "system administrator" to "system administrator",
        "reincarnated in another world" to "reincarnated in another world",
        "late romance" to "late romance",
        "heavenly tribulation" to "heavenly tribulation",
        "proactive protagonist" to "proactive protagonist",
        "complex family relationships" to "complex family relationships",
        "pets" to "pets",
        "level system" to "level system",
        "enemies become allies" to "enemies become allies",
        "cute children" to "cute children",
        "death" to "death",
        "mythical beasts" to "mythical beasts",
        "enemies become lovers" to "enemies become lovers",
        "soul power" to "soul power",
        "ancient china" to "ancient china",
        "dao comprehension" to "dao comprehension",
        "charming protagonist" to "charming protagonist",
        "acting" to "acting",
        "time travel" to "time travel",
        "multiple reincarnated individuals" to "multiple reincarnated individuals",
        "past trauma" to "past trauma",
        "absent parents" to "absent parents",
        "master-disciple relationship" to "master-disciple relationship",
        "childcare" to "childcare",
        "r*pe victim becomes lover" to "r*pe victim becomes lover",
        "family" to "family",
        "secret organizations" to "secret organizations",
        "phoenixes" to "phoenixes",
        "appearance different from actual age" to "appearance different from actual age",
        "curses" to "curses",
        "age progression" to "age progression",
        "secret identity" to "secret identity",
        "sharp-tongued characters" to "sharp-tongued characters",
        "naive protagonist" to "naive protagonist",
        "knights" to "knights",
        "kingdom building" to "kingdom building",
        "spatial manipulation" to "spatial manipulation",
        "demon lord" to "demon lord",
        "tsundere" to "tsundere",
        "appearance changes" to "appearance changes",
        "cruel characters" to "cruel characters",
        "fantasy creatures" to "fantasy creatures",
        "assassins" to "assassins",
        "popular love interests" to "popular love interests",
        "spirit advisor" to "spirit advisor",
        "time manipulation" to "time manipulation",
        "dark" to "dark",
        "business management" to "business management",
        "heartwarming" to "heartwarming",
        "broken engagement" to "broken engagement",
        "destiny" to "destiny",
        "cooking" to "cooking",
        "religions" to "religions",
        "protagonist with multiple bodies" to "protagonist with multiple bodies",
        "cute protagonist" to "cute protagonist",
        "r-18" to "r-18"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/${
            if (mainCategory.isNullOrEmpty() && !tag.isNullOrEmpty()) "tag/$tag"
            else if (!mainCategory.isNullOrEmpty() && tag.isNullOrEmpty()) "category/$mainCategory"
            else "browse"
        }?order_by=$orderBy&page=$page"
        val document = app.get(url).document
        val returnValue = document.select("div.grid a[href^='/novel/']")
            .mapNotNull { card ->
                val href = card.attr("href")
                val title =
                    card.selectFirst("h3")?.text() ?: return@mapNotNull null
                newSearchResponse(
                    name = title,
                    url = fixUrl(href)
                ) {
                    posterUrl = fixUrlNull(card.selectFirst("img")?.attr("src"))
                }
            }
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val jsonData = document.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw Exception("Invalid data")
        val nextData = parseJson<NextData>(jsonData)
        val novel = nextData.props.pageProps.novel ?: throw Exception("Novel data not found")
        val title = novel.title ?: throw Exception("Title not found")
        val slug = novel.slug ?: url.removeSuffix("/").substringAfterLast("/")
        val chapterCount = novel.chapterCount ?: 0

        val chapters = (1..chapterCount).map { chapterNumber ->
            val chapterUrl = "$mainUrl/chapter/$slug-$chapterNumber"
            newChapterData("Chapter $chapterNumber", chapterUrl)
        }

        val id = novel.id?.toString() ?: url.removeSuffix("/").substringAfterLast("/")

        return newStreamResponse(title, url, chapters) {
            posterUrl = novel.coverKey?.let { "https://cdn.wuxiaworld.eu/$it-480.webp" }
                ?: document.selectFirst("meta[property='og:image']")?.attr("content")
            synopsis = novel.description
            author = novel.author?.name
            setStatus(novel.status?.lowercase())
            tags = novel.genres?.mapNotNull { it.name }.orEmpty() + novel.tags?.mapNotNull { it.name }.orEmpty()
            reviewData = id
            related = getRelated(document)
        }
    }

    private fun getRelated(document: Document): List<SearchResponse>? {
        val section = document.selectFirst("section[aria-label='You may also like'] > div.grid")
        return section?.select("a")?.mapNotNull { aTag ->
            val href = aTag.attr("href")
            val title = aTag.selectFirst("h3")?.text() ?: return@mapNotNull null
            if (href.isEmpty() || title.isEmpty()) return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(aTag.selectFirst("img")?.attr("src"))
            }
        }.takeIf { !it.isNullOrEmpty() }
    }

    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val id = data?.toIntOrNull() ?: return emptyList()
        val inputJson = "{\"0\":{\"json\":{\"novelId\":$id,\"sort\":\"helpful\",\"rating\":null,\"limit\":10,\"direction\":\"forward\"}}}"
        val realUrl = "$mainUrl/api/trpc/review.list?batch=1&input=${Uri.encode(inputJson)}"


        val res = app.get(realUrl).parsedSafe<Array<TrpcReviewResponse>>()
        val items = res?.firstOrNull()?.result?.data?.json?.items ?: return emptyList()
        return items.mapNotNull { item ->
            val reviewTxt = item.bodyHtml ?: return@mapNotNull null
            val cleanDate = item.createdAt?.replace("T", " ")

            newReview(org.jsoup.Jsoup.parse(reviewTxt).text()) {
                containsSpoilers = item.isSpoiler == true
                username = item.author?.name
                date = cleanDate
                avatarUrl = item.author?.avatar
                rating = item.rating?.times(200)
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val content = document.selectFirst("article, div.chapter-content, div.prose, #main") ?: return null
        content.select("script, style, .ads, header, footer").remove()
        return content.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${Uri.encode(query)}"
        val document = app.get(url).document
        return document.select("div.grid a[href^='/novel/']")
            .mapNotNull { card ->
                val href = card.attr("href")
                val title =
                    card.selectFirst("h3")?.text() ?: return@mapNotNull null
                newSearchResponse(
                    name = title,
                    url = fixUrl(href)
                ) {
                    posterUrl = fixUrlNull(card.selectFirst("img")?.attr("src"))
                }
            }
    }



    data class TrpcReviewResponse(
        @JsonProperty("result") val result: TrpcResult?
    )

    data class TrpcResult(
        @JsonProperty("data") val data: TrpcData?
    )

    data class TrpcData(
        @JsonProperty("json") val json: TrpcJson?
    )

    data class TrpcJson(
        @JsonProperty("items") val items: List<TrpcReviewItem>?
    )

    data class TrpcReviewItem(
        @JsonProperty("bodyHtml") val bodyHtml: String?,
        @JsonProperty("rating") val rating: Int?,
        @JsonProperty("isSpoiler") val isSpoiler: Boolean?,
        @JsonProperty("createdAt") val createdAt: String?,
        @JsonProperty("author") val author: TrpcAuthor?
    )

    data class TrpcAuthor(
        @JsonProperty("name") val name: String?,
        @JsonProperty("avatar") val avatar: String?
    )

    data class NextData(
        @JsonProperty("props") val props: NextProps
    )

    data class NextProps(
        @JsonProperty("pageProps") val pageProps: NextPageProps
    )

    data class NextPageProps(
        @JsonProperty("novel") val novel: WuxiaNovelDto?
    )

    data class WuxiaAuthorDto(
        @JsonProperty("name") val name: String?,
        @JsonProperty("slug") val slug: String?
    )

    data class WuxiaNovelDto(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("coverKey") val coverKey: String?,
        @JsonProperty("chapterCount") val chapterCount: Int?,
        @JsonProperty("genres") val genres: List<WuxiaGenreDto>?,
        @JsonProperty("tags") val tags: List<WuxiaTagDto>?,
        @JsonProperty("author") val author: WuxiaAuthorDto?
    )

    data class WuxiaGenreDto(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("name") val name: String?
    )

    data class WuxiaTagDto(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("name") val name: String?
    )
}