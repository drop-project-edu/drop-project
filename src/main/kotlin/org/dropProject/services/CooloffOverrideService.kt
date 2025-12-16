/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2025 Pedro Alves
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
package org.dropproject.services

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing temporary cooloff overrides.
 * Allows teachers to temporarily disable cooloff periods for assignments.
 * All data is stored in-memory and will be lost on server restart.
 */
@Service
class CooloffOverrideService {

    private val overrides = ConcurrentHashMap<String, CooloffOverrideInfo>()

    data class CooloffOverrideInfo(
        val teacherId: String,
        val expiryTime: LocalDateTime,
        val createdAt: LocalDateTime = LocalDateTime.now()
    )

    /**
     * Disables cooloff for an assignment for the specified duration.
     *
     * @param assignmentId The assignment ID
     * @param teacherId The teacher who is disabling cooloff
     * @param durationMinutes The duration in minutes (typically 15, 30, or 60)
     * @return The created override information
     */
    fun disableCooloff(assignmentId: String, teacherId: String, durationMinutes: Int): CooloffOverrideInfo {
        val info = CooloffOverrideInfo(
            teacherId = teacherId,
            expiryTime = LocalDateTime.now().plusMinutes(durationMinutes.toLong())
        )
        overrides[assignmentId] = info
        return info
    }

    /**
     * Re-enables cooloff for an assignment (removes the override).
     *
     * @param assignmentId The assignment ID
     */
    fun enableCooloff(assignmentId: String) {
        overrides.remove(assignmentId)
    }

    /**
     * Checks if cooloff is currently disabled for an assignment.
     *
     * @param assignmentId The assignment ID
     * @return true if cooloff is disabled and not expired, false otherwise
     */
    fun isDisabled(assignmentId: String): Boolean {
        val info = overrides[assignmentId] ?: return false
        return LocalDateTime.now().isBefore(info.expiryTime)
    }

    /**
     * Gets the override information for an assignment if it exists and is not expired.
     *
     * @param assignmentId The assignment ID
     * @return The override info if active, null otherwise
     */
    fun getOverrideInfo(assignmentId: String): CooloffOverrideInfo? {
        val info = overrides[assignmentId] ?: return null
        return if (LocalDateTime.now().isBefore(info.expiryTime)) info else null
    }

    /**
     * Gets the remaining minutes until the cooloff override expires.
     *
     * @param assignmentId The assignment ID
     * @return The remaining minutes, or null if no active override
     */
    fun getRemainingMinutes(assignmentId: String): Long? {
        val info = getOverrideInfo(assignmentId) ?: return null
        return ChronoUnit.MINUTES.between(LocalDateTime.now(), info.expiryTime)
    }

    /**
     * Scheduled task that runs every minute to clean up expired overrides.
     * Prevents memory leaks from expired entries.
     */
    @Scheduled(fixedRate = 60_000)
    fun cleanExpiredOverrides() {
        val now = LocalDateTime.now()
        overrides.entries.removeIf { it.value.expiryTime.isBefore(now) }
    }
}
