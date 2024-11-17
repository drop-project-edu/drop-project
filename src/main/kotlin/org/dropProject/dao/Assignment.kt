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
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonView
import org.dropProject.data.JSONViews
import org.dropProject.extensions.format
import org.dropProject.forms.SubmissionMethod
import org.dropProject.services.AssignmentInstructions
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
 *  - HIDE_EVERYTHING - students will receive no information about hidden tests;
 *  - SHOW_OK_NOK - students will be informed about if they pass all the tests or if there is at least one failure;
 *  - SHOW_PROGRESS - students will know how many tests exist and how many they are passing (e.g. nr passed/nr total).
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
 * Enum representing the types of visibility of each assignment.
 *  - PUBLIC - everyone sees this assignment;
 *  - ONLY_BY_LINK - everyone that has the link sees this assignment;
 *  - PRIVATE - only authorized users see this assignment.
 */
enum class AssignmentVisibility {
    PUBLIC,
    ONLY_BY_LINK,
    PRIVATE
}

/**
 * Represents an Assignment, which is a problem to be solved by a student or group of students.
 *
 * @property id is a String that uniquely identifies the Assignment
 * @property name is a String representing the name of the Assignment
 * @property packageName is a String with the (optional) Assignment's expected package (e.g. all the code should be
 * placed in this package)
 * @property dueDate is an optional [LocalDateTime] with the submission deadline
 * @property submissionMethod is a [SubmissionMethod]
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
 * @property leaderboardType is a [LeaderboardType]
 * @property gitRepositoryUrl is a String with the location of the git repository used to create the Assignment
 * @property gitRepositoryPubKey is a String with the Public Key of the git repository
 * @property gitRepositoryPrivKey is a String the Private Key of the git repository
 * @property gitCurrentHash is a String with the hash of the current commit that is being used in the assignment
 * @property ownerUserId is a String with the user id of the user that created the Assignment
 * @property active is a Boolean indicating if the Assignment can receive submissions from students
 * @property archived is a Boolean indicating if the Assignment has been archived
 * @property buildReportId is a Long, representing the Id of the Assignment's [BuildReport]
 * @property numSubmissions is an Int, representing to total number of submissions in the repository
 * @property numSubmitters is an Int, representing the number of different submitters
 * @property visibility is a [AssignmentVisibility]
 * @property lastSubmissionDate is a Date, containing the date of the last submission performed for this Assignment
 * @property tags is a Set of [AssignmentTag].
 */
@Entity
@JsonIgnoreProperties(ignoreUnknown = true)  // this is useful to improve backward-compatible imports
data class Assignment(
    @Id
    @JsonView(JSONViews.StudentAPI::class)  // include this field on API calls
    @Column(length = 50)
    val id: String,

    @Column(nullable = false)
    @JsonView(JSONViews.StudentAPI::class)
    var name: String,

    @JsonView(JSONViews.StudentAPI::class)
    var packageName: String? = null,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd hh:mm:ss")
    @JsonView(JSONViews.StudentAPI::class)
    var dueDate: Date? = null,

    @Column(nullable = false)
    @JsonView(JSONViews.StudentAPI::class)
    var submissionMethod: SubmissionMethod,

    @Column(nullable = false)
    @JsonView(JSONViews.StudentAPI::class)
    var language: Language = Language.JAVA,

    var acceptsStudentTests: Boolean = false,
    var minStudentTests: Int? = null,
    var calculateStudentTestsCoverage: Boolean = false,
    var hiddenTestsVisibility: TestVisibility? = null,
    var mandatoryTestsSuffix: String? = null,
    var cooloffPeriod: Int? = null, // minutes
    var maxMemoryMb: Int? = null,
    var showLeaderBoard: Boolean = false,
    var leaderboardType: LeaderboardType? = null,

    @JsonView(JSONViews.TeacherAPI::class)
    val gitRepositoryUrl: String,
    @Column(columnDefinition = "TEXT")
    var gitRepositoryPubKey: String? = null,
    @Column(columnDefinition = "TEXT")
    var gitRepositoryPrivKey: String? = null,

    var gitRepositoryFolder: String,  // relative to assignment.root.location

    var gitCurrentHash: String? = null,  // updated on refresh

    @Column(nullable = false)
    @JsonView(JSONViews.TeacherAPI::class)
    var ownerUserId: String = "",

    @JsonView(JSONViews.StudentAPI::class)
    var active: Boolean = false,
    var archived: Boolean = false,
    @Enumerated(EnumType.STRING)
    var visibility: AssignmentVisibility = AssignmentVisibility.ONLY_BY_LINK,

    var buildReportId: Long? = null,  // build_report.id

    @OneToMany(mappedBy = "assignment", fetch = FetchType.EAGER, cascade = [CascadeType.REMOVE])
    @JsonIgnore
    var assignmentTestMethods: MutableList<AssignmentTestMethod> = mutableListOf(),

    @JsonView(JSONViews.TeacherAPI::class)
    var numSubmissions: Int = 0,

    var numUniqueSubmitters: Int = 0,

    var lastSubmissionDate: Date? = null,

    @Transient
    var authorizedStudentIds: List<String>? = null,

    @JsonView(JSONViews.TeacherAPI::class)
    @Transient
    var tagsStr: List<String>? = null,

    @JsonView(JSONViews.StudentAPI::class)
    @Transient
    var instructions: AssignmentInstructions? = null,

    @OneToOne(cascade = [CascadeType.REMOVE])
    @JoinColumn(name = "project_group_restrictions_id")
    var projectGroupRestrictions: ProjectGroupRestrictions? = null,
) {

    fun dueDateFormatted(): String? {
        return dueDate?.format(formatter)
    }

    fun overdue(submission: Submission): Boolean {
        if (dueDate != null) {
            return submission.submissionDate.after(dueDate)
        }

        return false
    }

    override fun toString(): String {
        return "$id - $name"
    }



    fun linkToGithub(): String? {
        if (gitRepositoryPrivKey == null) {
            return null
        }

        val parts = gitRepositoryUrl.split(":")
        val hostAndPath = parts[1].removeSuffix(".git").split("/")
        val host = parts[0].substringAfter("@")
        val username = hostAndPath[0]
        val repositoryName = hostAndPath[1]

        return "https://$host/$username/$repositoryName"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Assignment

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
