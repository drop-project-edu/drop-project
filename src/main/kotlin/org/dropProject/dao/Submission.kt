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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.dropProject.data.JSONViews
import org.dropProject.data.JUnitSummary
import org.dropProject.services.JUnitMethodResult
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*
import jakarta.persistence.*

/**
 * Enum represening the possible statuses that a Submission can be in.
 *
 * The submission starts in the "Submitted" status, and then moves to another status as it is validated
 * and tested.
 *
 * Some of the statuses represent problematic situations (e.g. "TO", "Too much output").
 */
enum class SubmissionStatus(val code: String, val description: String) {
    SUBMITTED("S", "Submitted"),
    VALIDATED("V", "Validated"),
    FAILED("F", "Failed"),
    ABORTED_BY_TIMEOUT("AT", "Aborted by timeout"),
    SUBMITTED_FOR_REBUILD("SR", "Submitted for rebuild"),
    VALIDATED_REBUILT("VR", "Validated (Rebuilt)"),
    REBUILDING("R", "Rebuilding"),
    ILLEGAL_ACCESS("IA", "Illegal Access"),
    DELETED("D", "Deleted"),
    TOO_MUCH_OUTPUT("TO", "Too much output");

    companion object {
        fun getSubmissionStatus(code: String) : SubmissionStatus {

            for (status in SubmissionStatus.values()) {
                if (code.equals(status.code)) {
                    return status
                }
            }
            throw IllegalArgumentException("No matching SubmissionStatus for code ${code}")
        }

    }
}

enum class SubmissionMode {
    UPLOAD, GIT, API
}

/**
 * Represents a Submission, which is a single interaction of the student (or group) with an [Assignment].
 * @property id is a primary-key like generated value
 * @property submissionId is a String
 * @property gitSubmissionId is a String
 * @property submissionDate is a [Date] representing the date and time when the submission was performed
 * @property submitterUserId is a String identifying the user that performed the submission
 * @property status is a String. The value will be the "Code" property of an [SubmissionStatus] object
 * @property statusDate is a [Date] representing the date and time when the Assignment's status was last updated
 * @property assignmentId is a String identifying the relevant Assignment
 * @property assignmentGitHash is the git commit from the assignment that was used to validate this submission
 * @property buildReportId is a String
 * @property structureErrors is a String
 * @property markedAsFinal is a Boolean, indicating if this submission is marked as the group's final one. The
 * final Submission is the one that is exported to CSV.
 * @property studentTests is a [JUnitSummary] with the result of executing the student's own unit tests
 * @property teacherTests is a [JUnitSummary] with the result of executing the teacher's public tests
 * @property hiddenTests is a [JUnitSummary] with the result of executing the teacher's hidden tests
 * @property coverage is an Int with the test coverage percentage calculated for the submission's own unit tests
 * @property testResults is a List of [JUnitMethodResult] containing the result for each evaluation JUnit Test
 * @property group is the [ProjectGrop] that performed the submission.
 */

@Entity @JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Submission(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @JsonView(JSONViews.StudentAPI::class)
        val id: Long = 0,
        val submissionId: String? = null,
        var gitSubmissionId: Long? = null,

        val submissionFolder: String? = null,  // TODO: For now, this is null for git submissions

        @Column(nullable = false)
        @JsonView(JSONViews.StudentAPI::class)
        var submissionDate: Date,

        @Column(nullable = false)
        val submitterUserId: String,

        @Transient
        var submitterName: String? = null,

        @Column(nullable = false)
        @JsonView(JSONViews.StudentAPI::class)
        private var status: String,  // should be accessed through getStatus() and setStatus()

        @Column(nullable = false)
        @JsonView(JSONViews.StudentAPI::class)
        var statusDate: Date,

        @Column(length = 50)
        val assignmentId: String,
        val assignmentGitHash: String?,

        @OneToOne(cascade = [CascadeType.REMOVE])
        @JoinColumn(name = "build_report_id")
        var buildReport: BuildReport? = null,

//        var buildReportId: Long? = null,  // build_report.id

        @Column(columnDefinition = "TEXT")
        var structureErrors: String? = null,

        @JsonView(JSONViews.TeacherAPI::class)
        var markedAsFinal: Boolean = false,

        @Transient
        var reportElements: List<SubmissionReport>? = null,

        @Transient
        var ellapsed: BigDecimal? = null,

        @Transient
        @JsonView(JSONViews.StudentAPI::class)
        var studentTests: JUnitSummary? = null,

        @Transient
        @JsonView(JSONViews.StudentAPI::class)
        var teacherTests: JUnitSummary? = null,

        @Transient
        @JsonView(JSONViews.TeacherAPI::class)
        var hiddenTests: JUnitSummary? = null,

        @Transient
        var coverage: Int? = null,

        @Transient
        @JsonView(JSONViews.StudentAPI::class)
        var testResults: List<JUnitMethodResult>? = null,

        @Transient
        @JsonView(JSONViews.StudentAPI::class)
        var overdue: Boolean? = null,

        var submissionMode: SubmissionMode? = null
) {
    @ManyToOne
    @JsonView(JSONViews.TeacherAPI::class)
    lateinit var group: ProjectGroup

    constructor(submissionId: String, assignmentId: String, assignmentGitHash: String?, submitterNumber: String, status: String,
                statusDate: Date, group: ProjectGroup, submissionFolder: String, submissionMode: SubmissionMode) :
            this(submissionId = submissionId, assignmentId = assignmentId, assignmentGitHash = assignmentGitHash,
                 submitterUserId = submitterNumber, status = status, statusDate = statusDate, submissionDate = Date(),
                 submissionFolder = submissionFolder, submissionMode = submissionMode) {
        this.group = group
    }

    fun getStatus(): SubmissionStatus {
        return SubmissionStatus.getSubmissionStatus(this.status)
    }

    fun setStatus(status: SubmissionStatus, dontUpdateStatusDate : Boolean = false) {
        this.status = status.code
        if (!dontUpdateStatusDate) {
            this.statusDate = Date()
        }
    }

    fun getSubmissionDateAsTimestamp(): Long {
        return submissionDate.time
    }

    fun submitterShortName(): String {
        if (submitterName != null) {
            if (submitterName!!.length > 12) {
                return submitterName!!.split(" ")[0]
            } else {
                return submitterName!!
            }
        }

        return ""
    }

    companion object {
        fun relativeUploadFolder(assignmentId: String, submissionDate: Date) = "$assignmentId/${SimpleDateFormat("w-yy").format(submissionDate)}"
    }

}
