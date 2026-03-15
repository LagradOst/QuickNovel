package com.lagradost.quicknovel.util

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ExplainHistoryAdapter(
    private val entries: List<ExplainEntry>
) : RecyclerView.Adapter<ExplainHistoryAdapter.ViewHolder>() {

    class ViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_explain_card, parent, false)
    ) {
        val word: TextView = itemView.findViewById(R.id.explain_card_word)
        val paragraph: TextView = itemView.findViewById(R.id.explain_card_paragraph)
        val result: TextView = itemView.findViewById(R.id.explain_card_result)
        val timestamp: TextView = itemView.findViewById(R.id.explain_card_timestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(parent)

    override fun getItemCount() = entries.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.word.text = entry.selected
        holder.paragraph.text = entry.paragraph
        holder.result.text = entry.result
        holder.timestamp.text = formatRelativeTime(entry.timestamp)
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
