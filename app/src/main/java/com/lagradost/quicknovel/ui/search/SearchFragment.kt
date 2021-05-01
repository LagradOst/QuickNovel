package com.lagradost.quicknovel.ui.search

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_search.*
import kotlin.concurrent.thread

import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.getApiSettings
import com.lagradost.quicknovel.ui.download.DownloadFragment


class SearchFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    companion object {
        val searchDowloads = ArrayList<DownloadFragment.DownloadData>()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val parameter = top_padding.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(parameter.leftMargin,
            parameter.topMargin + MainActivity.statusBarHeight,
            parameter.rightMargin,
            parameter.bottomMargin)
        top_padding.layoutParams = parameter

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            ResAdapter(
                it,
                ArrayList(),
                cardSpace,
            )
        }

        cardSpace.adapter = adapter
        cardSpace.layoutManager = GridLayoutManager(context, 1)
        search_loading_bar.alpha = 0f
        val search_exit_icon = main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        val search_mag_icon = main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        search_mag_icon.scaleX = 0.65f
        search_mag_icon.scaleY = 0.65f

        search_filter.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this.context!!)
            //val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
            val apiNamesSetting = getApiSettings()

            val apiNames = MainActivity.apis.map { it.name }

            builder.setMultiChoiceItems(apiNames.toTypedArray(),
                apiNames.map { a -> apiNamesSetting.contains(a) }.toBooleanArray(),
                DialogInterface.OnMultiChoiceClickListener { _, position: Int, checked: Boolean ->
                    val apiNamesSettingLocal = getApiSettings()
                    val settingsManagerLocal = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
                    if (checked) {
                        apiNamesSettingLocal.add(apiNames[position])
                    } else {
                        apiNamesSettingLocal.remove(apiNames[position])
                    }

                    val edit = settingsManagerLocal.edit()
                    edit.putStringSet(getString(R.string.search_providers_list_key),
                        apiNames.filter { a -> apiNamesSettingLocal.contains(a) }.toSet())
                    edit.apply()
                    MainActivity.allApi.providersActive = apiNamesSettingLocal
                })
            builder.setTitle("Search Providers")
            builder.setNegativeButton("Cancel") { _, _ -> }
            builder.show()
        }

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                search_exit_icon.alpha = 0f
                search_loading_bar.alpha = 1f
                thread {
                    val data = MainActivity.allApi.search(query)//MainActivity.activeAPI.search(query)
                    activity?.runOnUiThread {
                        if (data == null) {
                            Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                        } else {
                            (cardSpace.adapter as ResAdapter).cardList = data
                            (cardSpace.adapter as ResAdapter).notifyDataSetChanged()
                        }
                        search_exit_icon.alpha = 1f
                        search_loading_bar.alpha = 0f
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return true
            }
        })

        thread {
            searchDowloads.clear()
            val keys = DataStore.getKeys(DOWNLOAD_FOLDER)
            for (k in keys) {
                val data = DataStore.getKey<DownloadFragment.DownloadData>(k)
                if (data != null) {
                    val info = BookDownloader.downloadInfo(data.author, data.name, 100000, data.apiName)
                    if(info != null && info.progress > 0) {
                        searchDowloads.add(data)
                    }
                }
            }
        }
    }
}