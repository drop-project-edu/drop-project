package org.dropProject.dao

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Test
import java.util.*

class TestSubmissionStatus {

    @Test
    fun testSubmissionConstructor() {
        val projGroup = ProjectGroup()
        val date = Date()
        var submission = Submission("123", "proj-lp2-1920-p1", "1", "V", date, projGroup, "")
        assertEquals("123", submission.submissionId)
        assertEquals("proj-lp2-1920-p1", submission.assignmentId)
        assertEquals(date, submission.statusDate)
        assertEquals(projGroup, submission.group)
        assertEquals("", submission.submissionFolder)
    }

    @Test
    fun testSubmissionSetCoverage() {
        var submission = Submission("123", "proj-lp2-19-20-p1", "1", "V", Date(), ProjectGroup(), "")
        submission.coverage = 95
        assertEquals(95, submission.coverage!!)
    }

    @Test
    fun testGetSubmissionStatusInvalid() {
        var exceptionOk = false
        try {
            SubmissionStatus.getSubmissionStatus("Unknown")
        }
        catch(e: IllegalArgumentException) {
            exceptionOk = true
        }
        assertTrue(exceptionOk)
    }

}