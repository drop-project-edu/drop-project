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
import org.dropproject.dao.Author
import org.dropproject.dao.ProjectGroup
import org.dropproject.dao.Submission
import org.springframework.data.jpa.repository.Query

/**
 * Provides functions to query [Author]s that have been persisted in the database.
 */
interface AuthorRepository : JpaRepository<Author, Long> {

    /**
     * Gets all the authors associated with this userId. Notice that the author
     * includes the group on which he belongs. If the same student belongs to
     * several groups, this function will return several results.
     */
    fun findByUserId(userId: String) : List<Author>?

    @Query("SELECT a1.group FROM Author a1, Author a2 WHERE a1.userId = ?1 and a2.userId = ?2 and a1.group = a2.group")
    fun getGroupId(userId1: String, userId2: String): ProjectGroup?
}
