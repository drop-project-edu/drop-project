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

import org.dropproject.dao.Indicator
import org.dropproject.dao.ProjectGroup
import org.dropproject.dao.Submission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import org.dropproject.dao.SubmissionReport
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Provides functions to query [SubmissionReport]s that have been persisted in the database.
 */
interface SubmissionReportRepository : JpaRepository<SubmissionReport, Long> {

    fun findBySubmissionId(submissionId: Long) : List<SubmissionReport>
    fun deleteBySubmissionIdAndReportKey(submissionId: Long, reportKey: String)

    @Transactional
    @Modifying
    @Query("delete from SubmissionReport sr where sr.submissionId = :submissionId and sr.reportKey <> :except")
    fun deleteBySubmissionIdExceptProjectStructure(@Param("submissionId") submissionId: Long,
                                                   @Param("except") except: String = Indicator.PROJECT_STRUCTURE.code)

    @Transactional
    fun deleteBySubmissionId(submissionId: Long)

    fun findBySubmissionIdIn(submissionIds: List<Long>): List<SubmissionReport>
}
