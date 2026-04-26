package com.notespdf.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.notespdf.app.data.Note
import com.notespdf.app.databinding.ItemNoteBinding
import java.text.SimpleDateFormat
import java.util.*

class NotesAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onNoteLongClick: (Note) -> Unit
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(DiffCallback()) {

    private val noteColors = listOf(
        "#FFFFFF", "#FFF9C4", "#F8BBD9", "#BBDEFB", "#C8E6C9",
        "#FFE0B2", "#E1BEE7", "#B2EBF2", "#FFCDD2", "#DCEDC8"
    )

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            binding.tvTitle.text = note.title.ifBlank { "Untitled" }
            binding.tvContent.text = android.text.Html.fromHtml(note.content).toString().trim()
            binding.tvDate.text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(Date(note.updatedAt))

            val color = noteColors[note.color % noteColors.size]
            binding.cardNote.setCardBackgroundColor(Color.parseColor(color))

            if (note.isPinned) {
                binding.ivPin.visibility = android.view.View.VISIBLE
            } else {
                binding.ivPin.visibility = android.view.View.GONE
            }

            if (!note.pdfPath.isNullOrBlank()) {
                binding.ivPdf.visibility = android.view.View.VISIBLE
            } else {
                binding.ivPdf.visibility = android.view.View.GONE
            }

            if (note.tag.isNotBlank()) {
                binding.tvTag.visibility = android.view.View.VISIBLE
                binding.tvTag.text = "# ${note.tag}"
            } else {
                binding.tvTag.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener { onNoteClick(note) }
            binding.root.setOnLongClickListener {
                onNoteLongClick(note)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
    }
}
