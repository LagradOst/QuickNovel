package com.lagradost.quicknovel.services

import android.app.IntentService
import android.content.Intent
import com.lagradost.quicknovel.ReadActivity2
import com.lagradost.quicknovel.TTSHelper

class TTSPauseService : IntentService("TTSPauseService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val id = intent.getIntExtra("id", -1)

            when (id) {
                TTSHelper.TTSActionType.Pause.ordinal -> {
                    TTSHelper.TTSActionType.Pause
                }
                TTSHelper.TTSActionType.Resume.ordinal -> {
                    TTSHelper.TTSActionType.Resume
                }
                TTSHelper.TTSActionType.Stop.ordinal -> {
                    TTSHelper.TTSActionType.Stop
                }
                else -> null
            }?.let { action ->
                ReadActivity2.readActivity?.parseAction(action)
            }
            /*
            when (id) {
                TTSActionType.Pause.ordinal -> {
                    ReadActivity.readActivity.isTTSPaused = true
                }
                TTSActionType.Resume.ordinal -> {
                    ReadActivity.readActivity.isTTSPaused = false
                }
                TTSActionType.Stop.ordinal -> {
                    ReadActivity.readActivity.stopTTS()
                }
            }*/
        }
    }
}