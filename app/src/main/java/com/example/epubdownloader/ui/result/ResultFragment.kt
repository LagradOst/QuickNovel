package com.example.epubdownloader.ui.result

import android.content.Context
import android.opengl.Visibility
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.size
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import kotlinx.android.synthetic.main.fragment_result.*
import kotlin.concurrent.thread
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.example.epubdownloader.*
import jp.wasabeef.glide.transformations.BlurTransformation
import java.text.StringCharacterIterator

import java.text.CharacterIterator
import android.util.DisplayMetrics
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.marginTop
import androidx.core.view.setPadding
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.epubdownloader.BookDownloader.turnToEpub
import com.example.epubdownloader.ui.download.DownloadFragment
import kotlinx.android.synthetic.main.activity_main.*


class ResultFragment : Fragment() {
    fun newInstance(url: String) =
        ResultFragment().apply {
            arguments = Bundle().apply {
                //println(data)
                putString("url", url)
            }
        }


    var resultUrl = ""

    fun humanReadableByteCountSI(bytes: Int): String {
        var bytes = bytes
        if (-1000 < bytes && bytes < 1000) {
            return "$bytes"
        }
        val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
        while (bytes <= -999950 || bytes >= 999950) {
            bytes /= 1000
            ci.next()
        }
        return String.format("%.1f%c", bytes / 1000.0, ci.current()).replace(',', '.')
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        /*activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )*/

        return inflater.inflate(R.layout.fragment_result, container, false)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.getString("url")?.let {
            resultUrl = it
        }
    }

    var load: LoadResponse? = null
    var localId = 0
    var generateEpub = false
    var lastProgress: Int = 0
    fun updateGenerateBtt(progress: Int?) {
        if (load != null) {
            generateEpub = DataStore.getKey(DOWNLOAD_EPUB_SIZE, localId.toString(), 0) != (progress ?: lastProgress)
            if (progress != null) lastProgress = progress
            if (result_download_generate_epub != null) {
                if (generateEpub) {
                    result_download_generate_epub.setIconResource(R.drawable.ic_baseline_create_24)
                    result_download_generate_epub.text = "Generate Epub"
                } else {
                    result_download_generate_epub.setIconResource(R.drawable.ic_baseline_menu_book_24)
                    result_download_generate_epub.text = "Read Epub"
                }
            }
        }
    }

    fun updateDownloadInfo(info: BookDownloader.DownloadNotification) {
        if (localId != info.id) return
        activity?.runOnUiThread {
            result_download_progress_text.text = "${info.progress}/${info.total}"
            result_download_progress_bar.progress = info.progress * 100 / info.total
            result_download_progress_text_eta.text = info.ETA
            updateDownloadButtons(info.progress, info.total, info.state)
            updateGenerateBtt(info.progress)
        }
    }

    fun updateDownloadButtons(progress: Int, total: Int, state: BookDownloader.DownloadType) {
        val ePubGeneration = progress > 0
        if (result_download_generate_epub.isEnabled != ePubGeneration) {
            result_download_generate_epub.isEnabled = ePubGeneration
            result_download_generate_epub.alpha = if (ePubGeneration) 1f else 0.5f
        }

        val download = progress < total
        if (result_download_btt.isEnabled != download) {
            result_download_btt.isEnabled = download
            result_download_btt.alpha = if (download) 1f else 0.5f
        }


        result_download_btt.text = when (state) {
            BookDownloader.DownloadType.IsDone -> "Downloaded"
            BookDownloader.DownloadType.IsDownloading -> "Pause"
            BookDownloader.DownloadType.IsPaused -> "Resume"
            BookDownloader.DownloadType.IsFailed -> "Re-Download"
            BookDownloader.DownloadType.IsStopped -> "Download"
        }

        result_download_btt.iconSize = 30.toPx
        result_download_btt.setIconResource(when (state) {
            BookDownloader.DownloadType.IsDownloading -> R.drawable.netflix_pause
            BookDownloader.DownloadType.IsPaused -> R.drawable.netflix_play
            else -> R.drawable.netflix_download
        })
    }

    override fun onDestroy() {
        MainActivity.isInResults = false
        BookDownloader.downloadNotification -= ::updateDownloadInfo
        MainActivity.activity.window.navigationBarColor =
            ResourcesCompat.getColor(resources, R.color.darkBackground, null)
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        MainActivity.isInResults = true

        MainActivity.activity.window.navigationBarColor =
            ResourcesCompat.getColor(resources, R.color.grayBackground, null)

        result_holder.visibility = View.GONE
        result_loading.visibility = View.VISIBLE
        //  result_mainscroll.scrollTo(100.toPx, 0)
        result_mainscroll.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            val scrollFade = maxOf(0f, 1 - scrollY / 170.toPx.toFloat())
            result_info_header.alpha = scrollFade
            result_info_header.scaleX = 0.95f + scrollFade * 0.05f
            result_info_header.scaleY = 0.95f + scrollFade * 0.05f

            val crossFade = maxOf(0f, 1 - scrollY / 140.toPx.toFloat())
            result_back.alpha = crossFade
            result_back.isEnabled = crossFade > 0
        }

        // TRANSPARENT STATUSBAR
        result_info_header.setPadding(0, MainActivity.statusBarHeight, 0, 0)
        //result_back.setPadding(0, MainActivity.statusBarHeight, 0, 0)

