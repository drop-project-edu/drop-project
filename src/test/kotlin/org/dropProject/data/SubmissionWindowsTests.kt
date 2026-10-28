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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class SubmissionWindowsTests {

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test
    fun `a window is open from the time it opens until the time it closes, plus the tolerance`() {
        val windows = SubmissionWindows.parse("2026-11-03 09:00-11:00")

        assertFalse(windows.contains(at("2026-11-03T08:59:59")))
        assertTrue(windows.contains(at("2026-11-03T09:00:00")))
        assertTrue(windows.contains(at("2026-11-03T10:59:59")))
        assertTrue(windows.contains(at("2026-11-03T11:00:00")))
        assertTrue(windows.contains(at("2026-11-03T11:09:59")))
        assertFalse(windows.contains(at("2026-11-03T11:10:00")))

        // the same times on another day are not inside the window
        assertFalse(windows.contains(at("2026-11-04T10:00:00")))
    }

    @Test
    fun `the windows are normalized and sorted`() {
        val windows = SubmissionWindows.parse("2026-11-04 14:00 - 16:00\n2026-11-03 9:00-11:00 ,  2026-11-03 14:00-16:00")

        assertEquals("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00, 2026-11-04 14:00-16:00", windows.toString())
    }

    @Test
    fun `the current window is the one that closes last`() {
        val windows = SubmissionWindows.parse("2026-11-03 09:00-11:00, 2026-11-03 10:00-12:00")

        assertEquals("2026-11-03 10:00-12:00", windows.current(at("2026-11-03T10:30:00")).toString())
        assertEquals("2026-11-03 09:00-11:00", windows.current(at("2026-11-03T09:30:00")).toString())
        assertEquals("2026-11-03 10:00-12:00", windows.current(at("2026-11-03T12:05:00")).toString())
        assertNull(windows.current(at("2026-11-03T12:10:00")))
    }

    @Test
    fun `the next window is the first one that opens later`() {
        val windows = SubmissionWindows.parse("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00")

        assertEquals("2026-11-03 09:00-11:00", windows.next(at("2026-11-02T20:00:00")).toString())
        assertEquals("2026-11-03 14:00-16:00", windows.next(at("2026-11-03T10:00:00")).toString())
        assertNull(windows.next(at("2026-11-03T15:00:00")))
    }

    @Test
    fun `the status tells whether the windows are open and until when or when they open next`() {
        val windows = SubmissionWindows.parse("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00")

        val open = windows.status(at("2026-11-03T10:00:00"))
        assertTrue(open.open)
        assertEquals("Open now, until 11:00 (+10 min tolerance)", open.description)

        // during the tolerance, the students are still told the time it closes
        assertEquals("Open now, until 11:00 (+10 min tolerance)", windows.status(at("2026-11-03T11:05:00")).description)

        val closed = windows.status(at("2026-11-03T12:00:00"))
        assertFalse(closed.open)
        assertEquals("Closed now. Next window: 2026-11-03 14:00-16:00", closed.description)

        assertEquals("Closed. There are no more windows", windows.status(at("2026-11-03T16:10:00")).description)
    }

    @Test
    fun `invalid windows are rejected with the entry that is wrong`() {
        assertEquals("'2026-11-03 09:00' is not a window with a day, an opening time and a closing time " +
                "(e.g. 2026-11-03 09:00-11:00)",
            assertThrows<IllegalArgumentException> {
                SubmissionWindows.parse("2026-11-03 14:00-16:00, 2026-11-03 09:00")
            }.message)

        assertThrows<IllegalArgumentException> { SubmissionWindows.parse("2026-02-30 09:00-11:00") }
        assertThrows<IllegalArgumentException> { SubmissionWindows.parse("2026-11-03 25:00-26:00") }
        assertThrows<IllegalArgumentException> { SubmissionWindows.parse("03/11/2026 09:00-11:00") }
        assertThrows<IllegalArgumentException> { SubmissionWindows.parse(" , ") }

        assertEquals("'2026-11-03 23:00-01:00' closes before it opens (a window can't go past midnight)",
            assertThrows<IllegalArgumentException> { SubmissionWindows.parse("2026-11-03 23:00-01:00") }.message)
        assertThrows<IllegalArgumentException> { SubmissionWindows.parse("2026-11-03 09:00-09:00") }
    }
}
