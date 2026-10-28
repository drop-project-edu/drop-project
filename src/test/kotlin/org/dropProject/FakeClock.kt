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
package org.dropproject

import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Test seam for the application's [Clock]: tests can fix the current time with [setNow], so that what depends on it
 * (e.g. the submission windows of an assignment) doesn't depend on when the tests run. Until then, it is the system
 * clock.
 *
 * The fixed time is cleared after each test by [ResetStateExtension].
 */
@Component
@Primary
class FakeClock : Clock() {

    private val systemClock = Clock.systemDefaultZone()
    private var fixedNow: Instant? = null

    fun setNow(now: LocalDateTime) {
        fixedNow = now.atZone(zone).toInstant()
    }

    fun reset() {
        fixedNow = null
    }

    override fun getZone(): ZoneId = systemClock.zone

    override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()

    override fun instant(): Instant = fixedNow ?: systemClock.instant()
}
