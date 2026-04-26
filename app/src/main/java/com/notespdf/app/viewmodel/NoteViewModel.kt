package com.notespdf.app.ui

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.notespdf.app.databinding.ActivityPdfViewerBinding
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfViewerBinding
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex = 0
    private var totalPages = 0
    private var isAnnotationLocked = false

    enum class AnnotationMode { NONE, DRAW, HIGHLIGHT, ERASE }
    private var annotationMode = AnnotationMode.NONE

    private val drawPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val highlightPaint = Paint().apply {
        color = Color.YELLOW
        alpha = 120
        strokeWidth = 40f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val erasePaint = Paint().apply {
        strokeWidth = 30f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var currentPath = Path()
    private val savedPaths = mutableListOf<Pair<Path, Paint>>()
    private var annotationBitmap: Bitmap? = null
    private var annotationCanvas: Canvas? = null
    private var lastX = 0f
    private var lastY = 0f

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
        savedPaths.clear()

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
        binding.btnPrevPage.setOnClickListener {
            if (annotationMode == AnnotationMode.NONE) renderPage(currentPageIndex - 1)
        }
        binding.btnNextPage.setOnClickListener {
            if (annotationMode == AnnotationMode.NONE) renderPage(currentPageIndex + 1)
        }
    }

    private fun setupAnnotationTools() {
        // Draw button
        binding.btnDraw.setOnClickListener {
            annotationMode = if (annotationMode == AnnotationMode.DRAW) {
                unlockScroll()
                AnnotationMode.NONE
            } else {
                lockScroll()
                AnnotationMode.DRAW
            }
            updateAnnotationButtons()
        }

        // Highlight button — separate from draw
        binding.btnHighlight.setOnClickListener {
            annotationMode = if (annotationMode == AnnotationMode.HIGHLIGHT) {
                unlockScroll()
                AnnotationMode.NONE
            } else {
                lockScroll()
                AnnotationMode.HIGHLIGHT
            }
            updateAnnotationButtons()
        }

        // Erase button
        binding.btnErase.setOnClickListener {
            annotationMode = if (annotationMode == AnnotationMode.ERASE) {
                unlockScroll()
                AnnotationMode.NONE
            } else {
                lockScroll()
                AnnotationMode.ERASE
            }
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
            Toast.makeText(this, "Annotations cleared", Toast.LENGTH_SHORT).show()
        }

        // Color buttons for draw mode
        binding.btnColorRed.setOnClickListener {
            drawPaint.color = Color.RED
            Toast.makeText(this, "Red", Toast.LENGTH_SHORT).show()
        }
        binding.btnColorBlue.setOnClickListener {
            drawPaint.color = Color.BLUE
            Toast.makeText(this, "Blue", Toast.LENGTH_SHORT).show()
        }
        binding.btnColorGreen.setOnClickListener {
            drawPaint.color = Color.GREEN
            Toast.makeText(this, "Green", Toast.LENGTH_SHORT).show()
        }
        binding.btnColorBlack.setOnClickListener {
            drawPaint.color = Color.BLACK
            Toast.makeText(this, "Black", Toast.LENGTH_SHORT).show()
        }
    }

    private fun lockScroll() {
        isAnnotationLocked = true
        binding.scrollView.requestDisallowInterceptTouchEvent(true)
        Toast.makeText(this, "Scroll locked — annotation mode ON", Toast.LENGTH_SHORT).show()
    }

    private fun unlockScroll() {
        isAnnotationLocked = false
        binding.scrollView.requestDisallowInterceptTouchEvent(false)
        Toast.makeText(this, "Scroll unlocked", Toast.LENGTH_SHORT).show()
    }

    private fun updateAnnotationButtons() {
        binding.btnDraw.alpha = if (annotationMode == AnnotationMode.DRAW) 1f else 0.4f
        binding.btnHighlight.alpha = if (annotationMode == AnnotationMode.HIGHLIGHT) 1f else 0.4f
        binding.btnErase.alpha = if (annotationMode == AnnotationMode.ERASE) 1f else 0.4f
    }

    private fun setupTouchDrawing() {
        binding.overlayView.setOnTouchCallback { event ->
            if (annotationMode == AnnotationMode.NONE) return@setOnTouchCallback

            val x = event.x
            val y = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentPath = Path()
                    currentPath.moveTo(x, y)
                    lastX = x
                    lastY = y
                }
                MotionEvent.ACTION_MOVE -> {
                    // Smooth curve drawing
                    val midX = (x + lastX) / 2
                    val midY = (y + lastY) / 2
                    currentPath.quadTo(lastX, lastY, midX, midY)
                    lastX = x
                    lastY = y

                    val activePaint = getActivePaint()
                    annotationCanvas?.drawPath(currentPath, activePaint)
                    binding.overlayView.invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    currentPath.lineTo(x, y)
                    val activePaint = getActivePaint()
                    savedPaths.add(Pair(Path(currentPath), Paint(activePaint)))
                    annotationCanvas?.drawPath(currentPath, activePaint)
                    binding.overlayView.invalidate()
                }
            }
        }
    }

    private fun getActivePaint(): Paint {
        return when (annotationMode) {
            AnnotationMode.HIGHLIGHT -> Paint(highlightPaint)
            AnnotationMode.ERASE -> Paint(erasePaint)
            else -> Paint(drawPaint)
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
