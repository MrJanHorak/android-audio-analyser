package com.example.audioanalyser

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MeterReadingFormatTest {
    @Test
    fun suppressedValuesAreNotPresentedAsMeasuredSilence() {
        assertEquals("Below range (A)", formatMeterReading(0f, "A", Locale.US))
        assertEquals("dB(A): —", formatMeterReading(0f, "A", Locale.US, compact = true))
    }

    @Test
    fun positiveMeasurementsKeepTheirWeightingAndLocale() {
        assertEquals("70.9 dB(Z)", formatMeterReading(70.9f, "Z", Locale.US))
        assertEquals("dB(C): 70,6", formatMeterReading(70.6f, "C", Locale.GERMANY, compact = true))
    }
}
