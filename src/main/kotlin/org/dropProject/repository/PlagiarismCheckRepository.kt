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

import org.dropproject.dao.PlagiarismCheck
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

/**
 * Provides functions to query [PlagiarismCheck]s that have been persisted in the database.
 */
interface PlagiarismCheckRepository : JpaRepository<PlagiarismCheck, Long> {

    // this left join fetches the comparisons together with the check, since they are always needed
    // and are read outside of the transaction that loaded the check
    @Query("SELECT c from PlagiarismCheck c LEFT JOIN FETCH c.comparisons WHERE c.assignmentId = :assignmentId")
    fun findByAssignmentId(@Param("assignmentId") assignmentId: String): PlagiarismCheck?

    @Transactional
    fun deleteByAssignmentId(assignmentId: String): List<PlagiarismCheck>
}
