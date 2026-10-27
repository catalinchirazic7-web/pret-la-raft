package com.example.shoppinglist.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionStabilizerTest {

    private fun frame(price: Double?, title: String? = null) =
        ScanResult(
            price = price?.let { PriceCandidate(value = it, matchedText = "x", rank = 3) },
            title = title,
        )

    @Test
    fun singleFrameIsNotStable() {
        val stabilizer = DetectionStabilizer()
        assertNull(stabilizer.add(frame(6.49, "Lapte")))
    }

    @Test
    fun priceBecomesStableAfterTwoVotes() {
        val stabilizer = DetectionStabilizer()
        stabilizer.add(frame(6.49, "Lapte"))
        val stable = stabilizer.add(frame(6.49, "Lapte"))
        assertEquals(6.49, stable!!.price, 0.001)
        assertEquals("Lapte", stable.title)
    }

    @Test
    fun titleIsTheMostFrequentForThePrice() {
        val stabilizer = DetectionStabilizer()
        stabilizer.add(frame(6.49, "Lapte integral"))
        stabilizer.add(frame(6.49, "Lapte integral"))
        val stable = stabilizer.add(frame(6.49, "Lapte"))
        assertEquals("Lapte integral", stable!!.title)
    }

    @Test
    fun noiseFrameDoesNotBreakStability() {
        val stabilizer = DetectionStabilizer()
        stabilizer.add(frame(4.25))
        stabilizer.add(frame(null))
        val stable = stabilizer.add(frame(4.25))
        assertEquals(4.25, stable!!.price, 0.001)
    }

    @Test
    fun disarmBlocksAutoOpenUntilEnoughEmptyFrames() {
        val stabilizer = DetectionStabilizer(rearmAfterEmptyFrames = 3)
        assertTrue(stabilizer.isArmed)
        stabilizer.disarm()
        assertFalse(stabilizer.isArmed)

        repeat(2) { stabilizer.add(frame(null)) }
        assertFalse(stabilizer.isArmed)

        stabilizer.add(frame(null))
        assertTrue(stabilizer.isArmed)
    }

    @Test
    fun disarmClearsHistory() {
        val stabilizer = DetectionStabilizer()
        stabilizer.add(frame(6.49))
        stabilizer.disarm()
        assertNull(stabilizer.add(frame(6.49)))
    }
}
