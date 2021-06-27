package com.lagradost.quicknovel.util

import android.app.Activity
import androidx.preference.PreferenceManager
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
            WuxiaWorldSiteProvider(),
            ReadLightNovelProvider(),
            BoxNovelProvider(),
            ComrademaoProvider(),
        )

        val allApi: AllProvider = AllProvider()

        fun getApiFromName(name: String): MainAPI {
            for (a in apis) {
                if (a.name == name) {
                    return a
                }
            }
            return apis[1]
        }

        fun Activity.getApiSettings(): HashSet<String> {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            return settingsManager.getStringSet(getString(R.string.search_providers_list_key),
                setOf(apis[defProvider].name))?.toHashSet() ?: hashSetOf(apis[defProvider].name)
        }
    }
}