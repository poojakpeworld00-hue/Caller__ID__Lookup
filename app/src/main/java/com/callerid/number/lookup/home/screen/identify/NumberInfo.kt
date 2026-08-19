package com.callerid.number.lookup.home.screen.identify

object NumberInfo {

    private val regions: Map<String, String> = linkedMapOf(
        "+1" to "United States / Canada",
        "+44" to "United Kingdom",
        "+91" to "India",
        "+92" to "Pakistan",
        "+880" to "Bangladesh",
        "+971" to "United Arab Emirates",
        "+966" to "Saudi Arabia",
        "+61" to "Australia",
        "+33" to "France",
        "+49" to "Germany",
        "+34" to "Spain",
        "+39" to "Italy",
        "+55" to "Brazil",
        "+52" to "Mexico",
        "+81" to "Japan",
        "+86" to "China",
        "+7" to "Russia / Kazakhstan",
        "+27" to "South Africa",
        "+234" to "Nigeria",
        "+62" to "Indonesia",
        "+63" to "Philippines",
        "+90" to "Türkiye",
        "+20" to "Egypt"
    )

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val plus = if (trimmed.startsWith("+")) "+" else ""
        return plus + trimmed.filter { it.isDigit() }
    }

    fun regionCode(normalized: String): String {
        if (!normalized.startsWith("+")) return ""

        for (len in 4 downTo 2) {
            val prefix = normalized.take(len)
            if (regions.containsKey(prefix)) return prefix
        }
        return ""
    }

    fun regionName(normalized: String): String? {
        val code = regionCode(normalized)
        return regions[code]
    }

    fun format(normalized: String): String {
        if (normalized.isEmpty()) return normalized
        val hasPlus = normalized.startsWith("+")
        val digits = normalized.filter { it.isDigit() }
        if (digits.length < 7) return normalized

        val tail = digits.takeLast(10)
        val country = digits.dropLast(10)
        val grouped = when (tail.length) {
            10 -> "${tail.substring(0, 3)} ${tail.substring(3, 6)} ${tail.substring(6)}"
            else -> tail
        }
        val prefix = if (hasPlus) "+$country " else if (country.isNotEmpty()) "$country " else ""
        return (prefix + grouped).trim()
    }
}
