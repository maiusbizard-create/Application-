package com.example

import androidx.compose.ui.text.AnnotatedString
import com.example.util.IbanValidationState
import com.example.util.IbanValidator
import com.example.util.IbanVisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IbanValidatorTest {
    @Test
    fun validSepaIbansPassMod97() {
        assertTrue(IbanValidator.isValidSepaIban("DE89 3704 0044 0532 0130 00"))
        assertTrue(IbanValidator.isValidSepaIban("FR7630006000011234567890189"))
        assertTrue(IbanValidator.isValidSepaIban("BE68539007547034"))
        assertTrue(IbanValidator.isValidSepaIban("ES9121000418450200051332"))
        assertTrue(IbanValidator.isValidSepaIban("NL91ABNA0417164300"))
    }

    @Test
    fun invalidIbansFailMod97() {
        assertFalse(IbanValidator.isValidSepaIban("DE89370400440532013001"))
        assertFalse(IbanValidator.isValidSepaIban("FR76"))
        assertFalse(IbanValidator.isValidSepaIban("1234567890123456"))
    }

    @Test
    fun realtimeValidationReportsIncrementalLengthAndErrors() {
        assertEquals(IbanValidationState.Empty, IbanValidator.validateRealtime("   "))

        val badChars = IbanValidator.validateRealtime("FR76@3000")
        assertTrue(badChars is IbanValidationState.InvalidCharacters)
        assertTrue(badChars.isError)

        val badCountryPrefix = IbanValidator.validateRealtime("12AB")
        assertTrue(badCountryPrefix is IbanValidationState.InvalidPrefix)
        assertTrue(badCountryPrefix.isError)

        val badCheckDigits = IbanValidator.validateRealtime("FR7A3000")
        assertTrue(badCheckDigits is IbanValidationState.InvalidPrefix)
        assertTrue(badCheckDigits.isError)

        val incompleteFr = IbanValidator.validateRealtime("fr76 3000")
        assertTrue(incompleteFr is IbanValidationState.IncompleteLength)
        incompleteFr as IbanValidationState.IncompleteLength
        assertEquals(8, incompleteFr.currentLength)
        assertEquals(27, incompleteFr.expectedLength)
        assertEquals("France", incompleteFr.countryName)
        assertFalse(incompleteFr.isError)
        assertFalse(incompleteFr.isValid)

        val tooLongDe = IbanValidator.validateRealtime("DE893704004405320130001")
        assertTrue(tooLongDe is IbanValidationState.InvalidLengthExceeded)
        assertTrue(tooLongDe.isError)

        val badDeFormat = IbanValidator.validateRealtime("DE89ABCD00440532013000")
        assertTrue(badDeFormat is IbanValidationState.InvalidFormat)
        assertTrue(badDeFormat.isError)

        val badChecksum = IbanValidator.validateRealtime("DE89370400440532013001")
        assertTrue(badChecksum is IbanValidationState.InvalidChecksum)
        assertTrue(badChecksum.isError)

        val validFr = IbanValidator.validateRealtime("FR76 3000 6000 0112 3456 7890 189")
        assertTrue(validFr is IbanValidationState.Valid)
        assertTrue(validFr.isValid)
        assertEquals(27, validFr.currentLength)
        assertEquals("France", validFr.countryName)
    }

    @Test
    fun visualTransformationGroupsEveryFourCharsAndMapsOffsets() {
        val transformation = IbanVisualTransformation()
        val transformed = transformation.filter(AnnotatedString("FR76300060"))
        assertEquals("FR76 3000 60", transformed.text.text)

        assertEquals(0, transformed.offsetMapping.originalToTransformed(0))
        assertEquals(4, transformed.offsetMapping.originalToTransformed(4))
        assertEquals(6, transformed.offsetMapping.originalToTransformed(5))
        assertEquals(12, transformed.offsetMapping.originalToTransformed(10))

        assertEquals(4, transformed.offsetMapping.transformedToOriginal(4))
        assertEquals(4, transformed.offsetMapping.transformedToOriginal(5))
        assertEquals(5, transformed.offsetMapping.transformedToOriginal(6))
        assertEquals(10, transformed.offsetMapping.transformedToOriginal(12))
    }

    @Test
    fun euroParsingAndMinimumTenEuros() {
        assertEquals(1000L, IbanValidator.parseEuroToCents("10"))
        assertEquals(1050L, IbanValidator.parseEuroToCents("10,50"))
        assertEquals(2599L, IbanValidator.parseEuroToCents("25.99"))
        assertNull(IbanValidator.parseEuroToCents("0"))
        assertNull(IbanValidator.parseEuroToCents("10.123"))
        assertTrue((IbanValidator.parseEuroToCents("9.99") ?: 0L) < IbanValidator.MIN_SEPA_AMOUNT_CENTS)
    }
}

