package com.example.focusmode

private const val MIN_MATCH_DIGITS = 7

private fun digitsOnly(s: String): String = s.replace(Regex("[^0-9]"), "").trimStart('0')

// Suffix match rather than a fixed-length comparison: country-code length and trunk-prefix
// ("0" before the area code) conventions vary by country, so a contact saved locally and the
// same number arriving with a country code on caller ID won't line up at any fixed cut point.
// The trailing digits are the only part guaranteed to match.
fun phoneNumbersMatch(a: String, b: String): Boolean {
    val da = digitsOnly(a)
    val db = digitsOnly(b)
    if (da.isEmpty() || db.isEmpty()) return false
    if (da.length < MIN_MATCH_DIGITS || db.length < MIN_MATCH_DIGITS) return da == db
    return da.endsWith(db) || db.endsWith(da)
}
