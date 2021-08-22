package org.dropProject.data

/**
 * Represents the response after a submission. It will be converted to JSON
 */
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