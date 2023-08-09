package com.lagradost.quicknovel.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.lagradost.quicknovel.ReadActivity2
import com.lagradost.quicknovel.TTSHelper

class BecomingNoisyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {

            /*if (ReadActivity.readActivity.ttsStatus == ReadActivity.TTSStatus.IsRunning) {
                ReadActivity.readActivity.isTTSPaused = true
            }*/
            ReadActivity2.readActivity?.parseAction(TTSHelper.TTSActionType.Pause)
        }
    }
}