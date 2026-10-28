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

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonView
import org.dropproject.data.AllowedIps
import org.dropproject.data.JSONViews
import org.dropproject.data.SubmissionWindows
import org.dropproject.extensions.format
import org.dropproject.forms.SubmissionMethod
import org.dropproject.services.AssignmentInstructions
import java.util.*
import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ResponseStatus

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
 * @property submissionStructure is a [SubmissionStructure], indicating the expected project structure for student submissions
 * @property springBoot is a Boolean, indicating if the teacher's pom.xml builds a Spring Boot project. It is detected
 * whenever the assignment is validated, and is null for an assignment that was never validated
 * @property acceptsStudentTests is a Boolean, indicating if the students are allowed to submit their own unit tests
 * @property minStudentTests is an optional Integer, indicating the minimum number of unit tests that students are
 * asked to implement
 * @property calculateStudentTestsCoverage is an optional Boolean, indicating if the test coverage should be calculated
 * for student's own tests
 * @property coverageVisibleToStudents is an optional Boolean, indicating if the coverage results should be visible to students
 * @property cooloffPeriod is an optional Integer with the number of minutes that students must wait between consecutive
 * submissions
 * @property allowedIps is an optional String with the only IPv4 addresses and wildcard prefixes (e.g. "10.12.33.*")
 * that students may access the assignment from (see [org.dropproject.data.AllowedIps])
 * @property submissionWindows is a List of [SubmissionWindow]s with the only periods of time in which students may
 * submit to the assignment. Without any, students may submit at any time
 * @property maxMemoryMb is an optional Integer, indicating the maximum number of Mb that the student's code can use
 * @property showLeaderBoard is a Boolean, indicating if the leaderboard page should be active for this Assignment
 * @property leaderboardType is a [LeaderboardType]
 * @property baseAssignmentId is an optional String with the id of the "project" Assignment that this one is a
 * defense of. If it is set, every submission is compared with the group's last submission to that Assignment
 * @property maxChangedLines is an optional Integer with the maximum number of lines that a submission is allowed to
 * change relatively to the linked project submission. It is only enforced after [defenseInstructionsReleased]
 * @property defenseInstructionsReleased is a Boolean, indicating if the defense instructions (phase 2) have already
 * been released to the students. It is toggled manually by the teacher, typically when the defense starts
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

    @Column(nullable = false)
    @JsonView(JSONViews.StudentAPI::class)
    var submissionStructure: SubmissionStructure = SubmissionStructure.COMPACT,

    var springBoot: Boolean? = null,

    var acceptsStudentTests: Boolean = false,
    var minStudentTests: Int? = null,
    var calculateStudentTestsCoverage: Boolean = false,
    var coverageVisibleToStudents: Boolean = false,
    var hiddenTestsVisibility: TestVisibility? = null,
    var mandatoryTestsSuffix: String? = null,
    var cooloffPeriod: Int? = null, // minutes
    var allowedIps: String? = null,
    var maxMemoryMb: Int? = null,
    var showLeaderBoard: Boolean = false,
    var leaderboardType: LeaderboardType? = null,

    @Column(length = 50)
    var baseAssignmentId: String? = null,
    var maxChangedLines: Int? = null,
    var defenseInstructionsReleased: Boolean = false,

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

    // eager, since they are needed whenever a student opens or submits to the assignment, and fetched with separate
    // selects (in batches, when several assignments are loaded), instead of being joined with the other collections,
    // which would multiply the rows of the query. They are exported and imported as text, through submissionWindowsText
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "submission_window", joinColumns = [JoinColumn(name = "assignment_id")])
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    @OrderBy("opensAt ASC, closesAt ASC")
    @JsonIgnore
    var submissionWindows: MutableList<SubmissionWindow> = mutableListOf(),

    @JsonView(JSONViews.TeacherAPI::class)
    var numSubmissions: Int = 0,

    var numUniqueSubmitters: Int = 0,

    var lastSubmissionDate: Date? = null,

    @Transient
    var authorizedStudentIds: List<String>? = null,

    @ManyToMany(fetch = FetchType.LAZY, cascade = [])
    @JoinTable(
        name = "AssignmentTags",
        joinColumns = [JoinColumn(name = "assignmentId", referencedColumnName = "id")],
        inverseJoinColumns = [JoinColumn(name = "tagId", referencedColumnName = "id")]
    )
    var tags: MutableSet<AssignmentTag> = mutableSetOf(),

    @JsonView(JSONViews.StudentAPI::class, JSONViews.TeacherAPI::class)
    @Transient
    var instructions: AssignmentInstructions? = null,

    @OneToOne(cascade = [CascadeType.REMOVE])
    @JoinColumn(name = "project_group_restrictions_id")
    var projectGroupRestrictions: ProjectGroupRestrictions? = null,
) {

    // READ_ONLY because this property has no setter: without it, importing an assignment fails with
    // "Should never call set() on setterless property ('tagsStr')", since the exports include it
    @get:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @get:JsonView(JSONViews.TeacherAPI::class)
    @get:Transient
    val tagsStr: List<String>
        get() = tags.map { it.name }

    fun dueDateFormatted(): String? {
        return dueDate?.format(formatter)
    }

    fun overdue(submission: Submission): Boolean {
        if (dueDate != null) {
            return submission.submissionDate.after(dueDate)
        }

        return false
    }

    /**
     * Whether students may access this assignment from [clientIp]. Without [allowedIps], every address is accepted.
     */
    fun acceptsClientIp(clientIp: String?): Boolean =
        allowedIps.isNullOrBlank() || AllowedIps.parse(allowedIps!!).allows(clientIp)

    /**
     * The windows in which students may submit to this assignment, or null if they may submit at any time.
     */
    fun submissionWindowSchedule(): SubmissionWindows? = SubmissionWindows.of(submissionWindows.map { it.toWindow() })

    /**
     * Replaces the windows in which students may submit to this assignment. With null, they may submit at any time.
     */
    fun replaceSubmissionWindows(windows: SubmissionWindows?) {
        // the list is changed in place, so that hibernate knows that it has to replace the stored windows
        submissionWindows.clear()
        windows?.windows?.forEach { submissionWindows.add(SubmissionWindow(opensAt = it.start, closesAt = it.end)) }
    }

    /**
     * The submission windows written as text (see [SubmissionWindows]), e.g.
     * "2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00", or null if there are none. This is how they are shown to the
     * teachers and how they are exported and imported, as the "submissionWindows" property of the assignment's json.
     */
    @get:JsonProperty("submissionWindows")
    @set:JsonProperty("submissionWindows")
    var submissionWindowsText: String?
        get() = submissionWindowSchedule()?.toString()
        set(value) = replaceSubmissionWindows(value?.takeIf { it.isNotBlank() }?.let { SubmissionWindows.parse(it) })

    override fun toString(): String {
        return "$id - $name"
    }

    fun getLastSubmissionDateAsTimestamp(): Long {
        return lastSubmissionDate?.time ?: 0
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

    /**
     * Thrown when an assignment that was asked for doesn't exist.
     */
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Inexistent assignment")
    class NotFoundException(assignmentId: String) : Exception("Inexistent assignment ${assignmentId}") {
        val LOG = LoggerFactory.getLogger(this.javaClass.name)

        init {
            LOG.warn("Inexistent assignment ${assignmentId}")
        }
    }

    /**
     * Thrown when a user who is one of an assignment's intended users tries to open it while it is not active.
     *
     * It is an [AccessDeniedException] so that it is reported exactly like any other denial, through the security
     * chain of the request (see [org.dropproject.controllers.GlobalExceptionHandler]). The difference is that the
     * access denied page recognizes it and explains that the assignment is closed, instead of suggesting that the user
     * asks the teacher for permission that they already have.
     *
     * @property assignmentId is a String identifying the Assignment that is not active
     */
    class NotActiveException(val assignmentId: String) :
        AccessDeniedException("Assignment ${assignmentId} is not active")

    /**
     * Thrown when a user that may access an Assignment is connecting from an address that the assignment doesn't
     * allow (see [allowedIps]). Like [NotActiveException], it is reported as any other denial, but the access denied
     * page recognizes it, so that it can tell the student what the problem is.
     *
     * @property assignmentId is a String identifying the Assignment
     * @property clientIp is the address the user is connecting from
     */
    class NetworkNotAllowedException(val assignmentId: String, val clientIp: String?) :
        AccessDeniedException("Assignment ${assignmentId} can't be accessed from ${clientIp}")

    /**
     * Thrown when a student submits to an assignment outside of its submission windows (see [submissionWindows]).
     *
     * It is not an access denial: the student may use the assignment, just not right now. It is reported by
     * [org.dropproject.controllers.GlobalExceptionHandler] with its message, which is written to be read by the
     * student.
     */
    class SubmissionWindowClosedException(message: String) : RuntimeException(message)
}
