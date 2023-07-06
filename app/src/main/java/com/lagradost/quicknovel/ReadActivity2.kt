package com.lagradost.quicknovel

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.viewModels
import com.fasterxml.jackson.module.kotlin.readValue
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.TTSHelper.initMediaSession
import com.lagradost.quicknovel.databinding.ReadMainBinding
import com.lagradost.quicknovel.receivers.BecomingNoisyReceiver
import com.lagradost.quicknovel.ui.mainpage.MainPageViewModel
import nl.siegmann.epublib.epub.EpubReader
import java.lang.ref.WeakReference

class ReadActivity2 : AppCompatActivity(), ColorPickerDialogListener  {
    companion object {
        private var _readActivity: WeakReference<ReadActivity2>? = null
        var readActivity
            get() = _readActivity?.get()
            private set(value) {
                _readActivity = WeakReference(value)
            }
    }


    lateinit var binding: ReadMainBinding
    private val viewModel: ReadActivityViewModel by viewModels()

    override fun onColorSelected(dialog: Int, color: Int) {
        when (dialog) {
            0 -> setBackgroundColor(color)
            1 -> setTextColor(color)
        }
    }

    private fun setBackgroundColor(color: Int) {

    }

    private fun setTextColor(color: Int) {

    }

    override fun onDialogDismissed(dialog: Int) {
        updateImages()
    }

    private fun updateImages() {

    }

    private fun kill() {
        with(NotificationManagerCompat.from(this)) { // KILLS NOTIFICATION
            cancel(TTS_NOTIFICATION_ID)
        }
        finish()
    }

    fun registerBattery() {
        val mBatInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            @SuppressLint("SetTextI18n")
            override fun onReceive(ctxt: Context?, intent: Intent) {
                val batteryPct: Float = intent.let { intent ->
                    val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    level * 100 / scale.toFloat()
                }
                binding.readBattery.text = "${batteryPct.toInt()}%"
            }
        }
        IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            this.registerReceiver(mBatInfoReceiver, ifilter)
        }
    }

    fun parseAction(input : TTSHelper.TTSActionType) : Boolean {
        return viewModel.parseAction(input)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        binding = ReadMainBinding.inflate(layoutInflater)

        registerBattery()

        viewModel.init(intent,this)
    }
}