package org.dropProject.data

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Represents the response after a submission. It will be converted to JSON
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // exclude nulls fields from serialization
class SubmissionResult(val submissionId: Long? = null, val error: String? = null) {

    init {
        if (submissionId == null && error == null) {
            throw Exception("You must set at least one of [submissionId, error]")
        }

        if (submissionId != null && error != null) {
            throw Exception("You can't set both [submissionId, error]")
        }
    }

}