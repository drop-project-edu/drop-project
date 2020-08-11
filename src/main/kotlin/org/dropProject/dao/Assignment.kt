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
package org.dropProject.dao

import org.dropProject.Constants
import org.dropProject.forms.SubmissionMethod
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.persistence.*

val formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm")

enum class Language {
    JAVA, KOTLIN
}

enum class TestVisibility {
    HIDE_EVERYTHING,
    SHOW_OK_NOK,    // show only if it passes all the hidden tests or not
    SHOW_PROGRESS  // show the number of tests that pass
}

enum class LeaderboardType {
    TESTS_OK,    // ordered by number of passed tests (desc)
    ELLAPSED,    // ordered by number of passed tests (desc) and then ellapsed (asc)
    COVERAGE     // ordered by number of passed tests (desc) and then coverage (desc)
}

@Entity
data class Assignment(
        @Id
        val id: String,

        @Column(nullable = false)
        var name: String,

        var packageName: String? = null,
        var dueDate: LocalDateTime? = null,

        @Column(nullable = false)
        var submissionMethod: SubmissionMethod,

        @Column(nullable = false)
        var language: Language = Language.JAVA,

        var acceptsStudentTests: Boolean = false,
        var minStudentTests: Int? = null,
        var calculateStudentTestsCoverage: Boolean = false,
        var hiddenTestsVisibility: TestVisibility? = null,
        var cooloffPeriod: Int? = null, // minutes
        var maxMemoryMb: Int? = null,
        var showLeaderBoard: Boolean = false,
        var leaderboardType: LeaderboardType? = null,

        val gitRepositoryUrl: String,
        @Column(columnDefinition = "TEXT")
        var gitRepositoryPubKey: String? = null,
        @Column(columnDefinition = "TEXT")
        var gitRepositoryPrivKey: String? = null,

        var gitRepositoryFolder: String,  // relative to assignment.root.location

        @Column(nullable = false)
        val ownerUserId: String,

        var active: Boolean = false,
        var archived: Boolean = false,

        var buildReportId: Long? = null,  // build_report.id

        @Transient
        var numSubmissions: Int = 0,

        @Transient
        var numUniqueSubmitters: Int = 0,

        @Transient
        var public: Boolean = true,

        @Transient
        var lastSubmissionDate: Date? = null
) {

    @ManyToMany(cascade = [CascadeType.ALL])
    val tags = mutableSetOf<AssignmentTag>()

    fun dueDateFormatted(): String? {
        return dueDate?.format(formatter)
    }
}
