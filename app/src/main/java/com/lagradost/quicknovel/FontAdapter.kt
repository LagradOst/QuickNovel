package com.lagradost.quicknovel

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import com.lagradost.quicknovel.databinding.SortBottomSingleChoiceBinding
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.UIHelper.parseFontFileName
import java.io.File

data class FontFile(
    val file : File?
)

class FontAdapter(val context: Context, private val checked: Int?, val clickCallback : (FontFile) -> Unit) : NoStateAdapter<FontFile>() {
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SortBottomSingleChoiceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: FontFile, position: Int) {
        val binding = holder.view as? SortBottomSingleChoiceBinding ?: return
        val font = item.file

        binding.text1.text = parseFontFileName(font?.name)
        binding.text1.isActivated = position == checked
        if (font != null) {
            binding.text1.typeface = Typeface.createFromFile(font)
        }
        binding.text1.setOnClickListener {
            this.clickCallback.invoke(item)
        }
    }
}