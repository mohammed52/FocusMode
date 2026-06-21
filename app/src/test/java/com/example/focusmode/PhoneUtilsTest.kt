package com.example.focusmode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUtilsTest {

    @Test
    fun exactDigitsMatch() {
        assertTrue(phoneNumbersMatch("9876543210", "9876543210"))
    }

    @Test
    fun formattingCharactersAreIgnored() {
        assertTrue(phoneNumbersMatch("(987) 654-3210", "987 654 3210"))
    }

    @Test
    fun incomingNumberWithCountryCodeMatchesContactWithoutIt() {
        // India: 10-digit local number vs +91-prefixed caller ID
        assertTrue(phoneNumbersMatch("9876543210", "+919876543210"))
    }

    @Test
    fun trunkPrefixDroppedWhenCountryCodeAdded() {
        // Australia: contact saved with leading trunk 0, caller ID has +61 instead
        assertTrue(phoneNumbersMatch("0412345678", "+61412345678"))
    }

    @Test
    fun internationalAccessCodeFormatMatches() {
        // 00<country code> dialing format vs local trunk format
        assertTrue(phoneNumbersMatch("0061412345678", "0412345678"))
    }

    @Test
    fun differentNumbersDoNotMatch() {
        assertFalse(phoneNumbersMatch("9876543210", "9876543211"))
    }

    @Test
    fun shortNumbersRequireExactMatch() {
        assertTrue(phoneNumbersMatch("123", "123"))
        assertFalse(phoneNumbersMatch("123", "456"))
        // "123" ends with "123" but is below the minimum match length, so it must not
        // be treated as a match against an unrelated longer number that happens to end in it.
        assertFalse(phoneNumbersMatch("123", "9999123"))
    }

    @Test
    fun blankOrNonNumericInputNeverMatches() {
        assertFalse(phoneNumbersMatch("", ""))
        assertFalse(phoneNumbersMatch("John Doe", "9876543210"))
    }
}
