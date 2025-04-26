package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.util.AppUtils.parseJson

class WattpadProvider : MainAPI() {
    override val mainUrl = "https://www.wattpad.com"
    override val name = "Wattpad"

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "https://www.wattpad.com/search/$query"
        val document = app.get(url).document
        return document.select(".story-card").mapNotNull { element ->
            val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null
            val img =
                fixUrlNull(element.selectFirst(".story-card-data > .cover > img")?.attr("src"))
            val info =
                element.selectFirst(".story-card-data > .story-info") ?: return@mapNotNull null
            val title = info.selectFirst(".sr-only")?.text() ?: return@mapNotNull null
            //val description = info.selectFirst(".description")?.text()
            SearchResponse(name = title, url = href, posterUrl = img, apiName = name)
        }
    }

    data class MainData(
        var part: Metadata? = null
    )

    data class Metadata(@JsonProperty("data") var data: Data? = null)

    data class TextUrl(
        @JsonProperty("text") var text: String? = null,
        @JsonProperty("refresh_token") var refreshToken: String? = null
    )

    data class Parts(

        @JsonProperty("id") var id: Int? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("url") var url: String? = null,
        //@JsonProperty("rating" ) var rating : Int?     = null,
        //@JsonProperty("draft"  ) var draft  : Boolean? = null

    )

    data class Data(
        //@JsonProperty("group"                  ) var group                  : Group?                        = null,
        @JsonProperty("text_url") var textUrl: TextUrl? = null,
        /*
        @JsonProperty("id"                     ) var id                     : Int?                          = null,
        @JsonProperty("title"                  ) var title                  : String?                       = null,
        @JsonProperty("url"                    ) var url                    : String?                       = null,
        @JsonProperty("rating"                 ) var rating                 : Int?                          = null,
        @JsonProperty("draft"                  ) var draft                  : Boolean?                      = null,
        @JsonProperty("modifyDate"             ) var modifyDate             : String?                       = null,
        @JsonProperty("createDate"             ) var createDate             : String?                       = null,
        @JsonProperty("length"                 ) var length                 : Int?                          = null,
        @JsonProperty("videoId"                ) var videoId                : String?                       = null,
        @JsonProperty("photoUrl"               ) var photoUrl               : String?                       = null,
        @JsonProperty("commentCount"           ) var commentCount           : Int?                          = null,
        @JsonProperty("voteCount"              ) var voteCount              : Int?                          = null,
        @JsonProperty("readCount"              ) var readCount              : Int?                          = null,
        @JsonProperty("hasBannedHeader"        ) var hasBannedHeader        : Boolean?                      = null,
        //@JsonProperty("dedication"             ) var dedication             : Dedication?                   = Dedication(),
        @JsonProperty("pages"                  ) var pages                  : Int?                          = null,
        @JsonProperty("wordCount"              ) var wordCount              : Int?                          = null,

        //@JsonProperty("source"                 ) var source                 : Source?                       = Source(),
        //
        @JsonProperty("isAdExempt"             ) var isAdExempt             : Boolean?                      = null,
        @JsonProperty("brandSafetyLevel"       ) var brandSafetyLevel       : Int?                          = null,
        @JsonProperty("brandSafetySource"      ) var brandSafetySource      : String?                       = null,
        @JsonProperty("knownUnsafe"            ) var knownUnsafe            : Boolean?                      = null,
        @JsonProperty("isAuthor"               ) var isAuthor               : Boolean?                      = null,
        @JsonProperty("pageNumber"             ) var pageNumber             : Int?                          = null,
        @JsonProperty("firstPage"              ) var firstPage              : Boolean?                      = null,
        @JsonProperty("lastPage"               ) var lastPage               : Boolean?                      = null,
        @JsonProperty("isMicroPart"            ) var isMicroPart            : Boolean?                      = null,
        @JsonProperty("isSmallPart"            ) var isSmallPart            : Boolean?                      = null,
        @JsonProperty("isMediumPart"           ) var isMediumPart           : Boolean?                      = null,
        @JsonProperty("isBigPart"              ) var isBigPart              : Boolean?                      = null,
        @JsonProperty("descCharLimit"          ) var descCharLimit          : Int?                          = null,
        @JsonProperty("isHybeStory"            ) var isHybeStory            : Boolean?                      = null,
        @JsonProperty("meta"                   ) var meta                   : Meta?                         = Meta(),
        @JsonProperty("hide3rdPartyAuth"       ) var hide3rdPartyAuth       : Boolean?                      = null,
        @JsonProperty("reportUrl"              ) var reportUrl              : String?                       = null,
        @JsonProperty("isAdmin"                ) var isAdmin                : Boolean?                      = null,
        @JsonProperty("isModerator"            ) var isModerator            : Boolean?                      = null,
        @JsonProperty("showAdminPanel"         ) var showAdminPanel         : Boolean?                      = null,
        //@JsonProperty("nextPart"               ) var nextPart               : NextPart?                     = NextPart(),
        @JsonProperty("nextPage"               ) var nextPage               : String?                       = null,
        @JsonProperty("ampUrl"                 ) var ampUrl                 : String?                       = null,
        @JsonProperty("isTablet"               ) var isTablet               : Boolean?                      = null,
        //@JsonProperty("media"                  ) var media                  : ArrayList<Media>              = arrayListOf(),
        @JsonProperty("bgCover"                ) var bgCover                : String?                       = null,
        @JsonProperty("isWriterPreview"        ) var isWriterPreview        : String?                       = null,
        @JsonProperty("storyInfoFlag"          ) var storyInfoFlag          : Boolean?                      = null,
        @JsonProperty("showCover"              ) var showCover              : Boolean?                      = null,
        @JsonProperty("showCarousel"           ) var showCarousel           : Boolean?                      = null,
        //@JsonProperty("mediaShare"             ) var mediaShare             : ArrayList<MediaShare>         = arrayListOf(),
        //@JsonProperty("socialShareHidden"      ) var socialShareHidden      : ArrayList<SocialShareHidden>  = arrayListOf(),
        //@JsonProperty("socialShareVisible"     ) var socialShareVisible     : ArrayList<SocialShareVisible> = arrayListOf(),
        @JsonProperty("storyText"              ) var storyText              : String?                       = null,
        //@JsonProperty("page"                   ) var page                   : Page?                         = Page(),
        @JsonProperty("isDesktop"              ) var isDesktop              : Boolean?                      = null,
        @JsonProperty("isStoryReading"         ) var isStoryReading         : Boolean?                      = null,
        @JsonProperty("anonymousUser"          ) var anonymousUser          : Boolean?                      = null,
        @JsonProperty("bottomBannerImage"      ) var bottomBannerImage      : String?                       = null,
        @JsonProperty("bottomBannerTitle"      ) var bottomBannerTitle      : String?                       = null,
        @JsonProperty("dismissibleBanner"      ) var dismissibleBanner      : Boolean?                      = null,
        @JsonProperty("showBottomBanner"       ) var showBottomBanner       : Boolean?                      = null,
        @JsonProperty("rank"                   ) var rank                   : String?                       = null,
        @JsonProperty("branchLink"             ) var branchLink             : String?                       = null,
        @JsonProperty("showStoryReadingSurvey" ) var showStoryReadingSurvey : Boolean?                      = null*/

    )
