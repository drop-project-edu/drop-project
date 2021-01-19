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

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.dropProject.dao.GitSubmission
import org.dropProject.repository.*

/**
 * Provides functionality related with handling [GitSubmission]s (for example, searching for a GitSubmission in the
 * database or deleting a [GitSubmission].
 */
@Service
class GitSubmissionService(
        val submissionRepository: SubmissionRepository,
        val gitSubmissionRepository: GitSubmissionRepository,
        val jUnitReportRepository: JUnitReportRepository,
        val jacocoReportRepository: JacocoReportRepository,
        val submissionReportRepository: SubmissionReportRepository,
        val projectGroupRepository: ProjectGroupRepository) {

    /**
     * Searches the [SubmissionRepository] for a [Submission] belonging to authorName and corresponding to a
     * certain [Assignment]. Considers Submissions done by the author or to a group containing that author.
     *
     * @return a Submission or null
     */
    fun findGitSubmissionBy(authorName: String, assignmentId: String): GitSubmission? {
        val groups = projectGroupRepository.getGroupsForAuthor(authorName)
        for (group in groups) {
            val submission = gitSubmissionRepository.findByGroupAndAssignmentId (group, assignmentId)
            if (submission != null) {
                return submission
            }
        }
        return null
    }

    // remove all submission reports and submissions
    fun deleteGitSubmission(gitSubmission: GitSubmission) {

        val submissions = submissionRepository.findByGitSubmissionId(gitSubmission.id)
        for (submission in submissions) {
            submissionReportRepository.deleteBySubmissionId(submission.id)
            jUnitReportRepository.deleteBySubmissionId(submission.id)
            jacocoReportRepository.deleteBySubmissionId(submission.id)
        }

        submissionRepository.deleteByGitSubmissionId(gitSubmission.id)
        gitSubmissionRepository.delete(gitSubmission)
    }
}
