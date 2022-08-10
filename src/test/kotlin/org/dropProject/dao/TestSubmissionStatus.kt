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
package org.dropProject.dao

import junit.framework.TestCase.*
import org.junit.Test
import java.util.*

class TestSubmissionStatus {

    @Test
    fun testSubmissionConstructor() {
        val projGroup = ProjectGroup()
        val date = Date()
        var submission = Submission("123", "proj-lp2-1920-p1", null, "1", "V", date, projGroup, "")
        assertEquals("123", submission.submissionId)
        assertEquals("proj-lp2-1920-p1", submission.assignmentId)
        assertEquals(date, submission.statusDate)
        assertEquals(projGroup, submission.group)
        assertEquals("", submission.submissionFolder)
    }

    @Test
    fun testSubmissionSetCoverage() {
        var submission = Submission("123", "proj-lp2-19-20-p1", null, "1", "V", Date(), ProjectGroup(), "")
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
