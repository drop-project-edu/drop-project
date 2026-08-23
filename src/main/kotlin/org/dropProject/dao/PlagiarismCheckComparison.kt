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

/**
 * Represents a pair of [Submission]s that a [PlagiarismCheck] found to be similar.
 *
 * @property id is a primary-key like generated value
 * @property plagiarismCheck is the check that produced this comparison
 * @property firstSubmissionId is the id of the first Submission of the pair
 * @property secondSubmissionId is the id of the second Submission of the pair
 * @property similarityPercentage is how similar the two submissions are, between 0 and 100
 */
@Entity
data class PlagiarismCheckComparison(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        @ManyToOne
        @JoinColumn(name = "plagiarism_check_id", nullable = false)
        var plagiarismCheck: PlagiarismCheck? = null,

        @Column(nullable = false)
        val firstSubmissionId: Long,

        @Column(nullable = false)
        val secondSubmissionId: Long,

        @Column(nullable = false)
        val similarityPercentage: Int
) {
    // the parent is excluded from equals/hashCode/toString to avoid infinite recursion, since
    // PlagiarismCheck holds the list of its comparisons
    override fun equals(other: Any?) = other is PlagiarismCheckComparison && other.id == id
    override fun hashCode() = id.hashCode()
    override fun toString() =
            "PlagiarismCheckComparison(id=$id, firstSubmissionId=$firstSubmissionId, " +
                    "secondSubmissionId=$secondSubmissionId, similarityPercentage=$similarityPercentage)"
}
