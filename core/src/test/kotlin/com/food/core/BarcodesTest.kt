package com.food.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodesTest {
    @Test fun `valid EAN-13 and EAN-8 pass`() {
        assertEquals("4006381333931", Barcodes.normalize("4006381333931"))
        assertEquals("96385074", Barcodes.normalize("96385074"))
    }

    @Test fun `a wrong check digit is a misread`() {
        assertNull(Barcodes.normalize("4006381333932"))
        assertNull(Barcodes.normalize("96385075"))
    }

    @Test fun `UPC-A becomes EAN-13`() {
        assertEquals("0036000291452", Barcodes.normalize("036000291452"))
    }

    @Test fun `typed numbers may have spaces`() {
        assertEquals("4006381333931", Barcodes.normalize(" 4 006381 333931 "))
    }

    @Test fun `other lengths and letters are rejected`() {
        assertNull(Barcodes.normalize(""))
        assertNull(Barcodes.normalize("12345"))
        assertNull(Barcodes.normalize("40063813339A1"))
    }
}
