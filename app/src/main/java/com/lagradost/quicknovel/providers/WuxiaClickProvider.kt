package com.lagradost.quicknovel.providers

import android.net.Uri
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.nodes.Element

class WuxiaClickProvider :  MainAPI() {
    override val name = "WuxiaClick"
    override val mainUrl = "https://wuxia.click"
    override val iconId = R.drawable.icon_wuxiaclick
    override val iconBackgroundId = R.color.wuxiacliCkcolor


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
        "Translated chapters" to "-num_of_chaps",
        "Rating" to "-rating",
        "Name" to "-name",
        "Old" to "-created_at",
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
    ): HeadMainPageResponse
    {
        val url = "$mainUrl/${
            if(mainCategory.isNullOrEmpty() && !tag.isNullOrEmpty()) "tag/$tag" 
            else if(!mainCategory.isNullOrEmpty() && tag.isNullOrEmpty()) "category/$mainCategory"
            else "search"
        }?page=$page&order_by=$orderBy"
        println(url)
        val document = app.get(url).document

        val returnValue = document.select("div.mantine-Grid-root > div.mantine-Grid-col > div > a").mapNotNull { card ->
            val href = card.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("div.mantine-w2rcte > div")?.text() ?: return@mapNotNull null


            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = card.selectFirst("img")?.attr("src")
            }

        }
        return HeadMainPageResponse(url, returnValue)
    }

    private fun getChapters(dc: Element?, url:String):List<ChapterData> {
        if(dc == null) return emptyList()
        val totalChapters = dc.selectFirst("div.mantine-Group-root div.mantine-19n0k2t")?.text()?.substringBefore(" C")?.toIntOrNull()
        val slug = url.substringAfterLast("/")
        return if (totalChapters == null) emptyList() else (1..totalChapters).map { chapterNumber ->
            val chapterUrl = "$mainUrl/chapter/$slug-$chapterNumber"
            newChapterData("Chapter $chapterNumber", chapterUrl)
        }
    }

    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document//body > div.body > div > div > div.col-lg-8
        val infoDiv = document.selectFirst("div.mantine-Container-root > div.mantine-Paper-root.mantine-Card-root > div")
        val title = infoDiv?.selectFirst("h5")?.text() ?: throw Exception("Title not found")
        val chapters = getChapters(infoDiv, url)

        return newStreamResponse(title,fixUrl(url), chapters) {
            this.posterUrl = infoDiv.selectFirst("img")?.attr("src")
            this.synopsis = infoDiv.selectFirst("div.mantine-Spoiler-root > div.mantine-Spoiler-content > div > div.mantine-Text-root")?.text() ?: ""

            this.author = infoDiv.selectFirst("div.mantine-lqk3v2 > div")?.text()?.substringAfterLast("By ") ?: ""

            setStatus(infoDiv.selectFirst("div.mantine-Group-root.mantine-1uxmzbt > div.mantine-1huvzos")?.text())

            this.tags = infoDiv.select("div.mantine-Spoiler-root > div.mantine-Spoiler-content > div > div.mantine-Group-root > div")?.mapNotNull {
                it.text().trim().takeIf { text ->  !text.isEmpty() }
            }
        }
    }


    override suspend fun loadHtml(url: String): String {
        val document = app.get(url).document
        val contentElement = document.select("div.mantine-Container-root.mantine-sqid3s > div.mantine-Paper-root.mantine-18ybim5 > div:nth-child(3)").joinToString("</br>")
        return contentElement
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${Uri.encode(query)}"
        val document = app.get(url).document
        return document.select("div.mantine-Grid-root > div.mantine-Grid-col > div > a")
            .mapNotNull { card ->
                val href = card.attr("href") ?: return@mapNotNull null
                val title =
                    card.selectFirst("div.mantine-w2rcte > div")?.text() ?: return@mapNotNull null
                newSearchResponse(
                    name = title,
                    url = href
                ) {
                    posterUrl = card.selectFirst("img")?.attr("src")
                }

            }
    }
}