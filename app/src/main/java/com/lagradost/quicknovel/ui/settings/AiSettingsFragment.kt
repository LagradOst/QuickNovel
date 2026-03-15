package com.lagradost.quicknovel.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.lagradost.quicknovel.OPENROUTER_API_KEY
import com.lagradost.quicknovel.OPENROUTER_DEFAULT_MODEL
import com.lagradost.quicknovel.OPENROUTER_DEFAULT_PROMPT
import com.lagradost.quicknovel.OPENROUTER_MODEL
import com.lagradost.quicknovel.OPENROUTER_PROMPT
import com.lagradost.quicknovel.OPENROUTER_REASONING
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.databinding.FragmentAiSettingsBinding

class AiSettingsFragment : Fragment() {

    private var _binding: FragmentAiSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        // Load saved values
        binding.aiApiKey.setText(ctx.getKey<String>(OPENROUTER_API_KEY) ?: "")
        binding.aiModel.setText(ctx.getKey<String>(OPENROUTER_MODEL) ?: OPENROUTER_DEFAULT_MODEL)
        binding.aiPrompt.setText(ctx.getKey<String>(OPENROUTER_PROMPT) ?: OPENROUTER_DEFAULT_PROMPT)
        binding.aiReasoning.isChecked = ctx.getKey<Boolean>(OPENROUTER_REASONING) ?: false

        // Save on change
        binding.aiApiKey.doAfterTextChanged {
            ctx.setKey(OPENROUTER_API_KEY, it?.toString()?.trim() ?: "")
        }
        binding.aiModel.doAfterTextChanged {
            ctx.setKey(OPENROUTER_MODEL, it?.toString()?.trim() ?: "")
        }
        binding.aiPrompt.doAfterTextChanged {
            ctx.setKey(OPENROUTER_PROMPT, it?.toString() ?: "")
        }
        binding.aiReasoning.setOnCheckedChangeListener { _, isChecked ->
            ctx.setKey(OPENROUTER_REASONING, isChecked)
        }

        // Reset prompt
        binding.aiResetPrompt.setOnClickListener {
            binding.aiPrompt.setText(OPENROUTER_DEFAULT_PROMPT)
            ctx.setKey(OPENROUTER_PROMPT, OPENROUTER_DEFAULT_PROMPT)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
