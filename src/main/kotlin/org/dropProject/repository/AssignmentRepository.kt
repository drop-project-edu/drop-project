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
import org.dropProject.dao.Assignment
import org.dropProject.dao.Author
import org.dropProject.dao.ProjectGroup
import org.dropProject.dao.Submission
import org.springframework.data.jpa.repository.EntityGraph

/**
 * Provides functions to query [Assignment]s that have been persisted in the database.
 */
interface AssignmentRepository : JpaRepository<Assignment, String> {

    @EntityGraph(attributePaths = ["tags"])
    fun findByOwnerUserId(ownerUserId: String): List<Assignment>
    fun findByGitRepositoryFolder(gitRepositoryFolder: String): Assignment?

//    @EntityGraph(attributePaths = ["tags"])
//    override fun getOne(id: String?): Assignment
}
