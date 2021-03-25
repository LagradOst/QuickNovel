package com.example.epubdownloader.ui.result

import android.opengl.Visibility
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.example.epubdownloader.MainActivity
import com.example.epubdownloader.R
import kotlinx.android.synthetic.main.fragment_result.*
import kotlin.concurrent.thread
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import jp.wasabeef.glide.transformations.BlurTransformation
import java.text.StringCharacterIterator

import java.text.CharacterIterator


class ResultFragment(url: String) : Fragment() {
    val resultUrl = url

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

        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        return inflater.inflate(R.layout.fragment_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        result_holder.visibility = View.GONE
        result_loading.visibility = View.VISIBLE

        thread {
            val res = MainActivity.api.load(resultUrl)
            activity?.runOnUiThread {
                if (res == null) {
                    Toast.makeText(context, "Error loading", Toast.LENGTH_SHORT).show()
                } else {
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
                    if(res.Synopsis != null) {
                        result_synopsis_text.text = res.Synopsis
                    }
                    val last = res.data.last()
                   result_total_chapters.text = "Latest: " +  last.name //+ " " + last.dateOfRelease

                    /*
                    if(res.tags != null) {
                        var text = res.tags.joinToString(" • ")
                        result_tags.text = text
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
            }

        }
    }
}