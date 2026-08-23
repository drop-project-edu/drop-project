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
package org.dropproject.dao

import jakarta.persistence.*
import java.util.Date

/**
 * Represents a plagiarism check that was run over the submissions of an [Assignment]. Since the check is a
 * time consuming operation, its result is persisted, so that it can be consulted again without running it
 * from scratch. Only the last check of each assignment is kept - running a new one replaces the previous.
 *
 * @property id is a primary-key like generated value
 * @property assignmentId is a String with the relevant Assignment's ID
 * @property checkDate is the [Date] on which the check was run
 * @property checkedBy is the id of the user who ran the check
 * @property similarityThreshold is the minimum similarity that was needed for a comparison to be reported
 * @property numSubmissions is the number of submissions that were compared
 * @property ignoredSubmissions holds the names of the submissions that couldn't be processed, one per line
 * @property comparisons are the pairs of submissions that were found to be similar
 */
@Entity
data class PlagiarismCheck(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        @Column(nullable = false, length = 50)
        val assignmentId: String,  // assignment.id

        @Temporal(TemporalType.TIMESTAMP)
        @Column(nullable = false)
        val checkDate: Date,

        @Column(nullable = false)
        val checkedBy: String,

        @Column(nullable = false)
        val similarityThreshold: Double,

        @Column(nullable = false)
        val numSubmissions: Int,

        @Column(columnDefinition = "TEXT")
        val ignoredSubmissions: String? = null,

        @OneToMany(mappedBy = "plagiarismCheck", cascade = [CascadeType.ALL], orphanRemoval = true)
        val comparisons: MutableList<PlagiarismCheckComparison> = mutableListOf()
) {

    /**
     * Returns the names of the submissions that were ignored during this check.
     */
    fun ignoredSubmissionsList(): List<String> =
            ignoredSubmissions?.lines()?.filter { it.isNotBlank() } ?: emptyList()
}
