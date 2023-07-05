package com.lagradost.quicknovel.providers

import androidx.preference.PreferenceManager
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.lagradost.nicehttp.Requests
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.mvvm.debugAssert
import com.lagradost.quicknovel.mvvm.debugException
import com.lagradost.quicknovel.mvvm.debugWarning
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.collections.ArrayList

class ArchiveOfOurOwnProvider : MainAPI() {
    private val cookieJar  = PersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(context))
    private val app = Requests(MainActivity.app.baseClient.newBuilder().cookieJar(cookieJar).build())
    override val name = "Archive of Our Own"
    override val mainUrl = "https://archiveofourown.org"

    override val hasMainPage = true

    override val rateLimitTime: Long = 50

    override val iconId = R.drawable.ic_archive_of_our_own

    override val iconBackgroundId = R.color.archiveOfOurOwnColor

    override val orderBys = listOf(
        Pair("Latest", "latest"),
    )


    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        tryLogIn()
        val url =
            "$mainUrl/works"
        if (page > 1) return HeadMainPageResponse(
            url,
            ArrayList()
        ) // Latest ONLY HAS 1 PAGE

        val response = app.get(url)

        val document = response.document
        val works = document.select("li.work")
        if (works.size <= 0) return HeadMainPageResponse(url, ArrayList())

        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in works) {
            val workLink = h?.selectFirst("div.header.module > h4.heading > a")
            if (workLink == null){
                debugWarning { "Ao3 work has no actual work?" }
                continue
            }
            val name = workLink.text()
            val url = workLink.attr("href")

            returnValue.add(
                SearchResponse(
                    name,
                    fixUrl(url),
                    apiName = this.name
                )
            )
        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        tryLogIn()
        val response = app.get("$mainUrl/works/search?work_search[query]=$query")

        val document = response.document
        val works = document.select("li.work")
        if (works.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in works) {
            val workLink = h?.selectFirst("div.header.module > h4.heading > a")
            if (workLink == null){
                debugWarning { "Ao3 work has no actual work?" }
                continue
            }
            val name = workLink.text()
            val url = workLink.attr("href")

            val authorLink = h.selectFirst("div.header.module > h4.heading > a[rel=\"author\"]")
            if (authorLink == null){
                debugWarning { "Ao3 work has no actual author?" }
                continue
            }
            val author = authorLink.attr("href")

            returnValue.add(
                SearchResponse(
                    name,
                    fixUrl(url),
                    fixUrlNull(author),
                    apiName = this.name
                )
            )
        }
        return returnValue
    }

    override suspend fun load(url: String): LoadResponse? {
        tryLogIn()
        val response = app.get("$url/?view_adult=true")

        val document = response.document

        val name = document.selectFirst("h2.title.heading")?.text().toString()
        val author = document.selectFirst("h3.byline.heading > a[rel=\"author\"]")
        val peopleVoted = document.selectFirst("dd.kudos")?.text()?.replace(",","")?.toInt()
        val views = document.selectFirst("dd.hits")?.text()?.replace(",","")?.toInt()
        val synopsis = document.selectFirst("div.summary.module > blockquote.userstuff")
            ?.children()?.joinToString("\n", transform = Element::text)

        val tags = document.select("a.tag").map(org.jsoup.nodes.Element::text)

        val chaptersResponse = app.get("$url/navigate?view_adult=true")
        val chapterDocument = Jsoup.parse(chaptersResponse.text)

        val chapters = chapterDocument.selectFirst("ol.chapter.index.group[role=\"navigation\"]")

        val data = chapters?.children()?.map {
            val link = it.child(0)
            val date = it.child(1)
            ChapterData(
                name = link.text().toString(),
                url = fixUrl(link.attr("href").toString()),
                dateOfRelease = date.text()
            )
        }


        return StreamResponse(
            url,
            name,
            data ?: ArrayList(),
            author = author?.text(),
            peopleVoted.toString(),
            views,
            synopsis = synopsis,
            tags = tags
        )
    }

    override suspend fun loadHtml(url: String): String? {
        tryLogIn()
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div.chapter")?.html()
    }

    private var hasLoggedIn = false
    private suspend fun tryLogIn() {

        if (hasLoggedIn) return

        //Don't try to log in if we don't have email and password
        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(context!!.applicationContext)

        val ao3Email = preferenceManager.getString(context?.getString(R.string.ao3_email_key),"")!!
        val ao3Password = preferenceManager.getString(context?.getString(R.string.ao3_password_key),"")!!

        if (ao3Email == "" || ao3Password == ""){
            return
        }

        val response = app.get("$mainUrl/works/new", allowRedirects = false)
        if(response.code == 200){
            hasLoggedIn = true
            return
        }
        if(response.code != 302) {
            debugException { "AO3 isn't redirecting us to login page for some reason. If issue persists please report to extension creator." }
            return
        }

        val loginPageResponse = app.get("$mainUrl/users/login")

        val authenticityToken =
            loginPageResponse.document.selectFirst("form.new_user#new_user")
            ?.selectFirst("input[type=hidden][name=authenticity_token]")
                ?.attr("value")!!


        val loginResponse = app.post("$mainUrl/users/login", requestBody = FormBody.Builder()
            .add("user[login]", ao3Email)
            .add("user[password]", ao3Password)
            .add("user[remember_me]", "1")
            .add("commit", "Log in")
            .add("authenticity_token", authenticityToken)
            .build()
            )

        /*data= mapOf(
            Pair("user[login]", ao3Email),
            Pair("user[password]", ao3Password),
            Pair("user[remember_me]", "1"),
            Pair("commit", "Log in"),
            Pair("authenticity_token", authenticityToken),
        )*/

        if(loginResponse.okhttpResponse.priorResponse == null){
            if(loginResponse.text.contains("The password or user name you entered doesn't match our records.")){
                if (!preferenceManager.edit().putString(context?.getString(R.string.ao3_password_key), "").commit()){
                    debugException { "Something went wrong clearing your password!" }
                }
                debugWarning { "Username or Password incorrect! Password's been cleared" }
            }else{
                debugException { "Something went wrong logging you in." }
            }

        }else{
            debugAssert( {response.url.startsWith("$mainUrl/users") && response.url != "$mainUrl/users/login"},
                {"Expected to be sent to $mainUrl/users/yourusername was instead sent to ${response.url}"})
        }

    }
}
