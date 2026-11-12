package com.itsluminous.livescore

import java.util.Locale

/**
 * The badminton nations the picker always offers (ADR-051), by the IOC-style codes BWF's feed writes (`INA`, `TPE`,
 * `MAS`), each with the ISO country the platform can name in the TV's language (`Locale("", iso).displayCountry`). Four
 * BWF members are not ISO countries (Chinese Taipei, England, Scotland, Wales) and have no ISO code here: the picker
 * names them from string resources. A code outside this list (seen on a cached match) is named by the platform when it
 * knows it as an ISO alpha-3 code (`IND`, `JPN`, `KOR`), else shown as the code. Pure; `BadmintonNationsTest`.
 */
object BadmintonNations {
    /** IOC code → ISO 3166 alpha-2, the fifty-six nations of the World Tour and the continental circuits. */
    val ISO: Map<String, String> = linkedMapOf(
        "CHN" to "CN", "INA" to "ID", "JPN" to "JP", "DEN" to "DK", "MAS" to "MY", "KOR" to "KR", "THA" to "TH",
        "IND" to "IN", "HKG" to "HK", "SGP" to "SG", "FRA" to "FR", "GER" to "DE", "NED" to "NL", "ESP" to "ES",
        "CAN" to "CA", "USA" to "US", "VIE" to "VN", "BUL" to "BG", "SUI" to "CH", "IRL" to "IE", "SWE" to "SE",
        "FIN" to "FI", "UKR" to "UA", "POL" to "PL", "CZE" to "CZ", "AUT" to "AT", "BEL" to "BE", "ITA" to "IT",
        "POR" to "PT", "TUR" to "TR", "ISR" to "IL", "EGY" to "EG", "NGR" to "NG", "RSA" to "ZA", "AUS" to "AU",
        "NZL" to "NZ", "BRA" to "BR", "MEX" to "MX", "GUA" to "GT", "PER" to "PE", "MRI" to "MU", "SRI" to "LK",
        "PAK" to "PK", "NEP" to "NP", "KAZ" to "KZ", "UZB" to "UZ", "UAE" to "AE", "MAC" to "MO", "PHI" to "PH",
        "CRO" to "HR", "SLO" to "SI", "SVK" to "SK", "HUN" to "HU", "LTU" to "LT", "EST" to "EE", "NOR" to "NO",
    )

    /** The BWF members that are not ISO countries, named by the picker's own strings. */
    val NON_ISO: Set<String> = setOf("TPE", "ENG", "SCO", "WAL")

    /** Every code the picker lists before the cache adds any: the ISO map's and the four above. */
    val CODES: List<String> get() = ISO.keys.toList() + NON_ISO

    /**
     * The country's name in [locale] for the code [code] (upper-cased): from the ISO map, else the platform's own reading
     * of a three-letter code; null when the platform does not know it (a caller then shows the code) or the code is one
     * of [NON_ISO] (a caller names those itself).
     */
    fun name(code: String, locale: Locale = Locale.getDefault()): String? {
        val c = code.trim().uppercase()
        if (c.isEmpty() || c in NON_ISO) return null
        val iso = ISO[c] ?: c
        val name = Locale("", iso).getDisplayCountry(locale)
        return name.takeIf { it.isNotEmpty() && !it.equals(iso, ignoreCase = true) && !it.equals(c, ignoreCase = true) }
    }
}
