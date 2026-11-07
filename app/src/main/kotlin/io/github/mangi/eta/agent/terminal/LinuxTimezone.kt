package io.github.mangi.eta.agent.terminal

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.time.zone.ZoneOffsetTransitionRule
import java.util.TimeZone
import kotlin.math.abs

/** Environment only: never installs tzdata or changes the guest's /etc/localtime. */
internal object LinuxTimezone {
    data class Spec(val id: String, val posix: String)

    fun current(now: Instant = Instant.now()): Spec {
        val timezone = TimeZone.getDefault()
        val zone = runCatching { timezone.toZoneId() }.getOrElse {
            ZoneOffset.ofTotalSeconds(timezone.getOffset(now.toEpochMilli()) / 1000)
        }
        return Spec(timezone.id, posix(zone, now))
    }

    fun posix(zone: ZoneId, now: Instant): String {
        val rules = zone.rules
        val current = rules.getOffset(now)
        fun fixed() = abbreviation(current) + offset(-current.totalSeconds)
        val transitions = rules.transitionRules
        if (transitions.size != 2) return fixed()
        val start = transitions.singleOrNull { it.offsetBefore == it.standardOffset && it.offsetAfter != it.standardOffset } ?: return fixed()
        val end = transitions.singleOrNull { it.offsetAfter == it.standardOffset && it.offsetBefore != it.standardOffset } ?: return fixed()
        if (start.standardOffset != end.standardOffset || start.offsetAfter != end.offsetBefore) return fixed()
        val startRule = monthRule(start) ?: return fixed()
        val endRule = monthRule(end) ?: return fixed()
        val standard = start.standardOffset
        val summer = start.offsetAfter
        val summerOffset = if (summer.totalSeconds == standard.totalSeconds + 3600) "" else offset(-summer.totalSeconds)
        return abbreviation(standard) + offset(-standard.totalSeconds) + abbreviation(summer) + summerOffset + ",$startRule,$endRule"
    }

    private fun monthRule(rule: ZoneOffsetTransitionRule): String? {
        // Java resolves UTC/STANDARD/WALL and midnight-end-of-day into the pre-transition
        // wall clock required by POSIX. Check a Gregorian cycle before claiming equivalence.
        val dates = (2000 until 2400).map { rule.createTransition(it).dateTimeBefore }
        val first = dates.first()
        if (dates.any { it.month != first.month || it.dayOfWeek != first.dayOfWeek || it.toLocalTime() != first.toLocalTime() }) return null
        val week = (1..5).firstOrNull { candidate -> dates.all { actual ->
            val month = LocalDate.of(actual.year, actual.month, 1)
            val expected = month.with(if (candidate == 5) TemporalAdjusters.lastInMonth(actual.dayOfWeek)
                else TemporalAdjusters.dayOfWeekInMonth(candidate, actual.dayOfWeek))
            actual.toLocalDate() == expected
        } } ?: return null
        val time = first.toLocalTime()
        val suffix = if (time == LocalTime.of(2, 0)) "" else "/${offset(time.toSecondOfDay())}"
        return "M${first.monthValue}.$week.${first.dayOfWeek.value % 7}$suffix"
    }

    private fun abbreviation(value: ZoneOffset): String {
        val seconds = abs(value.totalSeconds)
        val hours = (seconds / 3600).toString().padStart(2, '0')
        val minutes = if (seconds % 3600 == 0) "" else (seconds / 60 % 60).toString().padStart(2, '0')
        val remainder = if (seconds % 60 == 0) "" else (seconds % 60).toString().padStart(2, '0')
        return "<${if (value.totalSeconds < 0) "-" else "+"}$hours$minutes$remainder>"
    }

    private fun offset(totalSeconds: Int): String {
        val seconds = abs(totalSeconds)
        val minutes = if (seconds % 3600 == 0) "" else ":" + (seconds / 60 % 60).toString().padStart(2, '0')
        val remainder = if (seconds % 60 == 0) "" else ":" + (seconds % 60).toString().padStart(2, '0')
        return "${if (totalSeconds < 0) "-" else ""}${seconds / 3600}$minutes$remainder"
    }
}
