package com.notespdf.app.ui

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.notespdf.app.R
import com.notespdf.app.databinding.ActivityPdfViewerBinding
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfViewerBinding
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex = 0
    private var totalPages = 0

    // Annotation state
    private var annotationMode = AnnotationMode.NONE
    private val annotationPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val highlightPaint = Paint().apply {
        color = Color.YELLOW
        alpha = 120
        strokeWidth = 30f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private var annotationPath = Path()
    private val savedPaths = mutableListOf<Pair<Path, Paint>>()
    private var annotationBitmap: Bitmap? = null
    private var annotationCanvas: Canvas? = null

    enum class AnnotationMode { NONE, DRAW, HIGHLIGHT, ERASE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val pdfPath = intent.getStringExtra("PDF_PATH")
        if (pdfPath.isNullOrBlank()) {
            Toast.makeText(this, "No PDF file found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        openPdf(pdfPath)
        setupControls()
        setupAnnotationTools()
        setupTouchDrawing()
    }

    private fun openPdf(path: String) {
        try {
            val file = File(path)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(descriptor)
            totalPages = pdfRenderer!!.pageCount
            supportActionBar?.title = file.name
            renderPage(0)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open PDF", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun renderPage(index: Int) {
        if (index < 0 || index >= totalPages) return
        currentPage?.close()
        val page = pdfRenderer!!.openPage(index)
        currentPageIndex = index

        val scale = resources.displayMetrics.widthPixels.toFloat() / page.width
        val width = (page.width * scale).toInt()
        val height = (page.height * scale).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        annotationBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        annotationCanvas = Canvas(annotationBitmap!!)

        binding.ivPdfPage.setImageBitmap(bitmap)
        binding.overlayView.setAnnotationBitmap(annotationBitmap!!)
        binding.overlayView.invalidate()

        updatePageIndicator()
        currentPage = page
    }

    private fun updatePageIndicator() {
        binding.tvPageInfo.text = "${currentPageIndex + 1} / $totalPages"
        binding.btnPrevPage.isEnabled = currentPageIndex > 0
        binding.btnNextPage.isEnabled = currentPageIndex < totalPages - 1
    }

    private fun setupControls() {
        binding.btnPrevPage.setOnClickListener { renderPage(currentPageIndex - 1) }
        binding.btnNextPage.setOnClickListener { renderPage(currentPageIndex + 1) }
    }

    private fun setupAnnotationTools() {
        binding.btnDraw.setOnClickListener {
            annotationMode = if (annotationMode == AnnotationMode.DRAW) AnnotationMode.NONE else AnnotationMode.DRAW
            updateAnnotationButtons()
        }
        binding.btnHighlight.setOnClickListener {
            annotationMode = if (annotationMode == AnnotationMode.HIGHLIGHT) AnnotationMode.NONE else AnnotationMode.HIGHLIGHT
            updateAnnotationButtons()
        }
        binding.btnErase.setOnClickListener {
            annotationMode = if (annotationMode == AnnotationMode.ERASE) AnnotationMode.NONE else AnnotationMode.ERASE
            updateAnnotationButtons()
        }
        binding.btnUndo.setOnClickListener {
            if (savedPaths.isNotEmpty()) {
                savedPaths.removeLastOrNull()
                redrawAnnotations()
            }
        }
        binding.btnClearAnnotations.setOnClickListener {
            savedPaths.clear()
            redrawAnnotations()
        }

        // Color picker for annotations
        binding.btnColorRed.setOnClickListener { annotationPaint.color = Color.RED }
        binding.btnColorBlue.setOnClickListener { annotationPaint.color = Color.BLUE }
        binding.btnColorGreen.setOnClickListener { annotationPaint.color = Color.GREEN }
        binding.btnColorBlack.setOnClickListener { annotationPaint.color = Color.BLACK }
    }

    private fun updateAnnotationButtons() {
        binding.btnDraw.alpha = if (annotationMode == AnnotationMode.DRAW) 1f else 0.5f
        binding.btnHighlight.alpha = if (annotationMode == AnnotationMode.HIGHLIGHT) 1f else 0.5f
        binding.btnErase.alpha = if (annotationMode == AnnotationMode.ERASE) 1f else 0.5f
    }

    private fun setupTouchDrawing() {
        binding.overlayView.setOnTouchCallback { event ->
            if (annotationMode == AnnotationMode.NONE) return@setOnTouchCallback

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    annotationPath = Path()
                    annotationPath.moveTo(event.x, event.y)
                }
                MotionEvent.ACTION_MOVE -> {
                    annotationPath.lineTo(event.x, event.y)
                    val currentPaint = if (annotationMode == AnnotationMode.HIGHLIGHT) {
                        Paint(highlightPaint)
                    } else if (annotationMode == AnnotationMode.ERASE) {
                        Paint().apply {
                            color = Color.WHITE
                            strokeWidth = 30f
                            style = Paint.Style.STROKE
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                        }
                    } else {
                        Paint(annotationPaint)
                    }
                    annotationCanvas?.drawPath(annotationPath, currentPaint)
                    binding.overlayView.invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val currentPaint = if (annotationMode == AnnotationMode.HIGHLIGHT) {
                        Paint(highlightPaint)
                    } else {
                        Paint(annotationPaint)
                    }
                    savedPaths.add(Pair(Path(annotationPath), currentPaint))
                }
            }
        }
    }

    private fun redrawAnnotations() {
        annotationBitmap?.eraseColor(Color.TRANSPARENT)
        savedPaths.forEach { (path, paint) ->
            annotationCanvas?.drawPath(path, paint)
        }
        binding.overlayView.invalidate()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPage?.close()
        pdfRenderer?.close()
    }
}
