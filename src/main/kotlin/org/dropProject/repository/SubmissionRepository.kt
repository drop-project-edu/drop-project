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
package org.dropProject.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import org.dropProject.dao.ProjectGroup
import org.dropProject.dao.Submission
import org.dropProject.dao.SubmissionStatus
import java.util.*

/**
 * Provides functions to query [Submission]s that have been persisted in the database.
 */
interface SubmissionRepository : JpaRepository<Submission, Long> {

    fun findByAssignmentId(assignmentId: String) : List<Submission>

    @Query("SELECT COUNT(DISTINCT s.group) FROM Submission s WHERE s.assignmentId = ?1 and s.status <> 'D'")  // TODO Replace by constant
    fun findUniqueSubmittersByAssignmentId(assignmentId: String) : Long

    fun findByAssignmentIdAndMarkedAsFinal(assignmentId: String, markedAsFinal: Boolean) : List<Submission>
    fun findBySubmitterUserIdAndAssignmentId(submitterUserId: String, assignmentId: String) : List<Submission>
    fun findFirstBySubmitterUserIdAndAssignmentIdOrderBySubmissionDateDesc(submitterUserId: String, assignmentId: String) : Submission?
    fun countBySubmitterUserIdAndAssignmentId(submitterUserId: String, assignmentId: String) : Long
    fun findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(group: ProjectGroup, assignmentId: String) : List<Submission>
    fun findFirstByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(group: ProjectGroup, assignmentId: String) : Submission?
    fun findFirstByGroupInAndAssignmentIdOrderBySubmissionDateDesc(groups: List<ProjectGroup>, assignmentId: String) : Submission?
    fun findByStatusAndStatusDateBefore(status: String, statusDate: Date): List<Submission>
    fun countByAssignmentId(assignmentId: String): Long
    fun countByAssignmentIdAndSubmitterUserId(assignmentId: String, submitterUserId: String): Long

    fun findByGitSubmissionId(gitSubmissionId: Long) : List<Submission>

    fun findFirstByAssignmentIdOrderBySubmissionDateDesc(assignmentId: String) : Submission

    fun findByStatusOrderByStatusDate(statusId: String): List<Submission>
    fun findBySubmissionId(submissionId: String): Submission

    @Transactional
    fun deleteByGitSubmissionId(gitSubmissionId: Long)

    fun findByGroupIn(groups: List<ProjectGroup>): List<Submission>
}
