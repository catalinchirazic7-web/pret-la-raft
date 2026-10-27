package com.example.shoppinglist.ocr

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Rulează OCR pe fiecare cadru și transmite prețul ales de [PriceTagSelector] (sau null).
 * Apelat pe executorul de analiză al CameraX.
 */
class PriceTagAnalyzer(
    private val onResult: (PriceCandidate?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)

        // Harta de roșu se calculează acum, cât planurile YUV sunt sigur valide.
        val grid = try {
            RednessGrid.from(imageProxy)
        } catch (e: Exception) {
            Log.w(TAG, "Harta de roșu nu a putut fi calculată", e)
            null
        }

        val task = try {
            recognizer.process(input)
        } catch (e: Exception) {
            Log.e(TAG, "OCR nu a putut porni", e)
            imageProxy.close()
            return
        }

        // Cadrul se eliberează doar după ce ML Kit termină de citit bufferul.
        // Listener-ul de finalizare rulează și la succes, și la eșec.
        task
            .addOnSuccessListener { text ->
                val lines = text.toOcrLines()
                onResult(PriceTagSelector.select(lines) { box -> grid?.redFraction(box) ?: 0f })
            }
            .addOnFailureListener { e -> Log.w(TAG, "OCR a eșuat pe un cadru", e) }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        recognizer.close()
    }

    private fun Text.toOcrLines(): List<OcrLine> =
        textBlocks.flatMap { block ->
            block.lines.map { line ->
                val rect = line.boundingBox
                OcrLine(
                    text = line.text,
                    heightPx = (rect?.height() ?: 1).toFloat(),
                    box = rect?.let { PixelBox(it.left, it.top, it.right, it.bottom) },
                )
            }
        }

    private companion object {
        const val TAG = "PriceTagAnalyzer"
    }
}
