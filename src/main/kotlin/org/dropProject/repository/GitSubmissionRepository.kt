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
package org.dropproject.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.dropproject.dao.GitSubmission
import org.dropproject.dao.ProjectGroup

/**
 * Provides functions to query [GitSubmission]s that have been persisted in the database.
 */
interface GitSubmissionRepository : JpaRepository<GitSubmission, Long> {

    fun findByAssignmentId(assignmentId: String) : List<GitSubmission>
    fun findByAssignmentIdAndConnected(assignmentId: String, connected: Boolean) : List<GitSubmission>
    fun findBySubmitterUserIdAndAssignmentId(submitterUserId: String, assignmentId: String) : GitSubmission?
    fun findByGroupInAndAssignmentId(groups: List<ProjectGroup>, assignmentId: String) : GitSubmission?

    fun countByGroup(group: ProjectGroup) : Long
    fun countBySubmitterUserIdAndAssignmentId(submitterUserId: String, assignmentId: String) : Long
    fun countByAssignmentId(assignmentId: String): Long

    fun findByGroupAndAssignmentId(group: ProjectGroup, assignmentId: String) : GitSubmission?

}