/*
    data class Meta (

        @JsonProperty("url"            ) var url            : String?   = null,
        @JsonProperty("description"    ) var description    : String?   = null,
        @JsonProperty("keywords"       ) var keywords       : String?   = null,
        @JsonProperty("image"          ) var image          : String?   = null,
        @JsonProperty("banner"         ) var banner         : String?   = null,
        @JsonProperty("title"          ) var title          : String?   = null,
        @JsonProperty("facebook"       ) var facebook       : Boolean?  = null,
        @JsonProperty("twitter"        ) var twitter        : Boolean?  = null,
        @JsonProperty("apple"          ) var apple          : Boolean?  = null,
        @JsonProperty("pinterest"      ) var pinterest      : Boolean?  = null,
        @JsonProperty("googlePlus"     ) var googlePlus     : Boolean?  = null,
        @JsonProperty("otherCrawler"   ) var otherCrawler   : Boolean?  = null,
       // @JsonProperty("deeplink"       ) var deeplink       : Deeplink? = Deeplink(),
        //@JsonProperty("story"          ) var story          : Story?    = Story(),
        @JsonProperty("structuredData" ) var structuredData : String?   = null,
        @JsonProperty("robots"         ) var robots         : String?   = null,
        @JsonProperty("next"           ) var next           : String?   = null

    )*/

    data class Group(
        @JsonProperty("parts") var parts: ArrayList<Parts> = arrayListOf(),
/*
        @JsonProperty("id"                  ) var id                  : String?           = null,
        @JsonProperty("title"               ) var title               : String?           = null,
        @JsonProperty("createDate"          ) var createDate          : String?           = null,
        @JsonProperty("modifyDate"          ) var modifyDate          : String?           = null,
       // @JsonProperty("language"            ) var language            : Language?         = Language(),
       // @JsonProperty("user"                ) var user                : User?             = User(),
        @JsonProperty("description"         ) var description         : String?           = null,
        @JsonProperty("cover"               ) var cover               : String?           = null,
        @JsonProperty("cover_timestamp"     ) var coverTimestamp      : String?           = null,
        @JsonProperty("completed"           ) var completed           : Boolean?          = null,
        @JsonProperty("categories"          ) var categories          : ArrayList<Int>    = arrayListOf(),
        @JsonProperty("tags"                ) var tags                : ArrayList<String> = arrayListOf(),
        @JsonProperty("rating"              ) var rating              : Int?              = null,
        @JsonProperty("url"                 ) var url                 : String?           = null,
        @JsonProperty("deleted"             ) var deleted             : Boolean?          = null,
        @JsonProperty("rankings"            ) var rankings            : ArrayList<String> = arrayListOf(),
        @JsonProperty("isAdExempt"          ) var isAdExempt          : Boolean?          = null,
        @JsonProperty("draft"               ) var draft               : Boolean?          = null,
        @JsonProperty("isPaywalled"         ) var isPaywalled         : Boolean?          = null,
        @JsonProperty("isBrandSafe"         ) var isBrandSafe         : Boolean?          = null,
        @JsonProperty("USReader"            ) var USReader            : Boolean?          = null,
        @JsonProperty("category"            ) var category            : Int?              = null,
        @JsonProperty("mainCategory"        ) var mainCategory        : String?           = null,
        @JsonProperty("mainCategoryEnglish" ) var mainCategoryEnglish : String?           = null,
        @JsonProperty("inLanguage"          ) var inLanguage          : String?           = null
*/
    )

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val toc = document.select(".story-parts > ul > li > a").mapNotNull { a ->
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val name = (a.selectFirst("div") ?: a.selectFirst(".part__label"))?.text()
                ?: return@mapNotNull null
            newChapterData(url = href, name = name)
        }

        val title = document.selectFirst(".story-info > .sr-only")?.text()
            ?: document.selectFirst(".item-title")?.text()
            ?: throw ErrorLoadingException("No title")


        return newStreamResponse(name = title, url = url, data = toc) {
            posterUrl = fixUrlNull(document.selectFirst(".story-cover > img")?.attr("src"))
            author = document.selectFirst(".author-info__username > a")
                ?.text()
            tags = document.select("ul.tag-items > li > a").map { element ->
                element.text()
            }
            synopsis = document.selectFirst(".description-text")?.text()
        }
    }

    /*window.prefetched = */

    override suspend fun loadHtml(url: String): String {
        val response = app.get(url)
        val htmlJson =
            response.text.substringAfter("window.prefetched = ").substringBefore("</script>")

        var suffix = ""
        try {
            val data = parseJson<Map<String, Metadata>>(htmlJson)
            data.values.firstOrNull()?.data?.textUrl?.text?.let { str ->
                val index = str.indexOf('?')
                val before = str.substring(0 until index)
                val after = str.substring(index until str.length)

                // should be while(true) but cant be sure so I placed a upper bounds of 100
                for (i in 1..100) {
                    val text = app.get("$before-$i$after").text
                    // if the response is too short then we break because it probs did too much
                    if (text.length < 30) {
                        break
                    }
                    suffix += text
                }
            }
        } catch (e: Exception) {
            logError(e)
            suffix = ""
        }
        return suffix
        /*val document = response.document
        return document.selectFirst("pre")
            ?.apply { removeClass("trinityAudioPlaceholder"); removeClass("comment-marker") }
            ?.html()?.plus(suffix)*/
    }
}