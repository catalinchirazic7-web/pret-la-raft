package com.example.shoppinglist.ocr

import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageProxy.PlaneProxy

/**
 * Hartă grosieră a zonelor roșii ale unui cadru, în orientarea „dreaptă” a imaginii
 * (aceeași în care ML Kit întoarce coordonatele liniilor de text).
 *
 * Se calculează sincron, din planurile YUV, înainte de OCR, deci nu depinde de buffer-ul
 * cadrului după ce acesta este închis.
 */
class RednessGrid private constructor(
    private val uprightWidth: Int,
    private val uprightHeight: Int,
    private val red: BooleanArray,
) {

    /** Fracțiunea celulelor roșii care acoperă dreptunghiul dat (coordonate ale imaginii drepte). */
    fun redFraction(box: PixelBox): Float {
        val cellW = uprightWidth.toFloat() / COLUMNS
        val cellH = uprightHeight.toFloat() / ROWS
        val c0 = (box.left / cellW).toInt().coerceIn(0, COLUMNS - 1)
        val c1 = (box.right / cellW).toInt().coerceIn(0, COLUMNS - 1)
        val r0 = (box.top / cellH).toInt().coerceIn(0, ROWS - 1)
        val r1 = (box.bottom / cellH).toInt().coerceIn(0, ROWS - 1)
        var total = 0
        var reds = 0
        for (row in r0..r1) {
            for (column in c0..c1) {
                total++
                if (red[row * COLUMNS + column]) reds++
            }
        }
        return if (total == 0) 0f else reds.toFloat() / total
    }

    companion object {
        const val COLUMNS = 48
        const val ROWS = 36

        /** Prag YUV pentru roșu: Cr ridicat, Cb scăzut. */
        private const val RED_MIN_CR = 150
        private const val RED_MAX_CB = 110

        fun from(imageProxy: ImageProxy): RednessGrid {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val rawWidth = imageProxy.width
            val rawHeight = imageProxy.height
            val swapped = rotation == 90 || rotation == 270
            val uprightWidth = if (swapped) rawHeight else rawWidth
            val uprightHeight = if (swapped) rawWidth else rawHeight

            val planes = imageProxy.planes
            val cb = planes[1]
            val cr = planes[2]

            val red = BooleanArray(COLUMNS * ROWS)
            for (row in 0 until ROWS) {
                for (column in 0 until COLUMNS) {
                    val ux = ((column + 0.5f) * uprightWidth / COLUMNS).toInt().coerceIn(0, uprightWidth - 1)
                    val uy = ((row + 0.5f) * uprightHeight / ROWS).toInt().coerceIn(0, uprightHeight - 1)
                    val (rawX, rawY) = toRaw(ux, uy, rotation, rawWidth, rawHeight)
                    red[row * COLUMNS + column] = isRed(cb, cr, rawX, rawY)
                }
            }
            return RednessGrid(uprightWidth, uprightHeight, red)
        }

        /** Trece un punct din imaginea dreaptă în coordonatele imaginii brute a senzorului. */
        private fun toRaw(x: Int, y: Int, rotation: Int, rawWidth: Int, rawHeight: Int): Pair<Int, Int> =
            when (rotation) {
                90 -> Pair(y, rawHeight - 1 - x)
                180 -> Pair(rawWidth - 1 - x, rawHeight - 1 - y)
                270 -> Pair(rawWidth - 1 - y, x)
                else -> Pair(x, y)
            }

        private fun isRed(cb: PlaneProxy, cr: PlaneProxy, x: Int, y: Int): Boolean {
            val chromaX = x / 2
            val chromaY = y / 2
            val cbIndex = chromaY * cb.rowStride + chromaX * cb.pixelStride
            val crIndex = chromaY * cr.rowStride + chromaX * cr.pixelStride
            if (cbIndex < 0 || crIndex < 0 ||
                cbIndex >= cb.buffer.limit() || crIndex >= cr.buffer.limit()
            ) {
                return false
            }
            val u = cb.buffer.get(cbIndex).toInt() and 0xFF
            val v = cr.buffer.get(crIndex).toInt() and 0xFF
            return v >= RED_MIN_CR && u <= RED_MAX_CB
        }
    }
}
