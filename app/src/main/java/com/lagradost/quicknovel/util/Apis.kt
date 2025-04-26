package com.lagradost.quicknovel.util

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.providers.AllNovelProvider
import com.lagradost.quicknovel.providers.AnnasArchive
import com.lagradost.quicknovel.providers.BestLightNovelProvider
import com.lagradost.quicknovel.providers.FreewebnovelProvider
import com.lagradost.quicknovel.providers.GraycityProvider
import com.lagradost.quicknovel.providers.HiraethTranslationProvider
import com.lagradost.quicknovel.providers.IndoWebNovelProvider
import com.lagradost.quicknovel.providers.KolNovelProvider
import com.lagradost.quicknovel.providers.LibReadProvider
import com.lagradost.quicknovel.providers.MeioNovelProvider
import com.lagradost.quicknovel.providers.MoreNovelProvider
import com.lagradost.quicknovel.providers.MtlNovelProvider
import com.lagradost.quicknovel.providers.NovelBinProvider
import com.lagradost.quicknovel.providers.NovelFullProvider
import com.lagradost.quicknovel.providers.NovelsOnlineProvider
import com.lagradost.quicknovel.providers.PawReadProver
import com.lagradost.quicknovel.providers.ReadNovelFullProvider
import com.lagradost.quicknovel.providers.ReadfromnetProvider
import com.lagradost.quicknovel.providers.RedditProvider
import com.lagradost.quicknovel.providers.RoyalRoadProvider
import com.lagradost.quicknovel.providers.SakuraNovelProvider
import com.lagradost.quicknovel.providers.ScribblehubProvider
import com.lagradost.quicknovel.providers.WtrLabProvider
import com.lagradost.quicknovel.util.Coroutines.ioSafe

class Apis {
    companion object {
        val apis: List<MainAPI> = arrayOf(
            //AllProvider(),
//            NovelPassionProvider(), // Site gone
            BestLightNovelProvider(),
//            WuxiaWorldOnlineProvider(), // Site does not work
            RoyalRoadProvider(),
            HiraethTranslationProvider(),
            LibReadProvider(),
            FreewebnovelProvider(),
            //AzynovelProvider(), // dont exist anymore
            ReadfromnetProvider(),
            AllNovelProvider(),
            //RanobesProvider(), // custom capcha
            NovelFullProvider(),
            NovelBinProvider(),
            //MNovelFreeProvider(), // same as NovelFullVipProvider
            //EngNovelProvider(),
            NovelsOnlineProvider(),
            //EfremnetProvider(), // domain is expired
            GraycityProvider(),
            MtlNovelProvider(),

            AnnasArchive(),

            // chapter captcha
//            WuxiaWorldSiteProvider(),
            //ReadLightNovelProvider(), // NOT WORKING?
            //BoxNovelProvider(),
            // ComrademaoProvider(), // domain sold/down?
//            LightNovelPubProvider(), // Got cloudflare, but probably bypassable
            ReadNovelFullProvider(),
            ScribblehubProvider(),
            KolNovelProvider(),
            //RewayatArProvider(), // removed url
//            ReadAnyBookProvider(), // Books locked behind login
            MeioNovelProvider(),
            MoreNovelProvider(), // cloudflare?
            IndoWebNovelProvider(),
            SakuraNovelProvider(), // cloudflare?
            // WattpadProvider(), // they have randomized the css classes
            WtrLabProvider(),
            PawReadProver()
        ).sortedBy { it.name }

        fun getApiFromName(name: String): APIRepository {
            return getApiFromNameOrNull(name) ?: APIRepository(apis[1])
        }

        fun getApiFromNameNull(apiName: String?): MainAPI? {
            for (api in apis) {
                if (apiName == api.name)
                    return api
            }
            return null
        }

        fun getApiFromNameOrNull(name: String): APIRepository? {
            for (a in apis) {
                if (a.name == name) {
                    return APIRepository(a)
                }
            }
            if (name == RedditProvider().name) return APIRepository(RedditProvider())
            return null
        }

        fun printProviders() {
            /*
            var str = ""
            for (api in apis) {
                str += "- ${api.mainUrl}\n"
            }
            println(str)

            var str2 = ""
            for (api in apis) {
                val url = api.mainUrl.toUri()
                str2 += "                <data\n" +
                        "                        android:scheme=\"${url.scheme}\"\n" +
                        "                        android:host=\"${url.host}\"\n" +
                        "                        android:pathPrefix=\"/\" />"
            }
            println(str2)

            testProviders()*/
        }

        fun testProviders() = ioSafe {
            val TAG = "APITEST"
            apis.amap { api ->
                try {
                    var result: List<SearchResponse>? = null
                    for (x in arrayOf("my", "hello", "over", "guy")) {
                        result = api.search(x)
                        if (!result.isNullOrEmpty()) {
                            break
                        }
                    }

                    assert(!result.isNullOrEmpty()) {
                        "Invalid search response from ${api.name}"
                    }

                    val loadResult = api.load(result!![0].url)
                    assert(loadResult != null) {
                        "Invalid load response from ${api.name}"
                    }
                    if (loadResult is StreamResponse) {
                        assert(loadResult.data.isNotEmpty()) {
                            "Invalid StreamResponse (no chapters) from ${api.name}"
                        }
                        val html = api.loadHtml(loadResult.data[0].url)
                        assert(html != null) {
                            "Invalid html (null) from ${api.name}"
                        }
                    }

                    if (api.hasMainPage) {
                        val response = api.loadMainPage(
                            1,
                            api.mainCategories.firstOrNull()?.second,
                            api.orderBys.firstOrNull()?.second,
                            api.tags.firstOrNull()?.second
                        )
                        assert(response.list.isNotEmpty()) {
                            "Invalid (empty) loadMainPage list from ${api.name}"
                        }
                    }

                    Log.v(TAG, "Valid response from ${api.name}")
                } catch (t: Throwable) {
                    Log.e(TAG, "Invalid response from ${api.name}, got $t")
                }
            }
        }

        fun Context.getApiSettings(): HashSet<String> {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            val hashSet = HashSet<String>()
            hashSet.addAll(apis.map { it.name })

            val set = settingsManager.getStringSet(
                this.getString(R.string.search_providers_list_key),
                hashSet
            )?.toHashSet() ?: hashSet

            val activeLangs = getApiProviderLangSettings()
            val list = HashSet<String>()
            for (name in set) {
                val api = getApiFromNameNull(name) ?: continue
                if (activeLangs.contains(api.lang)) {
                    list.add(name)
                }
            }
            if (list.isEmpty()) return hashSet
            return list
        }

        fun getApiProviderLangSettings(): HashSet<String> {
            return activity?.getApiProviderLangSettings() ?: hashSetOf()
        }

        fun Context.getApiProviderLangSettings(): HashSet<String> {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            val hashSet = HashSet<String>()
            hashSet.add("en") // def is only en
            val list = settingsManager.getStringSet(
                this.getString(R.string.provider_lang_key),
                hashSet.toMutableSet()
            )

            if (list.isNullOrEmpty()) return hashSet
            return list.toHashSet()
        }
    }
}
