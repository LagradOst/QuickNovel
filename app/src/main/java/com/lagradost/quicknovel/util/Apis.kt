package com.lagradost.quicknovel.util

import android.app.Activity
import android.content.Context
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.providers.*

const val defProvider = 3

class Apis {
    companion object {
        val apis: Array<MainAPI> = arrayOf(
            //AllProvider(),
            NovelPassionProvider(),
            BestLightNovelProvider(),
            WuxiaWorldOnlineProvider(),
            RoyalRoadProvider(),
            FreewebnovelProvider(),
            AzynovelProvider(),
            ReadfromnetProvider(),
            AllNovelProvider(),
            RanobesProvider(),
            NovelFullProvider(),
            //MNovelFreeProvider(), // same as NovelFullVipProvider
            NovelFullVipProvider(),
            EngNovelProvider(),


            // chapter captcha
//            WuxiaWorldSiteProvider(),
            ReadLightNovelProvider(),
            BoxNovelProvider(),
            ComrademaoProvider(),
            LightNovelPubProvider(),
            ReadNovelFullProvider(),
            ScribblehubProvider(),
            KolNovelProvider(),
            RewayatArProvider(),
            ReadAnyBookProvider(),
            MeioNovelProvider(),
            MoreNovelProvider(),
            IndoWebNovelProvider(),
            SakuraNovelProvider(),
        )

        fun getApiFromName(name: String): APIRepository {
            return getApiFromNameOrNull(name) ?: APIRepository(apis[1])
        }

        private fun getApiFromNameNull(apiName: String?): MainAPI? {
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
            if(name == RedditProvider().name) return APIRepository(RedditProvider())
            return null
        }

        fun printProviders() {
            var str = ""
            for (api in apis) {
                str += "- ${api.mainUrl}\n"
            }
            println(str)
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
