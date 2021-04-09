package org.dropProject.data

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*


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
    var buildReport: String? = null,
    var structureErrors: String? = null,
    var markedAsFinal: Boolean = false,
    var authors: List<Author>,
    var submissionReport: List<SubmissionReport>
)
{

    data class Author(val userId: String, val name: String)
    data class SubmissionReport(val key: String,
                                val value: String,
                                val progress: Int?,
                                val goal: Int?)

}

