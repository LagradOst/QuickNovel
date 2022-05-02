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

    override val iconId = R.drawable.icon_readfromnet

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
        Pair("Graphic Novels", "graphic-novels"),
        Pair("Post Apocalyptic", "post-apocalyptic"),
        Pair("Apocalyptic", "apocalyptic"),
        Pair("True Crime", "true-crime"),
        Pair("Interracial Romance", "interracial-romance"),
        Pair("Cookbooks", "cookbooks"),
        Pair("Plays", "plays"),
        Pair("Dark", "dark"),
        Pair("Literature", "literature"),
        Pair("GLBT", "glbt"),
        Pair("Cooking, Food & Wine", "cooking-food-wine"),
        Pair("Economics", "economics"),
        Pair("Magic", "magic"),
        Pair("Entertainment", "entertainment"),
        Pair("Health", "health"),
        Pair("Novella", "novella"),
        Pair("Academic", "academic"),
        Pair("Drama", "drama"),
        Pair("Sociology", "sociology"),
        Pair("Lesbian Romance", "lesbian-romance"),
        Pair("Arts & Photography", "arts-photography"),
        Pair("European Literature", "european-literature"),
        Pair("Literary Fiction", "literary-fiction"),
        Pair("African American", "african-american"),
        Pair("Women & Gender Studies", "women-gender-studies"),
        Pair("Adult", "adult"),
        Pair("Steampunk", "steampunk"),
        Pair("Time Travel", "time-travel"),
        Pair("World War II", "world-war-ii"),
        Pair("Erotic", "erotic"),
        Pair("Light Novel", "light-novel"),
        Pair("Aliens", "aliens"),
        Pair("Urban", "urban"),
        Pair("Women's Fiction", "women-s-fiction"),
        Pair("Theatre", "theatre"),
        Pair("Military History", "military-history"),
        Pair("Health, Mind & Body", "health-mind-body"),
        Pair("Manga", "manga"),
        Pair("Category Romance", "category-romance"),
        Pair("Juvenile", "juvenile"),
        Pair("Feminism", "feminism"),
        Pair("Canada", "canada"),
        Pair("Lesbian", "lesbian"),
        Pair("Essays", "essays"),
        Pair("Science Fiction Romance", "science-fiction-romance"),
        Pair("Social Sciences", "social-sciences"),
        Pair("Action", "action"),
        Pair("Media Tie In", "media-tie-in"),
        Pair("Paranormal Fiction", "paranormal-fiction"),
        Pair("Business & Investing", "business-investing"),
        Pair("Environment", "environment"),
        Pair("Regency Romance", "regency-romance"),
        Pair("Action Adventure", "action-adventure"),
        Pair("Magical Realism", "magical-realism"),
        Pair("Audiobook", "audiobook"),
        Pair("Space", "space"),
        Pair("Regency", "regency"),
        Pair("Parenting & Families", "parenting-families"),
        Pair("Criticism", "criticism"),
        Pair("Africa", "africa"),
        Pair("Education", "education"),
        Pair("Home & Garden", "home-garden"),
        Pair("Alternate History", "alternate-history"),
        Pair("Space Opera", "space-opera"),
        Pair("Mythology", "mythology"),
        Pair("Dragons", "dragons"),
        Pair("Finance", "finance"),
        Pair("Traditional Regency", "traditional-regency"),
        Pair("Dark Romance", "dark-romance"),
        Pair("Cooking", "cooking"),
        Pair("Australia", "australia"),
        Pair("Culture", "culture"),
        Pair("Law", "law"),
        Pair("Spy Thriller", "spy-thriller"),
        Pair("Dark Fantasy", "dark-fantasy"),
        Pair("Epic Fantasy", "epic-fantasy"),
        Pair("Inspirational", "inspirational"),
        Pair("Theology", "theology"),
        Pair("Food", "food"),
        Pair("Mathematics", "mathematics"),
        Pair("Textbooks", "textbooks"),
        Pair("Literary Criticism", "literary-criticism"),
        Pair("Crafts", "crafts"),
        Pair("Christianity", "christianity"),
        Pair("Games", "games"),
        Pair("Nature", "nature"),
        Pair("Harem", "harem"),
        Pair("North American History", "north-american-history"),
        Pair("Love Inspired", "love-inspired"),
        Pair("Film", "film"),
        Pair("African American Romance", "african-american-romance"),
        Pair("Humanities", "humanities"),
        Pair("School", "school"),
        Pair("Role Playing Games", "role-playing-games"),
        Pair("Young Adult Romance", "young-adult-romance"),
        Pair("Professional & Technical", "professional-technical"),
        Pair("Espionage", "espionage"),
        Pair("Dystopian", "dystopian"),
        Pair("Urban Fantasy Romance", "urban-fantasy-romance"),
        Pair("Japan", "japan"),
        Pair("Photography", "photography"),
        Pair("Parenting", "parenting"),
        Pair("Fairy Tales", "fairy-tales"),
        Pair("Horses", "horses"),
        Pair("Clean Romance", "clean-romance"),
        Pair("France", "france"),
        Pair("Gothic", "gothic"),
        Pair("Abuse", "abuse"),
        Pair("Journalism", "journalism"),
        Pair("Biography Memoir", "biography-memoir"),
        Pair("BDSM Romance", "bdsm-romance"),
        Pair("Superheroes", "superheroes"),
        Pair("Gardening", "gardening"),
        Pair("New Adult, Historical Fiction", "new-adult-historical-fiction"),
        Pair("Couture", "couture"),
        Pair("Baseball", "baseball"),
        Pair("Russia", "russia"),
        Pair("Romantic Comedy", "romantic-comedy"),
        Pair("Thrillers", "thrillers"),
        Pair("Fantasy Romance", "fantasy-romance"),
        Pair("Speculative Fiction", "speculative-fiction"),
        Pair("Books About Books", "books-about-books"),
        Pair("Western Romance", "western-romance"),
        Pair("Computer Science", "computer-science"),
        Pair("Fairies", "fairies"),
        Pair("Crime Fiction", "crime-fiction"),
        Pair("Young Adult, New Adult", "young-adult-new-adult"),
        Pair("Historical Romantic Western", "historical-romantic-western"),
        Pair("China", "china"),
        Pair("Chapter Books", "chapter-books"),
        Pair("Holocaust", "holocaust"),
        Pair("Crafts & Hobbies", "crafts-hobbies"),
        Pair("Survival", "survival"),
        Pair("Fashion", "fashion"),
        Pair("Historical Mystery", "historical-mystery"),
        Pair("Ireland", "ireland"),
        Pair("Family", "family"),
        Pair("Medieval", "medieval"),
        Pair("American", "american"),
        Pair("Theater", "theater"),
        Pair("Motorcycle", "motorcycle"),
        Pair("Civil War", "civil-war"),
        Pair("World War I", "world-war-i"),
        Pair("Litrpg", "litrpg"),
        Pair("Leadership", "leadership"),
        Pair("Military", "military"),
        Pair("Cozy Mysteries", "cozy-mysteries"),
        Pair("Action Romance", "action-romance"),
        Pair("Military Science Fiction", "military-science-fiction"),
        Pair("Folklore", "folklore"),
        Pair("Detective & Western", "detective-western"),
        Pair("Architecture", "architecture"),
        Pair("American History", "american-history"),
        Pair("Gay", "gay"),
        Pair("British Literature", "british-literature"),
        Pair("Technology", "technology"),
        Pair("Retellings", "retellings"),
        Pair("Design", "design"),
        Pair("Mental Health", "mental-health"),
        Pair("Witches", "witches"),
        Pair("Post Apocalypse", "post-apocalypse"),
        Pair("New Adult Romance", "new-adult-romance"),
        Pair("Football", "football"),
        Pair("Alternative History", "alternative-history"),
        Pair("Werewolves", "werewolves"),
        Pair("Transport", "transport"),
        Pair("Animal Fiction", "animal-fiction"),
        Pair("Young Adult, New Adult, Adult", "young-adult-new-adult-adult"),
        Pair("Queer", "queer"),
        Pair("Computers & Internet", "computers-internet"),
        Pair("Terrorism", "terrorism"),
        Pair("Teaching", "teaching"),
        Pair("New York", "new-york"),
        Pair("Cats", "cats"),
        Pair("Anthropology", "anthropology"),
        Pair("Christian Romance", "christian-romance"),
        Pair("Dogs", "dogs"),
        Pair("Cyberpunk", "cyberpunk"),
        Pair("Medical", "medical"),
        Pair("Lds", "lds"),
        Pair("Fae", "fae"),
        Pair("Emergency Services", "emergency-services"),
        Pair("Rock Stars", "rock-stars"),
        Pair("Paranormal & Fantasy", "paranormal-fantasy"),
        Pair("Occult", "occult"),
        Pair("Weird Fiction", "weird-fiction"),
        Pair("Dark Erotica", "dark-erotica"),
        Pair("Culinary", "culinary"),
        Pair("Medieval Romance", "medieval-romance"),
        Pair("Supernatural", "supernatural"),
        Pair("Pulp", "pulp"),
        Pair("Intense Emotional Fiction", "intense-emotional-fiction"),
        Pair("Noir", "noir"),
        Pair("College", "college"),
        Pair("BDSM and D/s", "bdsm-and-d-s"),
        Pair("Angels", "angels"),
        Pair("Young Adult Fiction", "young-adult-fiction"),
        Pair("Race", "race"),
        Pair("Buddhism", "buddhism"),
        Pair("Satire", "satire"),
        Pair("Physics", "physics"),
        Pair("Love Inspired Suspense", "love-inspired-suspense"),
        Pair("Disambiguation Profile", "disambiguation-profile"),
        Pair("Personal Finance", "personal-finance"),
        Pair("M F Romance", "m-f-romance"),
        Pair("Italy", "italy"),
        Pair("Ghost Stories", "ghost-stories"),
        Pair("YA Mystery/suspense", "ya-mystery-suspense"),
        Pair("Romance/suspense", "romance-suspense"),
        Pair("Psychological Thriller", "psychological-thriller"),
        Pair("Novelizations", "novelizations"),
        Pair("Splatter Punk", "splatter-punk"),
        Pair("Relationships", "relationships"),
        Pair("Bizarro Fiction", "bizarro-fiction"),
        Pair("Mystery and Thrillers", "mystery-and-thrillers"),
        Pair("Ghosts", "ghosts"),
        Pair("Engineering", "engineering"),
        Pair("Coming Of Age", "coming-of-age"),
        Pair("Church", "church"),
        Pair("Archaeology", "archaeology"),
        Pair("Programming", "programming"),
        Pair("Linguistics", "linguistics"),
        Pair("German Literature", "german-literature"),
        Pair("Disambiguation", "disambiguation"),
        Pair("Sexuality", "sexuality"),
        Pair("Post Apocalyptic Fiction", "post-apocalyptic-fiction"),
        Pair("Lesbian Mystery", "lesbian-mystery"),
        Pair("Social Issues", "social-issues"),
        Pair("Gay Romance", "gay-romance"),
        Pair("Biology", "biology"),
        Pair("Research", "research"),
        Pair("Mystery & Thrillers, Particularl", "mystery-thrillers-particularl"),
        Pair("Pirates", "pirates"),
        Pair("Knitting", "knitting"),
        Pair("Islam", "islam"),
        Pair("Female Authors", "female-authors"),
        Pair("Medicine", "medicine"),
        Pair("Biblical Fiction", "biblical-fiction"),
        Pair("Supernatural Thriller", "supernatural-thriller"),
        Pair("Halloween", "halloween"),
        Pair("Gothic Romance", "gothic-romance"),
        Pair("Asian Literature", "asian-literature"),
        Pair("Southern Gothic", "southern-gothic"),
        Pair("Shifter Romance", "shifter-romance"),
        Pair("Quilting", "quilting"),
        Pair("Political Science", "political-science"),
        Pair("The United States Of America", "the-united-states-of-america"),
        Pair("Teen", "teen"),
        Pair("Sci Fi", "sci-fi"),
        Pair("Nurses", "nurses"),
        Pair("Mysteries", "mysteries"),
        Pair("BDSM Erotica", "bdsm-erotica"),
        Pair("YA Fantasy", "ya-fantasy"),
        Pair("Unfinished", "unfinished"),
        Pair("Romance & Mainstream Women's Fic", "romance-mainstream-women-s-fic"),
        Pair("Movies", "movies"),
        Pair("Womens", "womens"),
        Pair("Own", "own"),
        Pair("Nursing", "nursing"),
        Pair("Modern & Contemporary Fiction", "modern-contemporary-fiction"),
        Pair("Historical Fantasy", "historical-fantasy"),
        Pair("Harlequin Presents", "harlequin-presents"),
        Pair("Comedy", "comedy"),
        Pair("Scotland", "scotland"),
        Pair("Omegaverse", "omegaverse"),
        Pair("Love Stories", "love-stories"),
        Pair("Gaming", "gaming"),
        Pair("Sports and Games", "sports-and-games"),
        Pair("M M F", "m-m-f"),
        Pair("Foods", "foods"),
        Pair("Birds", "birds"),
        Pair("Southern Fiction", "southern-fiction"),
        Pair("Pop Culture", "pop-culture"),
        Pair("Murder Mystery", "murder-mystery"),
        Pair("Humor & Comedy", "humor-comedy"),
        Pair("Fitness", "fitness"),
        Pair("Roman", "roman"),
        Pair("Northern Africa", "northern-africa"),
        Pair("Management", "management"),
        Pair("High School", "high-school"),
        Pair("Collections", "collections"),
        Pair("Transgender", "transgender"),
        Pair("Jewish", "jewish"),
        Pair("Interracial", "interracial"),
        Pair("Essay", "essay"),
        Pair("Boxing", "boxing"),
        Pair("Art History", "art-history"),
        Pair("Young Adult Fantasy", "young-adult-fantasy"),
        Pair("Thrillers & Suspense", "thrillers-suspense"),
        Pair("M/m", "m-m"),
        Pair("Judaism", "judaism"),
        Pair("Historical Fiction, Horror", "historical-fiction-horror"),
        Pair("High Fantasy", "high-fantasy"),
        Pair("Fan Fiction", "fan-fiction"),
        Pair("Family Saga", "family-saga"),
        Pair("Storytime", "storytime"),
        Pair("Reverse Harem Fantasy Romance", "reverse-harem-fantasy-romance"),
        Pair("Mystery/suspense", "mystery-suspense"),
        Pair("Israel", "israel"),
        Pair("Humorous Contemporary Romance", "humorous-contemporary-romance"),
        Pair("Hard Boiled", "hard-boiled"),
        Pair("Disability", "disability"),
        Pair("Psychological Thrillers", "psychological-thrillers"),
        Pair("Library Science", "library-science"),
        Pair("Lds Fiction", "lds-fiction"),
        Pair("Irish Literature", "irish-literature"),
        Pair("Cycling", "cycling"),
        Pair("Computers", "computers"),
        Pair("Catholic", "catholic"),
        Pair("Aviation", "aviation"),
        Pair("Atheism", "atheism"),
        Pair("Translation", "translation"),
        Pair("Screenplays", "screenplays"),
        Pair("Pseudoscience", "pseudoscience"),
        Pair("Paranormal and Fantasy", "paranormal-and-fantasy"),
        Pair("Monsters", "monsters"),
        Pair("Horror & Suspense", "horror-suspense"),
        Pair("Dinosaurs", "dinosaurs"),
        Pair("Surrealism", "surrealism"),
        Pair("Marriage", "marriage"),
        Pair("Astronomy", "astronomy"),
        Pair("Historical Paranormal Romance", "historical-paranormal-romance"),
        Pair("Dark Fiction", "dark-fiction"),
        Pair("Theory", "theory"),
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