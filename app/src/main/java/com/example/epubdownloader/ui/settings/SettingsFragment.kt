package com.example.epubdownloader.ui.settings

import android.content.*
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.example.epubdownloader.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }
}