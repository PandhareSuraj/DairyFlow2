package com.example.dairyflow2.core

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val rupeeFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("en", "IN"))

fun Long.asRupees(): String = rupeeFormat.format(this / 100.0)

fun Double.trimQuantity(): String {
    val rounded = roundToInt().toDouble()
    return if (this == rounded) rounded.toInt().toString() else String.format(Locale.US, "%.2f", this)
}

fun LocalDate.displayDate(): String = format(dateFormatter)

fun YearMonth.monthKey(): String = toString()

fun todayKey(): String = LocalDate.now().toString()

fun currentMonthKey(): String = YearMonth.now().monthKey()

