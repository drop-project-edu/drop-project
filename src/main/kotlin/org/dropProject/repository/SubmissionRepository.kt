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

import org.dropProject.dao.ProjectGroup
import org.dropProject.dao.Submission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.*


/**
 * Provides functions to query [Submission]s that have been persisted in the database.
 */
interface SubmissionRepository : JpaRepository<Submission, Long> {

    // this left join instructs hibernate to fetch submissions and corresponding buildReports with just one query
    @Query("SELECT s from Submission s LEFT JOIN FETCH s.buildReport LEFT JOIN FETCH s.group WHERE s.assignmentId = :assignmentId")
    fun findByAssignmentId(@Param("assignmentId") assignmentId: String) : List<Submission>

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
    // this should only count non-deleted submissions (excludeStatusId -> DELETED)
    fun countByAssignmentIdAndStatusNot(assignmentId: String, excludeStatusId: String): Long
    fun countByAssignmentIdAndSubmitterUserId(assignmentId: String, submitterUserId: String): Long

    fun findByGitSubmissionId(gitSubmissionId: Long) : List<Submission>

    fun findFirstByAssignmentIdOrderBySubmissionDateDesc(assignmentId: String) : Submission

    fun findByStatusOrderByStatusDate(statusId: String): List<Submission>
    fun findBySubmissionId(submissionId: String): Submission

    @Transactional
    fun deleteByGitSubmissionId(gitSubmissionId: Long)

    @Transactional
    fun deleteAllByAssignmentId(assignmentId: String)

    fun findByGroupIn(groups: List<ProjectGroup>): List<Submission>
}
