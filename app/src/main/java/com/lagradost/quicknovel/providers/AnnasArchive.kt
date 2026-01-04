package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.DownloadExtractLink
import com.lagradost.quicknovel.DownloadLink
import com.lagradost.quicknovel.DownloadLinkType
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newEpubResponse
import com.lagradost.quicknovel.newSearchResponse
import org.jsoup.Jsoup

class AnnasArchive : MainAPI() {
    override val hasMainPage = false
    override val hasReviews = false
    override val lang = "en"
    override val name = "Annas Archive"
    override val mainUrl = "https://annas-archive.li"

    //open val searchTags = "lang=en&content=book_fiction&ext=epub&sort=&"

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?index=&page=1&sort=&ext=epub&display=&q=${query.replace(" ", "+")}"
        val text = app.get(url).text.replace(
            Regex("<!--([\\W\\w]*?)-->")
        ) { it.groupValues[1] }

        val document = Jsoup.parse(text)

        val results = document.select("div.js-aarecord-list-outer a.custom-a")

        return results.mapNotNull { element ->
            val link = element.attr("href")
            if (!link.startsWith("/md5/")) {
                println("Skipping non-md5 link: $link")
                return@mapNotNull null
            }
            val title = element
                .selectFirst("a.js-vim-focus")
                ?.text()
            if (title == null) {
                return@mapNotNull null
            }
            newSearchResponse(
                name = title,
                url = fixUrlNull(link) ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(
                    element.selectFirst("img")?.attr("src")
                )
            }
        }
    }


    private fun extract(url: String, name: String): DownloadLinkType {
        return if (url.contains(".epub")) {
            DownloadLink(
                url = url,
                name = name,
                kbPerSec = 2
            )
        } else {
            DownloadExtractLink(
                url = url,
                name = name
            )
        }
    }

    private suspend fun loadFromJsonUrl(md5: String): LoadResponse {
        return app.get("$mainUrl/db/md5/$md5.json").parsed<AnnasArchiveRoot>().let { root ->
            newEpubResponse(
                name = root.fileUnifiedData?.titleBest ?: root.additional.topBox.title
                ?: root.fileUnifiedData?.titleAdditional!!.first(),
                url = "$mainUrl/md5/$md5",
                links = root.additional.downloadUrls.mapNotNull { list ->
                    val name = list.getOrNull(0) ?: return@mapNotNull null
                    val link = list.getOrNull(1) ?: return@mapNotNull null
                    extract(link, name)
                }) {
                author = root.fileUnifiedData?.authorBest ?: root.additional.topBox.author
                        ?: root.fileUnifiedData?.authorAdditional?.firstOrNull()
                posterUrl = root.fileUnifiedData?.coverUrlBest ?: root.additional.topBox.coverUrl
                        ?: root.fileUnifiedData?.coverUrlAdditional?.firstOrNull()
                synopsis = root.additional.topBox.description
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        /* cloudflare
        val md5Prefix = "$mainUrl/md5/"
        if (url.startsWith(md5Prefix)) {
            try {
                return loadFromJsonUrl(url.removePrefix(md5Prefix))
            } catch (t: Throwable) {
                logError(t)
            }
        }*/

        // backup non json parser
        val document = app.get(url).document

        return newEpubResponse(
            name = document.selectFirst("div.text-2xl")?.ownText()!!,
            url = url,
            links = document.select("ul.mb-4 > li > a.js-download-link").mapNotNull { element ->
                val link = fixUrlNull(element.attr("href")) ?: return@mapNotNull null
                // member
                if (link.contains("fast_download")) {
                    return@mapNotNull null
                }
                // cloudflare
                if (link.contains("slow_download")) {
                    return@mapNotNull null
                }
                // no idea why this is in the js dl link
                if (link.endsWith("/datasets")) {
                    return@mapNotNull null
                }
                extract(link, element.text())
            }) {
            posterUrl = document.selectFirst("main > div > div > div > div > div > img")?.attr("src")
            author = document.selectFirst("main > div > div > a")?.ownText()
            synopsis = document.selectFirst("main > div > div > div > div.mb-1")?.text()
        }
    }

    // ================================ JSON ================================
    data class AnnasArchiveRoot(
        /*@JsonProperty("///md5") var ___md5: ArrayList<String> = arrayListOf(),
        @JsonProperty("md5") var md5: String? = null,
        @JsonProperty("///lgrsnf_book") var ___lgrsnfBook: String? = null,
        @JsonProperty("lgrsnf_book") var lgrsnfBook: String? = null,
        @JsonProperty("///lgrsfic_book") var ___lgrsficBook: String? = null,
        @JsonProperty("lgrsfic_book") var lgrsficBook: String? = null,
        @JsonProperty("///lgli_file") var ___lgliFile: String? = null,
        @JsonProperty("lgli_file") var lgliFile: LgliFile? = LgliFile(),
        @JsonProperty("///zlib_book") var ___zlibBook: String? = null,
        @JsonProperty("zlib_book") var zlibBook: ZlibBook? = ZlibBook(),
        @JsonProperty("///aa_lgli_comics_2022_08_file") var ___aaLgliComics202208File: ArrayList<String> = arrayListOf(),
        @JsonProperty("aa_lgli_comics_2022_08_file") var aaLgliComics202208File: String? = null,
        @JsonProperty("///ipfs_infos") var ___ipfsInfos: String? = null,
        @JsonProperty("ipfs_infos") var ipfsInfos: ArrayList<String> = arrayListOf(),
        @JsonProperty("///file_unified_data") var ___fileUnifiedData: String? = null,
        @JsonProperty("///search_only_fields") var ___searchOnlyFields: String? = null,
        @JsonProperty("search_only_fields") var searchOnlyFields: SearchOnlyFields? = SearchOnlyFields(),
        @JsonProperty("///additional") var ___additional: String? = null,*/
        @JsonProperty("additional") var additional: Additional = Additional(),
        @JsonProperty("file_unified_data") var fileUnifiedData: FileUnifiedData? = FileUnifiedData(),
    )

    /*data class LgliFile(
        @JsonProperty("f_id") var fId: Int? = null,
        @JsonProperty("md5") var md5: String? = null,
        @JsonProperty("libgen_topic") var libgenTopic: String? = null,
        @JsonProperty("libgen_id") var libgenId: Int? = null,
        @JsonProperty("fiction_id") var fictionId: Int? = null,
        @JsonProperty("fiction_rus_id") var fictionRusId: Int? = null,
        @JsonProperty("comics_id") var comicsId: Int? = null,
        @JsonProperty("scimag_id") var scimagId: Int? = null,
        @JsonProperty("standarts_id") var standartsId: Int? = null,
        @JsonProperty("magz_id") var magzId: Int? = null,
        @JsonProperty("scimag_archive_path") var scimagArchivePath: String? = null
    )


    data class ZlibBook(
        @JsonProperty("zlibrary_id") var zlibraryId: Int? = null,
        @JsonProperty("md5") var md5: String? = null,
        @JsonProperty("md5_reported") var md5Reported: String? = null,
        @JsonProperty("filesize") var filesize: Int? = null,
        @JsonProperty("filesize_reported") var filesizeReported: Int? = null,
        @JsonProperty("in_libgen") var inLibgen: Int? = null,
        @JsonProperty("pilimi_torrent") var pilimiTorrent: String? = null
    )



    data class SearchOnlyFields(
        @JsonProperty("search_text") var searchText: String? = null,
        @JsonProperty("score_base") var scoreBase: Int? = null
    )*/

    data class FileUnifiedData(
        @JsonProperty("original_filename_best") var originalFilenameBest: String? = null,
        @JsonProperty("original_filename_additional") var originalFilenameAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("original_filename_best_name_only") var originalFilenameBestNameOnly: String? = null,
        @JsonProperty("cover_url_best") var coverUrlBest: String? = null,
        @JsonProperty("cover_url_additional") var coverUrlAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("extension_best") var extensionBest: String? = null,
        @JsonProperty("extension_additional") var extensionAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("filesize_best") var filesizeBest: Int? = null,
        @JsonProperty("filesize_additional") var filesizeAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("title_best") var titleBest: String? = null,
        @JsonProperty("title_additional") var titleAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("author_best") var authorBest: String? = null,
        @JsonProperty("author_additional") var authorAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("publisher_best") var publisherBest: String? = null,
        @JsonProperty("publisher_additional") var publisherAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("edition_varia_best") var editionVariaBest: String? = null,
        @JsonProperty("edition_varia_additional") var editionVariaAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("year_best") var yearBest: String? = null,
        @JsonProperty("year_additional") var yearAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("comments_best") var commentsBest: String? = null,
        @JsonProperty("comments_additional") var commentsAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("stripped_description_best") var strippedDescriptionBest: String? = null,
        @JsonProperty("stripped_description_additional") var strippedDescriptionAdditional: ArrayList<String> = arrayListOf(),
        @JsonProperty("language_codes") var languageCodes: ArrayList<String> = arrayListOf(),
        @JsonProperty("most_likely_language_code") var mostLikelyLanguageCode: String? = null,
        @JsonProperty("sanitized_isbns") var sanitizedIsbns: ArrayList<String> = arrayListOf(),
        @JsonProperty("asin_multiple") var asinMultiple: ArrayList<String> = arrayListOf(),
        @JsonProperty("googlebookid_multiple") var googlebookidMultiple: ArrayList<String> = arrayListOf(),
        @JsonProperty("openlibraryid_multiple") var openlibraryidMultiple: ArrayList<String> = arrayListOf(),
        @JsonProperty("doi_multiple") var doiMultiple: ArrayList<String> = arrayListOf(),
        @JsonProperty("problems") var problems: ArrayList<String> = arrayListOf(),
        @JsonProperty("content_type") var contentType: String? = null,
        @JsonProperty("has_aa_downloads") var hasAaDownloads: Int? = null,
        @JsonProperty("has_aa_exclusive_downloads") var hasAaExclusiveDownloads: Int? = null
    )

    data class TopBox(
        @JsonProperty("meta_information") var metaInformation: ArrayList<String> = arrayListOf(),
        @JsonProperty("cover_url") var coverUrl: String? = null,
        @JsonProperty("top_row") var topRow: String? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("publisher_and_edition") var publisherAndEdition: String? = null,
        @JsonProperty("author") var author: String? = null,
        @JsonProperty("description") var description: String? = null
    )


    data class Additional(
        //@JsonProperty("most_likely_language_name") var mostLikelyLanguageName: String? = null,
        @JsonProperty("top_box") var topBox: TopBox = TopBox(),
        @JsonProperty("filename") var filename: String? = null,
        //@JsonProperty("isbns_rich") var isbnsRich: ArrayList<ArrayList<String>> = arrayListOf(),
        @JsonProperty("download_urls") var downloadUrls: ArrayList<ArrayList<String>> = arrayListOf(),
        //@JsonProperty("has_aa_downloads") var hasAaDownloads: Int? = null,
        //@JsonProperty("has_aa_exclusive_downloads") var hasAaExclusiveDownloads: Int? = null
    )
}