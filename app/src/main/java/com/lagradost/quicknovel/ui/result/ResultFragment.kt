package com.lagradost.quicknovel.ui.result

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getColorStateList
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.google.android.material.button.MaterialButton
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BookDownloader.turnToEpub
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.download.DownloadFragment.Companion.updateDownloadFromResult
import com.lagradost.quicknovel.ui.download.DownloadViewModel
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.android.synthetic.main.fragment_result.*
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import kotlin.concurrent.thread

const val MAX_SYNO_LENGH = 300

class ResultFragment : Fragment() {
    fun newInstance(url: String, apiName: String) =
        ResultFragment().apply {
            arguments = Bundle().apply {
                //println(data)
                putString("url", url)
                putString("apiName", apiName)
            }
        }

    private lateinit var viewModel: ResultViewModel

    var resultUrl = ""
    var api: MainAPI = MainAPI()

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

    val factory = InjectorUtils.provideResultViewModelFactory()
    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.getString("url")?.let {
            resultUrl = it
        }
        arguments?.getString("apiName")?.let {
            api = MainActivity.getApiFromName(it)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

    }

    var generateEpub = false
    var lastProgress: Int = 0
    fun updateGenerateBtt(progress: Int?) {
        if (viewModel.loadResponse.value != null) {
            generateEpub =
                DataStore.getKey(DOWNLOAD_EPUB_SIZE, viewModel.id.value.toString(), 0) != (progress ?: lastProgress)
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

    @SuppressLint("SetTextI18n")
    fun updateDownloadInfo(info: BookDownloader.DownloadNotification?) {
        if (info == null) return
        if (result_download_progress_text != null) {
            val hasDownload = info.progress > 0
            download_delete_trash_from_result.visibility = if (hasDownload) View.VISIBLE else View.GONE
            download_delete_trash_from_result.isClickable = hasDownload

            result_download_progress_text.text = "${info.progress}/${info.total}"
            //  result_download_progress_bar.max = info.total
            // ANIMATION PROGRESSBAR
            result_download_progress_bar.max = info.total * 100

            if (result_download_progress_bar.progress != 0) {
                val animation: ObjectAnimator = ObjectAnimator.ofInt(result_download_progress_bar,
                    "progress",
                    result_download_progress_bar.progress,
                    info.progress * 100)

                animation.duration = 500
                animation.setAutoCancel(true)
                animation.interpolator = DecelerateInterpolator()
                animation.start()
            } else {
                result_download_progress_bar.progress = info.progress * 100
            }

            result_download_progress_text_eta.text = info.ETA
            updateDownloadButtons(info.progress, info.total, info.state)
            updateGenerateBtt(info.progress)
        }
    }

    fun updateDownloadButtons(progress: Int, total: Int, state: BookDownloader.DownloadType) {
        val ePubGeneration = progress > 0
        if (result_download_generate_epub.isClickable != ePubGeneration) {
            result_download_generate_epub.isClickable = ePubGeneration
            result_download_generate_epub.alpha = if (ePubGeneration) 1f else 0.5f
        }

        var loadlState = state
        val download = progress < total
        if (!download) {
            loadlState = BookDownloader.DownloadType.IsDone
        }
        if (result_download_btt.isClickable != download) {
            result_download_btt.isClickable = download
            result_download_btt.alpha = if (download) 1f else 0.5f
        }

        result_download_btt.text = when (loadlState) {
            BookDownloader.DownloadType.IsDone -> "Downloaded"
            BookDownloader.DownloadType.IsDownloading -> "Pause"
            BookDownloader.DownloadType.IsPaused -> "Resume"
            BookDownloader.DownloadType.IsFailed -> "Re-Download"
            BookDownloader.DownloadType.IsStopped -> "Download"
        }

        result_download_btt.iconSize = 30.toPx
        result_download_btt.setIconResource(when (loadlState) {
            BookDownloader.DownloadType.IsDownloading -> R.drawable.ic_baseline_pause_24
            BookDownloader.DownloadType.IsPaused -> R.drawable.netflix_play
            BookDownloader.DownloadType.IsFailed -> R.drawable.ic_baseline_autorenew_24
            BookDownloader.DownloadType.IsDone -> R.drawable.ic_baseline_check_24
            else -> R.drawable.netflix_download
        })
    }

    override fun onDestroy() {
        //BookDownloader.downloadNotification -= ::updateDownloadInfo
        MainActivity.activity.window.navigationBarColor =
            ResourcesCompat.getColor(resources, R.color.darkBackground, null)
        super.onDestroy()
    }

    fun newIsFailed(failed: Boolean) {
        val isLoaded = viewModel.isLoaded.value ?: false
        val validState = isLoaded && viewModel.loadResponse.value != null
        result_loading.visibility = if (validState || failed) View.GONE else View.VISIBLE
        result_reload_connectionerror.visibility = if (failed) View.VISIBLE else View.GONE
    }

    fun newState(loadResponse: LoadResponse?) {
        val isLoaded = viewModel.isLoaded.value ?: false
        val validState = isLoaded && loadResponse != null

        /*
        if (mainStore.state.resultState.downloadNotification == null) mainStore.dispatch(
            LoadDownloadData())*/

        if (validState) {
            val res = loadResponse!!
            result_title.text = res.name
            result_author.text = res.author ?: getString(R.string.no_author)

            result_openinbrower_text.text = api.name //""// resultUrl
            result_openinbrower.setOnClickListener {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(resultUrl)
                startActivity(i)
            }

            download_delete_trash_from_result.setOnClickListener {
                val dialogClickListener =
                    DialogInterface.OnClickListener { dialog, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                BookDownloader.remove(loadResponse.author,
                                    loadResponse.name,
                                    viewModel.apiName.value ?: "")
                                var curren_value = viewModel.downloadNotification.value!!

                                viewModel.downloadNotification.postValue(BookDownloader.DownloadNotification(0,
                                    curren_value.total,
                                    curren_value.id,
                                    "",
                                    BookDownloader.DownloadType.IsStopped))
                            }
                            DialogInterface.BUTTON_NEGATIVE -> {
                            }
                        }
                    }
                val builder: AlertDialog.Builder = AlertDialog.Builder(this.requireContext())
                builder.setMessage("This will permanently delete ${loadResponse.name}.\nAre you sure?")
                    .setTitle("Delete")
                    .setPositiveButton("Delete", dialogClickListener)
                    .setNegativeButton("Cancel", dialogClickListener)
                    .show()
            }

            result_rating_voted_count.text = getString(R.string.no_data)
            if (res.rating != null) {
                result_rating.text = MainActivity.getRating(res.rating)
                if (res.peopleVoted != null) {
                    result_rating_voted_count.text = "${res.peopleVoted} Votes"
                }
            }

            // === TAGS ===
            result_tag.removeAllViews()
            if (res.tags == null && (res.status == null || res.status <= 0)) {
                result_tag_holder.visibility = View.GONE
            } else {
                result_tag_holder.visibility = View.VISIBLE

                var index = 0
                if (res.status != null && res.status > 0) {
                    val viewBtt = layoutInflater.inflate(R.layout.result_tag, null)
                    val mat = viewBtt.findViewById<MaterialButton>(R.id.result_tag_card)
                    mat.strokeColor = getColorStateList(context!!, R.color.colorOngoing)
                    mat.setTextColor(getColor(context!!, R.color.colorOngoing))
                    mat.rippleColor = getColorStateList(context!!, R.color.colorOngoing)
                    mat.text = when (res.status) {
                        1 -> "Ongoing"
                        2 -> "Completed"
                        3 -> "Paused"
                        4 -> "Dropped"
                        else -> "ERROR"
                    }
                    result_tag.addView(viewBtt, index)
                    index++
                }

                if (res.tags != null) {
                    for (tag in res.tags) {
                        val viewBtt = layoutInflater.inflate(R.layout.result_tag, null)
                        val btt = viewBtt.findViewById<MaterialButton>(R.id.result_tag_card)
                        btt.text = tag

                        for ((tagindex, apiTag) in api.tags.withIndex()) {
                            if (apiTag.first == tag) {
                                btt.setOnClickListener {
                                    MainActivity.activity.supportFragmentManager.beginTransaction()
                                        .setCustomAnimations(
                                            R.anim.enter_anim,
                                            R.anim.exit_anim,
                                            R.anim.pop_enter,
                                            R.anim.pop_exit)
                                        .add(R.id.homeRoot, MainPageFragment().newInstance(api.name, tag = tagindex))
                                        .commit()
                                }
                                break
                            }
                        }

                        result_tag.addView(viewBtt, index)
                        index++
                    }
                }
            }

            /*
            if(res.status != null && res.status > 0) {
                result_status.text = when(res.status) {
                    1 -> "Ongoing"
                    2 -> "Complete"
                    3 -> "Pause"
                    else -> "ERROR"
                }
            }
            else {
                result_status.visibility = View.GONE
            }*/


            result_views.text =
                if (res.views != null) humanReadableByteCountSI(res.views) else getString(R.string.no_data)

            if (res.synopsis != null) {
                var syno = res.synopsis
                if (syno.length > MAX_SYNO_LENGH) {
                    syno = syno.substring(0, MAX_SYNO_LENGH) + "..."
                }
                result_synopsis_text.setOnClickListener {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(this.context!!)
                    builder.setMessage(res.synopsis).setTitle("Synopsis")
                        .show()
                }
                result_synopsis_text.text = syno
            } else {
                result_synopsis_text.text = "..."
            }

            if (res.data.size > 0) {
                val last = res.data.last()
                result_total_chapters.text = "Latest: " + last.name //+ " " + last.dateOfRelease
            } else {
                result_total_chapters.text = getString(R.string.no_chapters)
            }

            val localId = viewModel.id.value!!

            DataStore.setKey(DOWNLOAD_TOTAL, localId.toString(), res.data.size)

            /*
            val start = BookDownloader.downloadInfo(res.author, res.name, res.data.size, api.name)
            result_download_progress_text_eta.text = ""
            if (start != null) {
                updateGenerateBtt(start.progress)
                result_download_progress_text.text = "${start.progress}/${start.total}"

                result_download_progress_bar.max = start.total * 100
                result_download_progress_bar.progress = start.progress * 100
                val state =
                    if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped
                updateDownloadButtons(start.progress, start.total, state!!)
            } else {
                result_download_progress_bar.progress = 0
            }*/

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
        }

        // TO FIX 1 FRAME WACK
        result_holder.visibility = if (isLoaded) View.VISIBLE else View.GONE
        result_loading.visibility = if (validState) View.GONE else View.VISIBLE

        result_download_card.post {
            val displayMetrics = context!!.resources.displayMetrics
            val height = result_download_card.height
            result_scroll_padding.setPadding(
                result_scroll_padding.paddingLeft,
                result_scroll_padding.paddingTop,
                result_scroll_padding.paddingRight,
                maxOf(0, displayMetrics.heightPixels - height))// - MainActivity.activity.nav_view.height
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProviders.of(this, factory)
            .get(ResultViewModel::class.java)

        if (viewModel.loadResponse.value == null)
            viewModel.initState(resultUrl, api.name)

        result_reload_connectionerror.setOnClickListener {
            viewModel.initState(resultUrl, api.name)
        }

        observe(viewModel.downloadNotification, ::updateDownloadInfo)
        observe(viewModel.loadResponse, ::newState)
        observe(viewModel.isFailedConnection, ::newIsFailed)

        /*
        storeSubscription =
            mainStore.subscribe { activity?.runOnUiThread { newState(mainStore.state.resultState.loadResponse) } }
        storeSubscription =
            mainStore.subscribe {
                if (mainStore.state.resultState.downloadNotification != null) {
                    activity?.runOnUiThread {
                        updateDownloadInfo(mainStore.state.resultState.downloadNotification!!)
                    }
                }
            }*/


        MainActivity.activity.window.navigationBarColor =
            ResourcesCompat.getColor(resources, R.color.bitDarkerGrayBackground, null)

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

        result_download_btt.setOnClickListener {
            thread {
                if (viewModel.loadResponse.value != null && viewModel.id.value != null && viewModel.apiName.value != null && viewModel.resultUrl.value != null) {
                    updateDownloadFromResult(
                        viewModel.loadResponse.value!!,
                        viewModel.id.value!!,
                        viewModel.apiName.value!!,
                        viewModel.resultUrl.value!!,
                        true)
                }
            }
        }

        result_back.setOnClickListener {
            MainActivity.backPressed()
        }

        result_download_generate_epub.setOnClickListener {
            if (generateEpub) {
                if (viewModel.loadResponse.value != null) {
                    thread {
                        val l = viewModel.loadResponse.value!!
                        val done = turnToEpub(l.author, l.name, api.name)
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
                if (viewModel.loadResponse.value != null) {
                    thread {
                        val card = viewModel.loadResponse.value!!
                        if (!BookDownloader.hasEpub(card.name)) {
                            BookDownloader.turnToEpub(card.author, card.name, api.name)
                        }
                        MainActivity.activity.runOnUiThread {
                            BookDownloader.openEpub(card.name)
                        }
                    }
                }
            }
        }
    }
}
