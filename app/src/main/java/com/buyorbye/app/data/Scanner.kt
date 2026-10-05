package com.buyorbye.app.data

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.buyorbye.app.domain.PriceParsing
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class ScanResult(
    val upc: String?,
    val shelfPrice: Double?,
    /** Recognized text lines, used as a search query when there's no barcode. */
    val textLines: List<String>,
    /** The frame the result came from, upright and downscaled, shown while the user confirms. */
    val photo: Bitmap? = null,
) {
    /** A rough product name from label text: the longest lines that look like words, not prices. */
    fun queryGuess(): String = textLines
        .map { it.trim() }
        .filter { line -> line.count { it.isLetter() } >= 4 && '$' !in line }
        .sortedByDescending { it.length }
        .take(2)
        .joinToString(" ")
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
            ?.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.all(Char::isDigit) } }

    suspend fun text(image: InputImage): List<String> =
        runCatching { textRecognizer.process(image).await() }.getOrNull()
            ?.textBlocks?.flatMap { b -> b.lines.map { it.text } }
            .orEmpty()

    suspend fun analyze(image: InputImage, knownUpc: String? = null, photo: Bitmap? = null): ScanResult {
        val upc = knownUpc ?: barcode(image)
        val lines = text(image)
        return ScanResult(upc, PriceParsing.guessShelfPrice(lines), lines, photo)
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
