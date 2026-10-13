package com.mintake.shared

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DoseState { UPCOMING, DUE, TAKEN, NOT_TAKEN, SNOOZED }

/** One scheduled dose of a medicine on a given day. */
data class Dose(val medicine: Medicine, val date: LocalDate, val time: LocalTime) {
    val key: String get() = "${medicine.id}|$date|${KEY_TIME.format(time)}"

    fun epochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    private companion object {
        val KEY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

object Schedule {
    private const val MINUTES_PER_DAY = 24 * 60

    fun dosesOn(date: LocalDate, medicines: List<Medicine>): List<Dose> =
        medicines.filter { it.enabled }.flatMap { medicine ->
            val minutes = when (medicine.repeat) {
                RepeatType.DAILY -> listOf(medicine.startMinutes)
                RepeatType.WEEKDAYS ->
                    if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) emptyList()
                    else listOf(medicine.startMinutes)
                RepeatType.INTERVAL -> {
                    // The cycle restarts at the start time every day. Doses that fall past
                    // midnight (e.g. 8 AM + 16 h = 12 AM) happen in the early hours of each day.
                    val step = medicine.intervalHours.coerceIn(1, 24) * 60
                    (0 until MINUTES_PER_DAY step step)
                        .map { (medicine.startMinutes + it) % MINUTES_PER_DAY }
                        .distinct()
                }
            }
            minutes.map { Dose(medicine, date, LocalTime.of(it / 60, it % 60)) }
        }.sortedWith(compareBy<Dose>({ it.time }, { it.medicine.name }))

    fun dateOfKey(key: String): String = key.split("|").getOrElse(1) { "" }

    fun findDose(key: String, medicines: List<Medicine>): Dose? {
        val parts = key.split("|")
        if (parts.size != 3) return null
        val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
        return dosesOn(date, medicines).firstOrNull { it.key == key }
    }
}

fun AppState.stateOf(dose: Dose, nowMillis: Long = System.currentTimeMillis()): DoseState {
    val record = records[dose.key]
    return when (record?.status) {
        DoseStatus.TAKEN -> DoseState.TAKEN
        DoseStatus.NOT_TAKEN -> DoseState.NOT_TAKEN
        DoseStatus.SNOOZED ->
            if ((record?.snoozedUntil ?: 0L) > nowMillis) DoseState.SNOOZED else DoseState.DUE
        null -> if (dose.epochMillis() <= nowMillis) DoseState.DUE else DoseState.UPCOMING
    }
}

object Format {
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun time(time: LocalTime): String = timeFormat.format(time)

    fun time(epochMillis: Long): String =
        timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime())

    fun minutes(minutesAfterMidnight: Int): String {
        val m = ((minutesAfterMidnight % 1440) + 1440) % 1440
        return time(LocalTime.of(m / 60, m % 60))
    }

    fun stateLabel(state: DoseState, dose: Dose, record: DoseRecord?, nowMillis: Long): String = when (state) {
        DoseState.TAKEN -> "Taken"
        DoseState.NOT_TAKEN -> "Not taken"
        DoseState.SNOOZED -> "Snoozed · ${time(record?.snoozedUntil ?: nowMillis)}"
        DoseState.DUE -> if (nowMillis - dose.epochMillis() > 30 * 60_000L) "Overdue" else "Due now"
        DoseState.UPCOMING -> "Upcoming"
    }

    fun relative(thenMillis: Long?, nowMillis: Long): String = when {
        thenMillis == null -> "not yet"
        nowMillis - thenMillis < 60_000L -> "just now"
        nowMillis - thenMillis < 3_600_000L -> "${(nowMillis - thenMillis) / 60_000L} min ago"
        else -> time(thenMillis)
    }
}
