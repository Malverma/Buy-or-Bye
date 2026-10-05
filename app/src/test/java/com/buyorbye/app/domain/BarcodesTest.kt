package com.buyorbye.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodesTest {
    @Test
    fun expandsUpcE() {
        assertEquals("042100005264", Barcodes.normalize("04252614", isUpcE = true))
        // 6-digit form: number system 0 assumed, check digit computed.
        assertEquals("042100005264", Barcodes.normalize("425261", isUpcE = true))
    }

    @Test
    fun usEan13BecomesUpcA() {
        assertEquals("038000138416", Barcodes.normalize("0038000138416"))
    }

    @Test
    fun leavesOtherCodesAlone() {
        assertEquals("038000138416", Barcodes.normalize("038000138416"))
        assertEquals("4006381333931", Barcodes.normalize("4006381333931"))
    }
}
