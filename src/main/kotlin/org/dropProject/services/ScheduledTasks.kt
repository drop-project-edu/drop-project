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
package org.dropproject.services

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.dropproject.dao.SubmissionStatus
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.RebuildStatusRepository
import org.dropproject.repository.SubmissionRepository
import org.slf4j.LoggerFactory
import org.dropproject.config.DropProjectProperties
import org.dropproject.config.PendingExport
import org.dropproject.config.PendingMultipleExports
import org.dropproject.config.PendingTasks
import java.io.File
import java.util.*
import java.util.logging.Logger

/**
 * Contains functionality related with scheduled tasks (tasks that are executed with a certain regularity; for example,
 * cleaning expired submissions).
 */
@Component
class ScheduledTasks(
        val submissionRepository: SubmissionRepository,
        val rebuildStatusRepository: RebuildStatusRepository,
        val assignmentRepository: AssignmentRepository,
        val gitClient: GitClient,
        val dropProjectProperties: DropProjectProperties,
        val pendingTasks: PendingTasks
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    // run every 100 minutes
    @Scheduled(fixedRate = 6_000_000)
    fun cleanExpiredSubmissions() {

        LOG.info("Checking expired submissions")

        val sometimeAgo = Date(System.currentTimeMillis() - 3600 * 1000)

        // check for processes in the submitted (or submitted-for-rebuild) state that were left in that state for
        // more than 1 hour. Both of these have an accurate statusDate, since it's set when they're created.
        val pendingStatuses = listOf(SubmissionStatus.SUBMITTED.code, SubmissionStatus.SUBMITTED_FOR_REBUILD.code)
        val expiredSubmissions = submissionRepository.findByStatusInAndStatusDateBefore(pendingStatuses, sometimeAgo)
        for (expiredSubmission in expiredSubmissions) {
            LOG.info("Cleaning up expired submission ${expiredSubmission.id} submitted at ${expiredSubmission.statusDate}")
            expiredSubmission.setStatus(SubmissionStatus.ABORTED_BY_TIMEOUT)
            submissionRepository.save(expiredSubmission)
        }

        // separately check for rebuilds that were left running for more than 1 hour. These can't be detected through
        // Submission.statusDate, since entering the REBUILDING status deliberately doesn't update it (so that
        // "rebuild without changing anything" doesn't affect the submission's visible date). RebuildStatus tracks
        // the actual rebuild start time instead.
        val expiredRebuilds = rebuildStatusRepository.findByStartedAtBefore(sometimeAgo)
        for (expiredRebuild in expiredRebuilds) {
            val submission = expiredRebuild.submission
            if (submission.getStatus() == SubmissionStatus.REBUILDING) {
                LOG.info("Cleaning up expired rebuild of submission ${submission.id} started at ${expiredRebuild.startedAt}")
                submission.setStatus(SubmissionStatus.ABORTED_BY_TIMEOUT)
                submissionRepository.save(submission)
            }
            rebuildStatusRepository.deleteBySubmissionId(submission.id)
        }
    }

    /**
     * Refreshes the SSH keys for all assignments. This is useful to prevent GitHub from deleting unused SSH keys.
     *
     * @return the number of assignments for which the SSH keys were refreshed
     */
    // run every 7 days
    @Scheduled(fixedRate = 604_800_000)
    fun refreshSSHKeysForAllAssignments(): Int {

        LOG.info("Refreshing (Github) SSH keys for all assignments")

        val assignments = assignmentRepository.findAll()
        var refreshedKeys = 0
        for (assignment in assignments) {
            try {
                gitClient.fetch(File(dropProjectProperties.assignments.rootLocation, assignment.gitRepositoryFolder),
                    assignment.gitRepositoryPrivKey!!.toByteArray())
                LOG.info("Refreshed (Github) SSH key for assignment ${assignment.id}")
                refreshedKeys++
            } catch (e: Exception) {
                LOG.warn("Error refreshing (Github) SSH keys for assignment ${assignment.id}: ${e.message}")
            }
        }

        return refreshedKeys
    }

    /**
     * Deletes the files produced by assignment exports that are older than [EXPORT_TIME_TO_LIVE], regardless of
     * having been downloaded or not. Exports are kept for a while (instead of being deleted right after the
     * download) so that the teacher can retry a download that failed or was interrupted.
     *
     * @return the number of exports that were deleted
     */
    // run every 10 minutes
    @Scheduled(fixedRate = 600_000)
    fun cleanExpiredExports(): Int {
        return deleteExportsCreatedBefore(System.currentTimeMillis() - EXPORT_TIME_TO_LIVE)
    }

    /**
     * Deletes the files produced by the assignment exports that were created before [timestamp], also forgetting
     * the corresponding pending tasks.
     *
     * @return the number of exports that were deleted
     */
    fun deleteExportsCreatedBefore(timestamp: Long): Int {

        var deletedExports = 0
        for (expiredTask in pendingTasks.removeCreatedBefore(timestamp)) {
            val expiredExports = when (expiredTask) {
                is PendingExport -> listOf(expiredTask)
                is PendingMultipleExports -> expiredTask.exports
                else -> emptyList()
            }

            for (expiredExport in expiredExports) {
                if (expiredExport.zipFile.delete()) {
                    deletedExports++
                    LOG.info("Deleted expired export ${expiredExport.zipFile.absolutePath}")
                } else {
                    LOG.warn("Error deleting expired export ${expiredExport.zipFile.absolutePath}")
                }
            }
        }

        return deletedExports
    }

    companion object {
        const val EXPORT_TIME_TO_LIVE = 3600 * 1000L  // 1 hour
    }
}
