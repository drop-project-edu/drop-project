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
import org.springframework.transaction.annotation.Transactional
import org.dropproject.dao.Assignee
import org.dropproject.dao.AssignmentACL

/**
 * Provides functions to query [AssignmentACL]s that have been persisted in the database.
 */
interface AssignmentACLRepository : JpaRepository<AssignmentACL, Long> {

    fun existsByAssignmentIdAndUserId(assignmentId: String, userId: String): Boolean
    fun findByAssignmentId(assignmentId: String): List<AssignmentACL>
    fun findByUserId(userId: String): List<AssignmentACL>

    @Transactional
    fun deleteByAssignmentId(assignmentId: String): List<AssignmentACL>
}
