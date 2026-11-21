package com.personal.cardvault.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class BinLookupTest {

    private fun createEngine(): BinLookupEngine {
        val assetsRoot = File("src/main/assets")
        return BinLookupEngine { relPath ->
            val file = File(assetsRoot, relPath)
            if (file.exists()) FileInputStream(file) else null
        }
    }

    @Test
    fun lookup_icbcUnionPayDebit_returnsChineseIssuer() {
        val engine = createEngine()
        val info = engine.lookup("6212 2602 0000 4321")
        assertNotNull("Expected 621226 to resolve", info)
        assertEquals("621226", info!!.bin)
        assertEquals("UNIONPAY", info.brand)
        assertEquals("中国工商银行", info.issuerDisplay)
    }

    @Test
    fun lookup_cmbMasterCard_returnsChineseIssuerAndBrand() {
        val engine = createEngine()
        val info = engine.lookup("5240113456788899")
        assertNotNull("Expected 524011 to resolve", info)
        assertEquals("MASTERCARD", info!!.brand)
        assertEquals("招商银行", info.issuerDisplay)
    }

    @Test
    fun lookup_shortInput_returnsNull() {
        val engine = createEngine()
        assertNull(engine.lookup("62122"))
        assertNull(engine.lookup(""))
    }
}
