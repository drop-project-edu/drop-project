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

import org.dropProject.data.JUnitSummary
import org.dropProject.services.JUnitMethodResult
import java.io.File
import java.util.*
import javax.persistence.*
import java.math.BigDecimal
import java.text.SimpleDateFormat

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

/**
 * Represents a Submission, which is a single interaction of the student (or group) with an Assignment.
 */
@Entity
data class Submission(
        @Id @GeneratedValue
        val id: Long = 0,
        val submissionId: String? = null,
        val gitSubmissionId: Long? = null,

        val submissionFolder: String? = null,  // TODO: For now, this is null for git submissions

        @Column(nullable = false)
        var submissionDate: Date,

        @Column(nullable = false)
        val submitterUserId: String,

        @Column(nullable = false)
        private var status: String,  // should be accessed through getStatus() and setStatus()

        @Column(nullable = false)
        var statusDate: Date,

        val assignmentId: String,

        var buildReportId: Long? = null,  // build_report.id

        @Column(columnDefinition = "TEXT")
        var structureErrors: String? = null,

        var markedAsFinal: Boolean = false,

        @Transient
        var reportElements: List<SubmissionReport>? = null,

        @Transient
        var ellapsed: BigDecimal? = null,

        @Transient
        var studentTests: JUnitSummary? = null,

        @Transient
        var teacherTests: JUnitSummary? = null,

        @Transient
        var hiddenTests: JUnitSummary? = null,

        @Transient
        var coverage: Int? = null,

        @Transient
        var testResults: List<JUnitMethodResult>? = null
) {
    @ManyToOne
    lateinit var group: ProjectGroup

    constructor(submissionId: String, assignmentId: String, submitterNumber: String, status: String,
                statusDate: Date, group: ProjectGroup, submissionFolder: String) :
            this(submissionId = submissionId, assignmentId = assignmentId, submitterUserId = submitterNumber,
                    status = status, statusDate = statusDate, submissionDate = Date(), submissionFolder = submissionFolder) {
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


    companion object {
        fun relativeUploadFolder(assignmentId: String, submissionDate: Date) = "$assignmentId/${SimpleDateFormat("w-yy").format(submissionDate)}"
    }

}
