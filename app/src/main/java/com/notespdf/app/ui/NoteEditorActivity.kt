package com.notespdf.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.notespdf.app.R
import com.notespdf.app.data.Note
import com.notespdf.app.databinding.ActivityNoteEditorBinding
import com.notespdf.app.viewmodel.NoteViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private val viewModel: NoteViewModel by viewModels()
    private var currentNote: Note? = null
    private var selectedColor = 0
    private var pdfPath: String? = null
    private var isPinned = false

    private val noteColors = listOf(
        "#FFFFFF", "#FFF9C4", "#F8BBD9", "#BBDEFB", "#C8E6C9",
        "#FFE0B2", "#E1BEE7", "#B2EBF2", "#FFCDD2", "#DCEDC8"
    )

    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handlePdfImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val noteId = intent.getIntExtra("NOTE_ID", -1)
        if (noteId != -1) {
            loadNote(noteId)
        }

        setupEditor()
        setupColorPicker()
        setupPdfButton()
        setupTagInput()
    }

    private fun loadNote(noteId: Int) {
        lifecycleScope.launch {
            currentNote = viewModel.getNoteById(noteId)
            currentNote?.let { note ->
                binding.etTitle.setText(note.title)
                binding.richEditor.html = note.content
                selectedColor = note.color
                pdfPath = note.pdfPath
                isPinned = note.isPinned
                binding.etTag.setText(note.tag)
                updatePdfChip()
                applyColor(selectedColor)
            }
        }
    }

    private fun setupEditor() {
        binding.richEditor.setEditorHeight(200)
        binding.richEditor.setEditorFontSize(16)
        binding.richEditor.setPadding(10, 10, 10, 10)

        // Formatting toolbar
        binding.btnBold.setOnClickListener { binding.richEditor.setBold() }
        binding.btnItalic.setOnClickListener { binding.richEditor.setItalic() }
        binding.btnUnderline.setOnClickListener { binding.richEditor.setUnderline() }
        binding.btnBullet.setOnClickListener { binding.richEditor.setBullets() }
        binding.btnNumber.setOnClickListener { binding.richEditor.setNumbers() }
        binding.btnQuote.setOnClickListener { binding.richEditor.setBlockquote() }
        binding.btnStrike.setOnClickListener { binding.richEditor.setStrikeThrough() }
        binding.btnH1.setOnClickListener { binding.richEditor.setHeading(1) }
        binding.btnH2.setOnClickListener { binding.richEditor.setHeading(2) }
    }

    private fun setupColorPicker() {
        noteColors.forEachIndexed { index, color ->
            val chip = Chip(this)
            chip.text = ""
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor(color)
            )
            chip.chipStrokeWidth = 2f
            chip.isCheckable = true
            chip.isChecked = index == selectedColor
            chip.setOnClickListener {
                selectedColor = index
                applyColor(index)
            }
            binding.colorPickerGroup.addView(chip)
        }
    }

    private fun applyColor(colorIndex: Int) {
        val color = android.graphics.Color.parseColor(noteColors[colorIndex % noteColors.size])
        binding.editorContainer.setBackgroundColor(color)
    }

    private fun setupPdfButton() {
        binding.btnAttachPdf.setOnClickListener {
            pickPdf.launch("application/pdf")
        }
        binding.btnViewPdf.setOnClickListener {
            pdfPath?.let { path ->
                val intent = Intent(this, PdfViewerActivity::class.java)
                intent.putExtra("PDF_PATH", path)
                startActivity(intent)
            }
        }
    }

    private fun handlePdfImport(uri: Uri) {
        try {
            val fileName = "pdf_${System.currentTimeMillis()}.pdf"
            val destFile = File(filesDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            pdfPath = destFile.absolutePath
            updatePdfChip()
            Toast.makeText(this, "PDF attached successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to attach PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePdfChip() {
        if (pdfPath != null) {
            binding.pdfChip.visibility = android.view.View.VISIBLE
            binding.pdfChip.text = File(pdfPath!!).name
            binding.btnViewPdf.visibility = android.view.View.VISIBLE
            binding.pdfChip.setOnCloseIconClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Remove PDF")
                    .setMessage("Remove attached PDF from this note?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove") { _, _ ->
                        pdfPath = null
                        binding.pdfChip.visibility = android.view.View.GONE
                        binding.btnViewPdf.visibility = android.view.View.GONE
                    }
                    .show()
            }
        } else {
            binding.pdfChip.visibility = android.view.View.GONE
            binding.btnViewPdf.visibility = android.view.View.GONE
        }
    }

    private fun setupTagInput() {
        // Tag input is just a simple EditText
    }

    private fun saveNote() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.richEditor.html ?: ""
        val tag = binding.etTag.text.toString().trim()

        if (title.isBlank() && content.isBlank()) {
            Toast.makeText(this, "Note is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val note = if (currentNote != null) {
            currentNote!!.copy(
                title = title.ifBlank { "Untitled" },
                content = content,
                color = selectedColor,
                pdfPath = pdfPath,
                isPinned = isPinned,
                tag = tag,
                updatedAt = now
            )
        } else {
            Note(
                title = title.ifBlank { "Untitled" },
                content = content,
                color = selectedColor,
                pdfPath = pdfPath,
                isPinned = isPinned,
                tag = tag,
                createdAt = now,
                updatedAt = now
            )
        }

        if (currentNote != null) viewModel.update(note)
        else viewModel.insert(note)

        Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_save -> { saveNote(); true }
            R.id.action_pin -> {
                isPinned = !isPinned
                item.setIcon(if (isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin)
                Toast.makeText(this, if (isPinned) "Note pinned" else "Note unpinned", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_delete -> {
                currentNote?.let {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Delete Note")
                        .setMessage("Delete this note permanently?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            viewModel.delete(it)
                            finish()
                        }
                        .show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