        val parameter = result_empty_view.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(parameter.leftMargin,
            parameter.topMargin + MainActivity.statusBarHeight,
            parameter.rightMargin,
            parameter.bottomMargin)
        result_empty_view.layoutParams = parameter

        val back_parameter = result_back.layoutParams as FrameLayout.LayoutParams
        back_parameter.setMargins(back_parameter.leftMargin,
            back_parameter.topMargin + MainActivity.statusBarHeight,
            back_parameter.rightMargin,
            back_parameter.bottomMargin)
        result_back.layoutParams = back_parameter

        thread {
            val res = MainActivity.api.load(resultUrl)
            load = res
            activity?.runOnUiThread {
                if (res == null) {
                    Toast.makeText(context, "Error loading", Toast.LENGTH_SHORT).show()
                } else {

                    MainActivity.activity.window.navigationBarColor =
                        ResourcesCompat.getColor(resources, R.color.bitDarkerGrayBackground, null)

                    result_holder.visibility = View.VISIBLE
                    result_loading.visibility = View.GONE

                    result_title.text = res.name
                    result_author.text = res.author

                    if (res.rating != null) {
                        result_rating.text = (res.rating.toFloat() / 20f).toString() + "★" //TODO FIX SETTINGS
                        if (res.peopleVoted != null) {
                            result_rating_voted_count.text = "${res.peopleVoted} Votes"
                        }
                    }

                    if (res.views != null) {
                        result_views.text = humanReadableByteCountSI(res.views)
                    }
                    if (res.Synopsis != null) {
                        result_synopsis_text.text = res.Synopsis
                    }
                    val last = res.data.last()
                    result_total_chapters.text = "Latest: " + last.name //+ " " + last.dateOfRelease

                    /*
                    if(res.tags != null) {
                        var text = res.tags.joinToString(" • ")
                        result_tags.text = text
                    }*/

                    localId = BookDownloader.generateId(res, MainActivity.api)
                    DataStore.setKey(DOWNLOAD_TOTAL, localId.toString(), res.data.size)

                    val start = BookDownloader.downloadInfo(res.author, res.name, res.data.size, MainActivity.api.name)
                    result_download_progress_text_eta.text = ""
                    if (start != null) {
                        updateGenerateBtt(start.progress)
                        result_download_progress_text.text = "${start.progress}/${start.total}"
                        result_download_progress_bar.progress = start.progress * 100 / start.total
                        val state =
                            if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped
                        updateDownloadButtons(start.progress, start.total, state!!)
                    } else {
                        result_download_progress_bar.progress = 0
                    }

                    val glideUrl =
                        GlideUrl(res.posterUrl)
                    context!!.let {
                        Glide.with(it)
                            .load(glideUrl)
                            .into(result_poster)

                        Glide.with(it)
                            .load(glideUrl)
                            .apply(bitmapTransform(BlurTransformation(100, 3)))
                            .into(result_poster_blur)
                    }

                    result_download_card.post {
                        val displayMetrics = context!!.resources.displayMetrics
                        val height = result_download_card.height
                        result_scroll_padding.setPadding(
                            result_scroll_padding.paddingLeft,
                            result_scroll_padding.paddingTop,
                            result_scroll_padding.paddingRight,
                            displayMetrics.heightPixels - height) // - MainActivity.activity.nav_view.height
                    }
                }
            }
        }

        BookDownloader.downloadNotification += ::updateDownloadInfo

        result_download_btt.setOnClickListener {
            if (load == null || localId == 0) return@setOnClickListener
            val l = load!!
            DataStore.setKey(DOWNLOAD_FOLDER, BookDownloader.generateId(l, MainActivity.api).toString(),
                DownloadFragment.DownloadData(resultUrl,
                    l.name,
                    l.author,
                    l.posterUrl,
                    l.rating,
                    l.peopleVoted,
                    l.views,
                    l.Synopsis,
                    l.tags,
                    MainActivity.api.name
                ))

            thread {
                when (if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped) {
                    BookDownloader.DownloadType.IsFailed -> BookDownloader.download(load!!, MainActivity.api)
                    BookDownloader.DownloadType.IsStopped -> BookDownloader.download(load!!, MainActivity.api)
                    BookDownloader.DownloadType.IsDownloading -> BookDownloader.updateDownload(localId,
                        BookDownloader.DownloadType.IsPaused)
                    BookDownloader.DownloadType.IsPaused -> BookDownloader.updateDownload(localId,
                        BookDownloader.DownloadType.IsDownloading)
                    else -> println("ERROR")
                }
            }
        }

        result_back.setOnClickListener {
            MainActivity.backPressed()
        }

        result_download_generate_epub.setOnClickListener {
            if (generateEpub) {
                if (load != null) {
                    thread {
                        val l = load!!
                        val done = turnToEpub(l.author, l.name, MainActivity.api.name)
                        MainActivity.activity.runOnUiThread {
                            updateGenerateBtt(null)
                            if (done) {
                                Toast.makeText(context, "Created ${l.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Error creating the Epub", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } else {
                if (load != null) {
                    BookDownloader.openEpub(load!!.name)
                }
            }
        }
    }
}
