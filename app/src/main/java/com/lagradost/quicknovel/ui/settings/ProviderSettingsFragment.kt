package com.lagradost.quicknovel.ui.settings


import android.os.Bundle
import androidx.preference.*
import com.lagradost.quicknovel.R

class ProviderSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.provider_settings, rootKey)
    }
}