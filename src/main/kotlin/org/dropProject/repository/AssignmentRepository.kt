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

import org.dropProject.dao.*
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Provides functions to query [Assignment]s that have been persisted in the database.
 * It also loads the tags of the assignments (eager loading)
 */
interface AssignmentRepository : JpaRepository<Assignment, String> {

    @EntityGraph(attributePaths = ["tags", "assignmentTestMethods"])
    override fun findById(id: String): Optional<Assignment>

    @EntityGraph(attributePaths = ["tags", "assignmentTestMethods"])
    override fun findAllById(ids: Iterable<String>): MutableList<Assignment>

    @EntityGraph(attributePaths = ["tags", "assignmentTestMethods"])
    fun findAllByOwnerUserId(ownerUserId: String): List<Assignment>

    @EntityGraph(attributePaths = ["tags", "assignmentTestMethods"])
    fun findByGitRepositoryFolder(gitRepositoryFolder: String): Assignment?

    @EntityGraph(attributePaths = ["tags", "assignmentTestMethods"])
    fun findAllByVisibilityAndActiveTrue(visibility: AssignmentVisibility): List<Assignment>

    @EntityGraph(attributePaths = ["tags", "assignmentTestMethods"])
    fun findAllByVisibility(visibility: AssignmentVisibility): List<Assignment>

    @EntityGraph(attributePaths = ["tags", "assignmentTestMethods"])
    fun findAllByNumSubmissions(numSubmissions: Int): List<Assignment>

    fun countByTags_Id(tagId: Long): Long
}
