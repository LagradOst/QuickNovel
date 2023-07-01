package com.lagradost.quicknovel.extractors

import androidx.annotation.WorkerThread
import com.lagradost.quicknovel.DownloadExtractLink
import com.lagradost.quicknovel.DownloadLink
import com.lagradost.quicknovel.DownloadLinkType
import com.lagradost.quicknovel.get

class LibgenLi : ExtractorApi() {
    override val mainUrl = "https://libgen.li"
    override val name = "LibgenLi"
    override val requiresReferer = false

    @WorkerThread
    override suspend fun getUrl(link : DownloadExtractLink): List<DownloadLinkType>? {
        val document = link.get().document
        val url = fixUrlNull(document.selectFirst("tbody>tr>td>a")?.attr("href"))
        return listOf(
            DownloadLink(
                url = url ?: return null,
                name = name,
                kbPerSec = 200
            )
        )
    }
}