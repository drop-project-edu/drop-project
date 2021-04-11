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

import com.fasterxml.jackson.annotation.JsonFormat
import org.dropProject.Constants
import org.dropProject.extensions.format
import org.dropProject.forms.SubmissionMethod
import java.util.*
import javax.persistence.*

val formatter = "dd MMM HH:mm"

/**
 * Enum representing the programming languages that Drop Project supports.
 */
enum class Language {
    JAVA, KOTLIN
}

/**
 * Enum representing the types of visibility that can be applied to the results of the hidden unit tests.
 */
enum class TestVisibility {
    HIDE_EVERYTHING,
    SHOW_OK_NOK,    // show only if it passes all the hidden tests or not
    SHOW_PROGRESS  // show the number of tests that pass
}

/**
 * Enum representing the types of Leaderboard (for example, based on Number of Passed Tests).
 */
enum class LeaderboardType {
    TESTS_OK,    // ordered by number of passed tests (desc)
    ELLAPSED,    // ordered by number of passed tests (desc) and then ellapsed (asc)
    COVERAGE     // ordered by number of passed tests (desc) and then coverage (desc)
}

/**
 * Represents an Assignment, which is a problem to be solved by a student or group of students.
 *
 * @property id is a String that uniquely identifies the Assignment
 * @property name is a String representing the name of the Assignment
 * @property packageName is a String with the (optional) Assignment's expected package (e.g. all the code should be
 * placed in this package)
 * @property dueDate is an optional [LocalDateTime] with the submission deadline
 * @property language is the programming [Language] that the code should be written in
 * @property acceptsStudentTests is a Boolean, indicating if the students are allowed to submit their own unit tests
 * @property minStudentTests is an optional Integer, indicating the minimum number of unit tests that students are
 * asked to implement
 * @property calculateStudentTestsCoverage is an optional Boolean, indicating if the test coverage should be calculated
 * for student's own tests
 * @property cooloffPeriod is an optional Integer with the number of minutes that students must wait between consecutive
 * submissions
 * @property maxMemoryMb is an optional Integer, indicating the maximum number of Mb that the student's code can use
 * @property showLeaderBoard is a Boolean, indicating if the leaderboard page should be active for this Assignment
 * @property gitRepositoryUrl is a String with the location of the git repository used to create the Assignment
 * @property gitRepositoryPubKey is a String with the Public Key of the git repository
 * @property gitRepositoryPrivKey is a String the Private Key of the git repository
 * @property ownerUserId is a String with the user id of the user that created the Assignment
 * @property active is a Boolean indicating if the Assignment can receive submissions from students
 * @property archived is a Boolean indicating if the Assignment has been archived
 * @property buildReportId is a Long, representing the Id of the Assignment's [BuildReport]
 *
 * @property public is a Boolean, indicating if the assignment is accessible to everyone or only to registered [Assignee]s
 * @property lastSubmissionDate is a Date, containing the date of the last submission performed for this Assignment
 * @property tags is a Set of [AssignmentTag].
 */
@Entity
data class Assignment(
        @Id
        val id: String,

        @Column(nullable = false)
        var name: String,

        var packageName: String? = null,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd hh:mm:ss")
        var dueDate: Date? = null,

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
        var ownerUserId: String = "",

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
