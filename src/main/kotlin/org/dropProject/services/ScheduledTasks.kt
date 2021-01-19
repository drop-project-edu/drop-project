/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 Pedro Alves
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
package org.dropProject.services

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.dropProject.dao.SubmissionStatus
import org.dropProject.repository.SubmissionRepository
import org.slf4j.LoggerFactory
import java.util.*
import java.util.logging.Logger

/**
 * Contains functionality related with scheduled tasks (tasks that are executed with a certain regularity; for example,
 * cleaning expired submissions).
 */
@Component
class ScheduledTasks(
        val submissionRepository: SubmissionRepository
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @Scheduled(fixedRate = 6_000_000)
    fun cleanExpiredSubmissions() {

        LOG.info("Checking expired submissions")

        // check for processes in the submitted state (i.e., pending validation) that were submitted more than 1 hour ago
        val sometimeAgo = Date(System.currentTimeMillis() - 3600 * 1000)

        val expiredSubmissions = submissionRepository.findByStatusAndStatusDateBefore(SubmissionStatus.SUBMITTED.code, sometimeAgo)
        for (expiredSubmission in expiredSubmissions) {
            LOG.info("Cleaning up expired submission ${expiredSubmission.id} submitted at ${expiredSubmission.statusDate}")
            expiredSubmission.setStatus(SubmissionStatus.ABORTED_BY_TIMEOUT)
            submissionRepository.save(expiredSubmission)
        }
    }
}
