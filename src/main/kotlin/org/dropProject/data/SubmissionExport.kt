/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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
package org.dropProject.data

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import org.dropProject.dao.SubmissionMode
import java.util.*
import javax.persistence.Column

const val EXPORTED_ASSIGNMENT_JSON_FILENAME = "assignment.json"
const val EXPORTED_SUBMISSIONS_JSON_FILENAME = "submissions.json"
const val EXPORTED_GIT_SUBMISSIONS_JSON_FILENAME = "git-submissions.json"
const val EXPORTED_ORIGINAL_SUBMISSIONS_FOLDER = "original"

/**
 * Represents all the data pertaining a submission, including "sibling" tables such as SubmissionReport, etc.
 * The goal is to be used to export submissions in JSON format
 */
data class SubmissionExport(
    @JsonIgnore
    var id: Long = 0,
    var submissionId: String? = null,
    var gitSubmissionId: Long? = null,
    var submissionFolder: String? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    var submissionDate: Date,
    var submitterUserId: String,
    var status: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    var statusDate: Date,
    var assignmentId: String,
    var assignmentGitHash: String? = null,
    var buildReport: String? = null,
    var structureErrors: String? = null,
    var markedAsFinal: Boolean = false,
    var authors: List<Author>,
    var submissionReport: List<SubmissionReport>,
    var junitReports: List<JUnitReport>?,
    var jacocoReports: List<JacocoReport>?,
    var submissionMode: SubmissionMode? = null
)
{

    data class Author(val userId: String, val name: String)
    data class SubmissionReport(val key: String,
                                val value: String,
                                val progress: Int?,
                                val goal: Int?)
    data class JUnitReport(val filename: String, val xmlReport: String)
    data class JacocoReport(val filename: String, val csvReport: String)

}

