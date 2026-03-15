package com.lagradost.quicknovel.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.quicknovel.databinding.FragmentSettingsTabbedBinding
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar

/**
 * Tabbed Settings screen.
 * Tab 0: Preferences (the original settings, now in PreferencesFragment)
 * Tab 1: AI (OpenRouter configuration, in AiSettingsFragment)
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsTabbedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsTabbedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.fixPaddingStatusbar(binding.settingsTabLayout)

        val tabTitles = listOf("Preferences", "Assistant")

        binding.settingsViewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabTitles.size
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> PreferencesFragment()
                1 -> AiSettingsFragment()
                else -> throw IllegalArgumentException("Unknown tab position $position")
            }
        }

        TabLayoutMediator(binding.settingsTabLayout, binding.settingsViewPager) { tab, pos ->
            tab.text = tabTitles[pos]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
