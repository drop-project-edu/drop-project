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
package org.dropproject.services

import org.dropproject.dao.Assignment
import org.dropproject.data.SubmissionWindows
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

/**
 * Decides, at the current time, whether students may submit to an assignment that has submission windows (see
 * [Assignment.submissionWindows]), and describes it to the students and to the teachers.
 */
@Service
class SubmissionWindowService(
    val clock: Clock,
    val i18n: MessageSource
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @Value("\${spring.web.locale}")
    val currentLocale : Locale = Locale.getDefault()

    /**
     * The status of the submission windows of [assignment] right now, or null if it has no windows (i.e. students may
     * submit at any time).
     */
    fun status(assignment: Assignment): SubmissionWindows.Status? =
        assignment.submissionWindowSchedule()?.status(LocalDateTime.now(clock))

    /**
     * The message that tells a student why they can't submit to [assignment] right now, because it is outside of all
     * its submission windows, or null if they can (which is always the case when it has no windows).
     */
    fun closedMessage(assignment: Assignment): String? {
        val status = status(assignment)
        if (status == null || status.open) {
            return null
        }

        return if (status.next != null) {
            i18n.getMessage("student.submit.window-closed-next", arrayOf(status.next.toString()), currentLocale)
        } else {
            i18n.getMessage("student.submit.window-closed", null, currentLocale)
        }
    }

    /**
     * @throws Assignment.SubmissionWindowClosedException if [assignment] doesn't accept submissions right now,
     * because it is outside of all its submission windows
     */
    fun check(assignment: Assignment) {
        val message = closedMessage(assignment) ?: return
        LOG.info("Refused a submission to ${assignment.id} outside of its submission windows")
        throw Assignment.SubmissionWindowClosedException(message)
    }
}
