package com.mohdshayan.inkhorn.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Numbers written the way a ledger writes them: "three", "forty-two", "Chapter six". */
object NumberWords {
    private val small = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val tens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    /** Words for 0 to 999,999; larger numbers fall back to grouped digits. */
    fun words(n: Int): String {
        if (n == Int.MIN_VALUE) return Numbers.whole(n.toDouble())
        if (n < 0) return "minus " + words(-n)
        if (n < 20) return small[n]
        if (n < 100) return tens[n / 10] + if (n % 10 != 0) "-" + small[n % 10] else ""
        if (n < 1000) {
            val rest = n % 100
            return small[n / 100] + " hundred" + if (rest != 0) " and " + words(rest) else ""
        }
        if (n < 1_000_000) {
            val rest = n % 1000
            val joiner = if (rest in 1..99) " and " else " "
            return words(n / 1000) + " thousand" + if (rest != 0) joiner + words(rest) else ""
        }
        return Numbers.whole(n.toDouble())
    }

    fun chapterName(n: Int): String = "Chapter " + words(n)

    private val ordinals = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth")

    /** "The second chronicle"; past the twelfth, "Chronicle 14". */
    fun chronicleName(n: Int): String =
        if (n in 1..ordinals.size) "The ${ordinals[n - 1]} chronicle" else "Chronicle $n"

    /** The run after [n] chronicles, for "Begin a second chronicle" and "Began the third chronicle". */
    fun nextChronicle(n: Int): String = when {
        n + 1 <= 2 -> "a second chronicle"
        n + 1 <= ordinals.size -> chronicleName(n + 1).replaceFirstChar { it.lowercase() }
        else -> "chronicle ${n + 1}"
    }

    /** "one day’s", "three days’", or "part of a day’s" for under a game day. */
    fun days(gameDays: Double): String {
        val d = floor(gameDays + 1e-9).toInt()
        return when {
            d <= 0 -> "part of a day’s"
            d == 1 -> "one day’s"
            else -> words(d) + " days’"
        }
    }

    /** "two hours", "one hour", "forty minutes". */
    fun duration(seconds: Double): String {
        val minutes = (seconds / 60.0).roundToLong().toInt()
        if (minutes < 60) return if (minutes == 1) "one minute" else words(minutes.coerceAtLeast(0)) + " minutes"
        val hours = minutes / 60
        return if (hours == 1) "one hour" else words(hours) + " hours"
    }
}

/** "a", "a and b", "a, b and c". */
fun List<String>.joinNaturally(): String = when (size) {
    0 -> ""
    1 -> first()
    else -> dropLast(1).joinToString(", ") + " and " + last()
}

/** Figures for store rows and costs. Locale independent so a save reads the same everywhere. */
object Numbers {
    fun whole(v: Double): String {
        val n = floor(v + 1e-9)
        if (abs(n) >= 10_000_000) {
            val m = n / 1_000_000.0
            return oneDecimal(m) + " million"
        }
        val digits = n.toLong().toString()
        val neg = digits.startsWith("-")
        val body = if (neg) digits.substring(1) else digits
        val grouped = body.reversed().chunked(3).joinToString(",").reversed()
        return if (neg) "-$grouped" else grouped
    }

    /** A store count: tenths below ten, so a fifth of a folio shows as 0.2, whole figures above. */
    fun store(v: Double): String {
        if (v < 10.0) {
            val tenths = kotlin.math.floor(v * 10 + 1e-6) / 10
            return if (tenths == kotlin.math.floor(tenths)) tenths.toLong().toString() else oneDecimal(tenths)
        }
        return whole(v)
    }

    fun oneDecimal(v: Double): String {
        val tenths = (v * 10).roundToLong()
        val sign = if (tenths < 0) "-" else ""
        val whole = abs(tenths / 10)
        val frac = abs(tenths % 10)
        return if (frac == 0L) "$sign$whole" else "$sign$whole.$frac"
    }

    /** A per-second rate as "3 a minute" or "0.4 a minute". */
    fun perMinute(perSecond: Double): String {
        val m = abs(perSecond * 60.0)
        val figure = if (m >= 10) whole(m) else oneDecimal(m)
        return if (perSecond < 0) "falling $figure a minute" else "$figure a minute"
    }

    /** "under a minute", "12 minutes", "1 hour 5 minutes", "7 hours 40 minutes". */
    fun playTime(seconds: Long): String {
        val minutes = seconds / 60
        if (minutes < 1) return "under a minute"
        val h = minutes / 60
        val m = minutes % 60
        val hPart = if (h == 1L) "1 hour" else "$h hours"
        val mPart = if (m == 1L) "1 minute" else "$m minutes"
        return when {
            h == 0L -> mPart
            m == 0L -> hPart
            else -> "$hPart $mPart"
        }
    }

    fun seconds(s: Double): String {
        val v = oneDecimal(s)
        return if (v == "1") "1 second" else "$v seconds"
    }
}
