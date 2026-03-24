package com.lagradost.quicknovel.providers

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

class LnoriProvider :  MainAPI() {
    override val name = "Inori"
    override val mainUrl = "https://lnori.com"
    override val iconId = R.drawable.icon_lnori
    override val iconBackgroundId = R.color.white


    override val hasMainPage = true

    override val orderBys = listOf(
        "Relevance" to "",
        "Title" to "title",
        "Year Released" to "year-released",
        "Volumes" to "volumes"
    )

    override val tags = listOf(
        "All" to "",
        "Academy" to "academy",
        "Action" to "action",
        "Adult Protagonist" to "adult-protagonist",
        "Adventure" to "adventure",
        "Age Gap" to "age-gap",
        "Airhead" to "airhead",
        "Alchemy" to "alchemy",
        "Animals" to "animals",
        "Anime Tie-in" to "anime-tie-in",
        "Aristocracy" to "aristocracy",
        "Battle" to "battle",
        "Books" to "books",
        "Boys Love" to "boys-love",
        "Business" to "business",
        "Camping" to "camping",
        "Childhood Friend" to "childhood-friend",
        "Chuunibyou" to "chuunibyou",
        "Combat" to "combat",
        "Comedy" to "comedy",
        "Contract Marriage" to "contract-marriage",
        "Cooking" to "cooking",
        "Crime" to "crime",
        "Cross-dressing" to "cross-dressing",
        "Dark" to "dark",
        "Dark Fantasy" to "dark-fantasy",
        "Demon Lord" to "demon-lord",
        "Demons" to "demons",
        "Dragons" to "dragons",
        "Drama" to "drama",
        "Dungeon" to "dungeon",
        "Dungeon Diving" to "dungeon-diving",
        "Dystopian" to "dystopian",
        "Ecchi" to "ecchi",
        "Elf" to "elf",
        "Enemies to Lovers" to "enemies-to-lovers",
        "Fairies" to "fairies",
        "Familiars" to "familiars",
        "Family" to "family",
        "Fanservice" to "fanservice",
        "Fantasy" to "fantasy",
        "Fantasy World" to "fantasy-world",
        "Female Protagonist" to "female-protagonist",
        "First Person" to "first-person",
        "Fish Out of Water" to "fish-out-of-water",
        "Food" to "food",
        "Friendship" to "friendship",
        "Futuristic" to "futuristic",
        "Game Elements" to "game-elements",
        "Gamer Protagonist" to "gamer-protagonist",
        "Gender Bender" to "gender-bender",
        "Genius" to "genius",
        "Girls Love" to "girls-love",
        "Guns" to "guns",
        "Harem" to "harem",
        "Heartwarming" to "heartwarming",
        "High Fantasy" to "high-fantasy",
        "High School" to "high-school",
        "Historical" to "historical",
        "Historical Fantasy" to "historical-fantasy",
        "Horror" to "horror",
        "Humor" to "humor",
        "Invention" to "invention",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Knights" to "knights",
        "Lgbtq" to "lgbtq",
        "Lighthearted" to "lighthearted",
        "Literary" to "literary",
        "Magic" to "magic",
        "Magic Academy" to "magic-academy",
        "Magical Weapons" to "magical-weapons",
        "Maid" to "maid",
        "Male Protagonist" to "male-protagonist",
        "Marriage" to "marriage",
        "Martial Arts" to "martial-arts",
        "Master And Servant" to "master-and-servant",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Medieval" to "medieval",
        "Military" to "military",
        "Modern Day" to "modern-day",
        "Moe" to "moe",
        "Monster Girls" to "monster-girls",
        "Monster Taming" to "monster-taming",
        "Monsters" to "monsters",
        "Multiple Pov" to "multiple-pov",
        "Mystery" to "mystery",
        "Nobility" to "nobility",
        "Not The Hero" to "not-the-hero",
        "Op Power" to "op-power",
        "Op Protagonist" to "op-protagonist",
        "Ordinary Protagonist" to "ordinary-protagonist",
        "Otaku" to "otaku",
        "Otome" to "otome",
        "Otome Game" to "otome-game",
        "Overpowered" to "overpowered",
        "Paranormal" to "paranormal",
        "Past Life" to "past-life",
        "Period Piece" to "period-piece",
        "Personal Growth" to "personal-growth",
        "Political Marriage" to "political-marriage",
        "Politics" to "politics",
        "Princess" to "princess",
        "Reincarnation" to "reincarnation",
        "Revenge" to "revenge",
        "Reverse Harem" to "reverse-harem",
        "Rewriting History" to "rewriting-history",
        "Romance" to "romance",
        "Romantic Fantasy" to "romantic-fantasy",
        "Rpg" to "rpg",
        "Satire" to "satire",
        "School" to "school",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shounen" to "shounen",
        "Slice Of Life" to "slice-of-life",
        "Slow Life" to "slow-life",
        "Snarky Protagonist" to "snarky-protagonist",
        "Sorcery" to "sorcery",
        "Strategy" to "strategy",
        "Strong Female Lead" to "strong-female-lead",
        "Supernatural" to "supernatural",
        "Superpowers" to "superpowers",
        "Survival" to "survival",
        "Sword And Sorcery" to "sword-and-sorcery",
        "Thriller" to "thriller",
        "Time Travel" to "time-travel",
        "Tsundere" to "tsundere",
        "Underdog" to "underdog",
        "Unique Ability" to "unique-ability",
        "Vampire" to "vampire",
        "Video Game" to "video-game",
        "Video Game Related" to "video-game-related",
        "Video Game Tie-in" to "video-game-tie-in",
        "Villainess" to "villainess",
        "Violence" to "violence",
        "Vrmmo" to "vrmmo",
        "War" to "war",
        "Weak Protagonist" to "weak-protagonist",
        "Witch" to "witch",
        "Zero To Hero" to "zero-to-hero"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val url = "$mainUrl/library"
        val document = app.get(url).document

        val returnValue = document.select("section article.card").mapNotNull { card ->
            if(!tag.isNullOrBlank()){
                val tags = card.attr("data-tags")
                if(!tags.contains(tag)) return@mapNotNull null
            }
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("h2")?.text() ?: return@mapNotNull null


            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = card.selectFirst("img")?.attr("src")
            }

        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document
        val infoDiv = document.select("section.hero-section")

        // Extract title
        val title = infoDiv.selectFirst("h1")?.text() ?: ""

        // Extract author
        val author = infoDiv.selectFirst("header.s-info p")?.text() ?: ""

        // Extract description/synopsis
        val synopsis = document.selectFirst("p.description.desc-wrapper")?.text() ?: ""

        val chapters = document.select("section.content-section > section.vol-grid > article").mapNotNull { li ->
            val name = li.selectFirst("h3.card-title")?.text()?:return@mapNotNull null
            val url = li.selectFirst("figure > a")?.attr("href")?:return@mapNotNull null
            newChapterData(name, url)
        }

        return newStreamResponse(title,fixUrl(url), chapters) {
            this.author = author
            this.posterUrl = infoDiv.selectFirst("article > figure > img")?.attr("src")
            this.synopsis = synopsis
            this.tags = infoDiv.select("nav.tags-box.desktop a").mapNotNull {
                it.text().trim().takeIf { text ->  !text.isEmpty() }
            }
        }
    }


    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val contentElement = document.select("main > article > *")
        return contentElement?.html()
    }


    private fun String.normalize() =
        this
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
            .replace("[^a-z0-9]".toRegex(), "")


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/library"
        val document = app.get(url).document

        return document.select("section article.card").mapNotNull { card ->
            val title = card.selectFirst("h2")?.text() ?: return@mapNotNull null
            if(!title.normalize().contains(query.normalize())) return@mapNotNull null

            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null

            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = card.selectFirst("img")?.attr("src")
            }

        }
    }
}