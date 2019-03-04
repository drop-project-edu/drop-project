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
import org.springframework.transaction.annotation.Transactional
import org.dropProject.dao.Assignee

interface AssigneeRepository : JpaRepository<Assignee, String> {

    fun existsByAssignmentId(assignmentId: String): Boolean
    fun existsByAssignmentIdAndAuthorUserId(assignmentId: String, authorUserId: String): Boolean
    fun findByAssignmentIdOrderByAuthorUserId(assignmentId: String): List<Assignee>
    fun findByAuthorUserId(authorUserId: String): List<Assignee>

    @Transactional
    fun deleteByAssignmentId(assignmentId: String): List<Assignee>

    @Transactional
    fun deleteByAuthorUserId(authorUserId: String): List<Assignee>

}
