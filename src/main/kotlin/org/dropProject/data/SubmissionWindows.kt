/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2026 Pedro Alves
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.dropproject.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The periods of time in which students may submit to an assignment, written as a list of windows separated by commas
 * or line breaks, e.g. "2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00". Each window is a day, followed by the time
 * it opens and the time it closes, in the server's time zone. A window is open from the minute it opens (inclusive)
 * until the minute it closes (exclusive), and it can't go past midnight.
 *
 * Submissions are still accepted for [CLOSING_TOLERANCE] after a window closes, for the students who submit at the
 * last minute and are delayed by the build of their previous submission or by the network. The students are only told
 * the time it closes, so that they don't count on the tolerance.
 *
 * This is meant for assignments that are solved during a class, when the same assignment is given to several classes
 * at different times: each class has its own window, and windows may overlap.
 *
 * The text is how the teachers write the windows (in the assignment form and over MCP) and how they are exported. They
 * are stored as [org.dropproject.dao.SubmissionWindow]s.
 */
class SubmissionWindows private constructor(
    // sorted by the time they open, and then by the time they close
    val windows: List<Window>
) {

    data class Window(val day: LocalDate, val opens: LocalTime, val closes: LocalTime) {

        val start: LocalDateTime get() = day.atTime(opens)
        val end: LocalDateTime get() = day.atTime(closes)

        /**
         * Whether submissions are accepted at [moment], i.e. whether it is between the time the window opens and the
         * time it closes, plus the [CLOSING_TOLERANCE].
         */
        fun contains(moment: LocalDateTime) = !moment.isBefore(start) && moment.isBefore(end.plus(CLOSING_TOLERANCE))

        override fun toString() = "${day} ${opens.format(TIME)}-${closes.format(TIME)}"
    }

    /**
     * Whether the windows are open at some moment and, either way, until when or when they open next.
     *
     * @property current is the window that is open, or null if none is
     * @property next is the first window that opens later, or null if there are no more windows
     */
    data class Status(val current: Window?, val next: Window?) {

        val open: Boolean get() = current != null

        /**
         * A summary for the teachers, e.g. "Open now, until 11:00 (+10 min tolerance)" or
         * "Closed now. Next window: 2026-11-03 14:00-16:00"
         */
        val description: String get() = when {
            current != null ->
                "Open now, until ${current.closes.format(TIME)} (+${CLOSING_TOLERANCE.toMinutes()} min tolerance)"
            next != null -> "Closed now. Next window: ${next}"
            else -> "Closed. There are no more windows"
        }
    }

    fun status(moment: LocalDateTime) = Status(current(moment), next(moment))

    /**
     * Whether [moment] is inside any of the windows.
     */
    fun contains(moment: LocalDateTime) = windows.any { it.contains(moment) }

    /**
     * The window that contains [moment], or null if there is none. When overlapping windows contain it, it is the one
     * that closes last, since submissions are accepted until then.
     */
    fun current(moment: LocalDateTime): Window? = windows.filter { it.contains(moment) }.maxByOrNull { it.end }

    /**
     * The first window that opens after [moment], or null if there are no more windows.
     */
    fun next(moment: LocalDateTime): Window? = windows.firstOrNull { it.start.isAfter(moment) }

    /**
     * The normalized form of the list, sorted and separated by commas,
     * e.g. "2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00" for "2026-11-03 14:00 - 16:00\n2026-11-03 9:00-11:00".
     */
    override fun toString() = windows.joinToString(", ")

    companion object {

        /**
         * How long submissions are still accepted after a window closes.
         */
        val CLOSING_TOLERANCE: Duration = Duration.ofMinutes(10)

        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val ORDER = compareBy<Window>({ it.start }, { it.end })
        private val WINDOW = Regex("""(\d{4}-\d{2}-\d{2})\s+(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})""")

        /**
         * @throws IllegalArgumentException naming the first window of [text] that is not valid
         */
        fun parse(text: String): SubmissionWindows {
            val entries = text.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
            require(entries.isNotEmpty()) { "Submission windows must have at least one window" }

            val windows = entries.map { entry ->
                val window = parseWindow(entry)
                require(window != null) {
                    "'$entry' is not a window with a day, an opening time and a closing time " +
                            "(e.g. 2026-11-03 09:00-11:00)"
                }
                require(window.opens.isBefore(window.closes)) {
                    "'$entry' closes before it opens (a window can't go past midnight)"
                }
                window
            }

            return SubmissionWindows(windows.sortedWith(ORDER))
        }

        /**
         * The windows in [windows], which are assumed to be valid (e.g. because they were stored after being parsed),
         * or null if there are none, i.e. if students may submit at any time.
         */
        fun of(windows: List<Window>): SubmissionWindows? =
            if (windows.isEmpty()) null else SubmissionWindows(windows.sortedWith(ORDER))

        // the window written in [entry], or null if it doesn't have the expected format or isn't a valid date or time
        private fun parseWindow(entry: String): Window? {
            val (day, opens, closes) = WINDOW.matchEntire(entry)?.destructured ?: return null
            return try {
                Window(LocalDate.parse(day), parseTime(opens), parseTime(closes))
            } catch (e: DateTimeParseException) {
                null
            }
        }

        // accepts hours with one digit, e.g. 9:00
        private fun parseTime(text: String): LocalTime = LocalTime.parse(text.padStart(5, '0'))
    }
}
