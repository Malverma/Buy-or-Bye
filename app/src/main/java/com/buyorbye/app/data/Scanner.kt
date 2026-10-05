package com.buyorbye.app.data

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.buyorbye.app.domain.Barcodes
import com.buyorbye.app.domain.PriceParsing
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** One line of recognized text; [height] (px) approximates its font size. */
data class OcrLine(val text: String, val height: Int)

data class ScanResult(
    val upc: String?,
    val shelfPrice: Double?,
    /** Recognized text, used to guess a search query when there's no barcode. */
    val lines: List<OcrLine>,
    /** The frame the result came from, upright and downscaled, shown while the user confirms. */
    val photo: Bitmap? = null,
) {
    /**
     * A product name guessed from the label: the largest word-like print, since brand and
     * product names are the biggest text on a package. Long lines (ingredients, fine print)
     * and lines that are mostly digits or symbols are skipped. Empty when nothing qualifies,
     * so the user sees the placeholder instead of junk.
     */
    fun queryGuess(): String = lines
        .map { it.copy(text = it.text.trim()) }
        .filter { looksLikeWords(it.text) }
        .sortedByDescending { it.height }
        .take(2)
        .joinToString(" ") { it.text }

    private fun looksLikeWords(line: String): Boolean {
        if (line.length > 40 || '$' in line || '%' in line) return false
        val letters = line.count { it.isLetter() }
        val nonSpace = line.count { !it.isWhitespace() }
        val hasWord = line.split(' ').any { w -> w.length >= 3 && w.all { it.isLetter() || it == '\'' } }
        return letters >= 3 && hasWord && letters >= nonSpace * 0.7
    }
}

/** On-device barcode + text recognition (ML Kit). Works offline. */
class Scanner {
    val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
            .build(),
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun barcode(image: InputImage): String? =
        runCatching { barcodeScanner.process(image).await() }.getOrNull()
            ?.firstNotNullOfOrNull { b ->
                b.rawValue?.takeIf { v -> v.isNotEmpty() && v.all(Char::isDigit) }
                    ?.let { Barcodes.normalize(it, isUpcE = b.format == Barcode.FORMAT_UPC_E) }
            }

    suspend fun text(image: InputImage): List<OcrLine> =
        runCatching { textRecognizer.process(image).await() }.getOrNull()
            ?.textBlocks?.flatMap { b -> b.lines.map { OcrLine(it.text, it.boundingBox?.height() ?: 0) } }
            .orEmpty()

    suspend fun analyze(image: InputImage, knownUpc: String? = null, photo: Bitmap? = null): ScanResult {
        val upc = knownUpc ?: barcode(image)
        val lines = text(image)
        return ScanResult(upc, PriceParsing.guessShelfPrice(lines.map { it.text }), lines, photo)
    }
}

/** Copies the frame to an upright bitmap no larger than [maxEdge]. Call before closing the proxy. */
fun ImageProxy.toUprightBitmap(maxEdge: Int = 1080): Bitmap? = runCatching {
    val src = toBitmap()
    val scale = minOf(1f, maxEdge.toFloat() / maxOf(src.width, src.height))
    val m = Matrix().apply {
        postScale(scale, scale)
        postRotate(imageInfo.rotationDegrees.toFloat())
    }
    Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}.getOrNull()
