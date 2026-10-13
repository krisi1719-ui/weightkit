package com.weightkit.app

import com.weightkit.app.model.defaultCurrency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DefaultCurrencyTest {
    @Test fun bulgariaUsesEuro() {
        assertEquals("€", defaultCurrency(Locale.forLanguageTag("bg-BG")))
        assertEquals("€", defaultCurrency(Locale.forLanguageTag("en-BG")))
        assertEquals("€", defaultCurrency(Locale.forLanguageTag("bg")))
    }

    @Test fun otherCountriesKeepTheirCurrency() {
        assertEquals("€", defaultCurrency(Locale.GERMANY))
        assertEquals("$", defaultCurrency(Locale.US))
    }
}
