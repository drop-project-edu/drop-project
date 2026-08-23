/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2022 Pedro Alves
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

import org.dropproject.Constants
import org.dropproject.config.DropProjectProperties
import org.dropproject.dao.Assignment
import org.dropproject.dao.PlagiarismCheck
import org.dropproject.dao.PlagiarismCheckComparison
import org.dropproject.dao.SubmissionStatus
import org.dropproject.repository.PlagiarismCheckRepository
import org.dropproject.repository.SubmissionRepository
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Service
import java.io.File
import java.util.Date

/**
 * Runs plagiarism checks over the submissions of an [Assignment] and keeps their result, so that a teacher can
 * consult it again without paying the cost of running the check a second time. Only the last check of each
 * assignment is kept - running a new one replaces the previous, together with its detailed report file.
 */
@Service
class PlagiarismService(
    private val jPlagService: JPlagService,
    private val submissionService: SubmissionService,
    private val submissionRepository: SubmissionRepository,
    private val plagiarismCheckRepository: PlagiarismCheckRepository,
    private val dropProjectProperties: DropProjectProperties
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Returns the zip file with the detailed JPlag report of the last check of this assignment. It may not exist,
     * for example if the assignment was never checked or if the check failed while producing the report.
     */
    fun getReportFile(assignmentId: String): File =
        File(dropProjectProperties.plagiarism.rootLocation, "dp-jplag-${assignmentId}-report.zip")

    /**
     * Returns the last plagiarism check that was run over this assignment, or null if it was never checked.
     */
    fun getLastCheck(assignmentId: String): PlagiarismCheck? =
        plagiarismCheckRepository.findByAssignmentId(assignmentId)

    /**
     * Runs a plagiarism check over the last (or final, if there are any) submissions of [assignment] and stores
     * its result, replacing the result of any previous check of the same assignment.
     *
     * Note that this is deliberately not transactional: checking for plagiarism takes long enough that holding a
     * database connection for the whole operation would starve the connection pool. If it fails midway, the
     * assignment is simply left without a stored check, which is the same state as a check that was never run.
     */
    fun runCheck(assignment: Assignment, checkedBy: String): PlagiarismCheck {

        val submissionInfos = submissionService.getSubmissionsList(assignment, retrieveReport = false)

        // check if there are any submissions marked as final. in that case, consider only final submissions
        // for plagiarism detection
        val hasSubmissionsMarkedAsFinal = submissionInfos.any { it.lastSubmission.markedAsFinal }
        val submissions = submissionInfos
            .filter { !hasSubmissionsMarkedAsFinal || it.lastSubmission.markedAsFinal }
            .map { it.lastSubmission }

        val tempDir = FileSystemResource(System.getProperty("java.io.tmpdir")).file
        val submissionsToCheckFolder = File(tempDir, "dp-jplag-${assignment.id}-submissions")
        // jplag zips this folder and deletes it, leaving only <folder>.zip behind (see getReportFile)
        val reportFolder = File(dropProjectProperties.plagiarism.rootLocation, "dp-jplag-${assignment.id}-report")
        reportFolder.parentFile.mkdirs()

        // make sure none of these exist. otherwise, if this check fails, the report of a previous check
        // would still be available for download, as if it was the result of this one
        submissionsToCheckFolder.deleteRecursively()
        reportFolder.deleteRecursively()
        getReportFile(assignment.id).delete()

        try {
            jPlagService.prepareSubmissions(submissions, submissionsToCheckFolder)
            LOG.info("Prepared submissions for jplag on ${submissionsToCheckFolder.absolutePath}")

            val result = jPlagService.checkSubmissions(submissionsToCheckFolder, assignment, reportFolder)
            LOG.info(
                "Checked submissions using jplag on ${submissionsToCheckFolder.absolutePath}. " +
                        "Wrote report to ${getReportFile(assignment.id).absolutePath}"
            )

            return storeCheck(assignment, checkedBy, submissions.size, result)

        } finally {
            // the copies of the students' code are only needed while jplag is running
            submissionsToCheckFolder.deleteRecursively()
            // there is normally nothing left here, since jplag deletes this folder after zipping it.
            // this only cleans up after a failed check
            reportFolder.deleteRecursively()
        }
    }

    /**
     * Rebuilds the [PlagiarismResult] of a stored [check], so that it can be shown the same way as the result of
     * a check that has just been run. Comparisons whose submissions no longer exist are dropped.
     */
    fun toResult(check: PlagiarismCheck): PlagiarismResult {

        val submissionsById = submissionRepository
            .findAllById(check.comparisons.flatMap { listOf(it.firstSubmissionId, it.secondSubmissionId) }.distinct())
            .associateBy { it.id }

        val numTriesByGroupId = numTriesByGroupId(check.assignmentId)

        val comparisons = check.comparisons
            .sortedByDescending { it.similarityPercentage }
            .mapIndexedNotNull { idx, comparison ->
                val first = submissionsById[comparison.firstSubmissionId]
                val second = submissionsById[comparison.secondSubmissionId]
                if (first == null || second == null) {
                    LOG.warn("Ignoring comparison ${comparison.id} of the plagiarism check ${check.id} " +
                            "because one of its submissions no longer exists")
                    null
                } else {
                    PlagiarismComparison(
                        idx, first, second, comparison.similarityPercentage,
                        numTriesByGroupId[first.group.id] ?: -1,
                        numTriesByGroupId[second.group.id] ?: -1
                    )
                }
            }

        return PlagiarismResult(comparisons, check.ignoredSubmissionsList())
    }

    /**
     * Removes everything that was stored about the plagiarism checks of this assignment.
     */
    fun deleteChecks(assignmentId: String) {
        plagiarismCheckRepository.deleteByAssignmentId(assignmentId)
        getReportFile(assignmentId).delete()
    }

    private fun storeCheck(
        assignment: Assignment, checkedBy: String, numSubmissions: Int, result: PlagiarismResult
    ): PlagiarismCheck {

        // only the last check of each assignment is kept
        plagiarismCheckRepository.deleteByAssignmentId(assignment.id)

        val check = PlagiarismCheck(
            assignmentId = assignment.id,
            checkDate = Date(),
            checkedBy = checkedBy,
            similarityThreshold = Constants.SIMILARITY_THRESHOLD,
            numSubmissions = numSubmissions,
            ignoredSubmissions = result.ignoredSubmissions.joinToString("\n").ifBlank { null }
        )

        result.comparisons.mapTo(check.comparisons) {
            PlagiarismCheckComparison(
                plagiarismCheck = check,
                firstSubmissionId = it.firstSubmission.id,
                secondSubmissionId = it.secondSubmission.id,
                similarityPercentage = it.similarityPercentage
            )
        }

        return plagiarismCheckRepository.save(check)
    }

    /**
     * Returns, for each group of this assignment, how many submissions it has. Deleted submissions are not
     * counted, to be consistent with the number of submissions shown everywhere else.
     */
    private fun numTriesByGroupId(assignmentId: String): Map<Long, Int> =
        submissionRepository.findByAssignmentId(assignmentId)
            .filter { it.getStatus() != SubmissionStatus.DELETED }
            .groupingBy { it.group.id }
            .eachCount()
}
