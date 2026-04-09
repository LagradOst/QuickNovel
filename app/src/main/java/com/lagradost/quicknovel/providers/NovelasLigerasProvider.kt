package com.lagradost.quicknovel.providers

import android.net.Uri
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse

class NovelasLigerasProvider : MainAPI() {
    override val name = "Nova"
    override val mainUrl = "https://novelasligeras.net"
    override val iconId = R.drawable.icon_novelasligeras
    override val iconBackgroundId = R.color.white
    override val usesCloudFlareKiller = true
    override val hasMainPage = true
    override val lang = "es"

    override val orderBys = listOf(
        "Popularidad" to "popularity",
        "Puntuación media" to "rating",
        "Últimos" to "date"
    )

    override val mainCategories = listOf(
        "Cualquiera" to "",
        "Cancelado" to "18",
        "Completado" to "407",
        "En Proceso" to "16",
        "Pausado" to "17"
    )
    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/index.php/lista-de-novela-ligera-novela-web/page/$page/?orderby=$orderBy${if(mainCategory.isNullOrEmpty())"" else "&ixwpst[pa_estado][]=$mainCategory"}&wps-title=1&wps-excerpt=1&wps-content=1&wps-categories=1&wps-attributes=1&wps-tags=1&wps-sku=1&ixwpsf[taxonomy][product_cat][show]=set&ixwpsf[taxonomy][product_cat][multiple]=0&ixwpsf[taxonomy][product_cat][filter]=1&ixwpsf[taxonomy][pa_estado][show]=set&ixwpsf[taxonomy][pa_estado][multiple]=0&ixwpsf[taxonomy][pa_estado][filter]=1&ixwpsf[taxonomy][pa_estado][op]=or&ixwpsf[taxonomy][pa_tipo][show]=set&ixwpsf[taxonomy][pa_tipo][multiple]=0&ixwpsf[taxonomy][pa_tipo][filter]=1&ixwpsf[taxonomy][pa_tipo][op]=or&ixwpsf[taxonomy][pa_pais][show]=set&ixwpsf[taxonomy][pa_pais][multiple]=0&ixwpsf[taxonomy][pa_pais][filter]=1&ixwpsf[taxonomy][pa_pais][op]=or"
        val document = app.get(url).document
        println(document)
        val returnValue =
            document.select("div#content > div.products > div > div > article").mapNotNull { card ->
                val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val title = card.selectFirst("h4")?.text() ?: return@mapNotNull null

                newSearchResponse(
                    name = title,
                    url = href
                ) {
                    posterUrl = card.selectFirst("img")?.attr("data-src")
                }
            }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val infoDiv = document.select("div.summary.entry-summary")
        val title = infoDiv.selectFirst("h1")?.text() ?: ""

        val synopsis = infoDiv.select("div.woocommerce-product-details__short-description > p")
            .joinToString("\n\n") { it.text() }

        val chapters = document.select("div.wpb_tour.wpb_content_element.tab-style-one .wpb_tabs_nav")
            .flatMapIndexed { volIndex, volNav ->
                volNav.select("li").flatMap { tabLi ->
                    val tabId = tabLi.selectFirst("a")?.attr("href")?.removePrefix("#") ?: ""
                    val selector = "div#$tabId"
                    val chapterDiv = document.selectFirst(selector)
                    val chapterTitle = tabLi.text()
                    chapterDiv?.select("div.wf-cell")?.mapNotNull { page ->
                        val link = page.selectFirst("a") ?: return@mapNotNull null
                        val chapterPart = link.text()
                        val chapterUrl = link.attr("href")

                        newChapterData(
                            name = "Vol. ${volIndex + 1} - $chapterTitle - $chapterPart",
                            url = chapterUrl
                        )
                    } ?: emptyList()
                }
            }

        return newStreamResponse(title, fixUrl(url), chapters) {
            this.posterUrl =
                document.selectFirst("div.woocommerce-product-gallery__image img")?.attr("src")
            this.synopsis = synopsis
            this.tags = infoDiv.select("div.product_meta span.tagged_as a").map {
                it.text().trim()
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val reader =
            document.selectFirst("div.wpb_text_column.wpb_content_element > div") ?: return null
        reader.select("h1, h2, a.track-ad").remove()
        return reader.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${Uri.encode(query)}&post_type=product"
        val document = app.get(url).document
        val searchResult =
            document.select("div#content > div.products > div > div > article").mapNotNull { card ->
                val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val title = card.selectFirst("h4")?.text() ?: return@mapNotNull null
                newSearchResponse(
                    name = title,
                    url = href
                ) {
                    posterUrl = card.selectFirst("img")?.attr("data-src")
                }
            }

        if (searchResult.isEmpty()) {
            val title = document.select("div.summary.entry-summary").selectFirst("h1")?.text()
            val canonicalUrl = document.selectFirst("link[rel=canonical]")?.attr("href")
            if (title != null && canonicalUrl != null) {
                return listOf(
                    newSearchResponse(
                        name = title,
                        url = canonicalUrl
                    ) {
                        posterUrl =
                            document.selectFirst("div.woocommerce-product-gallery__image img")
                                ?.attr("src")
                    }
                )
            }
        }
        return searchResult
    }
}
