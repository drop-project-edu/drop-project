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
package org.dropproject.services

import org.dropproject.TestsHelper
import org.dropproject.config.PendingExport
import org.dropproject.config.PendingTasks
import org.dropproject.dao.Assignment
import org.dropproject.dao.RebuildStatus
import org.dropproject.dao.SubmissionStatus
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.RebuildStatusRepository
import org.dropproject.repository.SubmissionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import java.io.File
import java.util.*

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations=["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class ScheduledTasksTests {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var testsHelper: TestsHelper

    @Autowired
    lateinit var scheduledTasks: ScheduledTasks

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var rebuildStatusRepository: RebuildStatusRepository

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var pendingTasks: PendingTasks

    @Test
    @DirtiesContext
    fun `cleanExpiredSubmissions aborts stale submissions and rebuilds but leaves a fresh rebuild alone`() {

        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
                gitRepositoryFolder = "testJavaProj")
        assignmentRepository.save(assignment01)

        testsHelper.makeSeveralSubmissions(listOf("projectInvalidStructure1", "projectInvalidStructure1",
            "projectInvalidStructure1", "projectInvalidStructure1"), mvc)

        val twoHoursAgo = Date(System.currentTimeMillis() - 2 * 3600 * 1000)
        // statusDate is deliberately frozen to an old value when entering REBUILDING (dontUpdateStatusDate=true),
        // so both the stuck and the fresh rebuild below get an old statusDate on purpose - only their
        // RebuildStatus.startedAt differs
        val threeDaysAgo = Date(System.currentTimeMillis() - 3 * 24 * 3600 * 1000)
        val oneMinuteAgo = Date(System.currentTimeMillis() - 60 * 1000)

        // 1) stale SUBMITTED submission -> should be aborted
        val staleSubmitted = submissionRepository.findById(1).get()
        staleSubmitted.setStatus(SubmissionStatus.SUBMITTED)
        staleSubmitted.statusDate = twoHoursAgo
        submissionRepository.save(staleSubmitted)

        // 2) stale SUBMITTED_FOR_REBUILD submission -> should be aborted
        val staleSubmittedForRebuild = submissionRepository.findById(2).get()
        staleSubmittedForRebuild.setStatus(SubmissionStatus.SUBMITTED_FOR_REBUILD)
        staleSubmittedForRebuild.statusDate = twoHoursAgo
        submissionRepository.save(staleSubmittedForRebuild)

        // 3) stuck REBUILDING submission, rebuild started 2 hours ago -> should be aborted
        val stuckRebuilding = submissionRepository.findById(3).get()
        stuckRebuilding.setStatus(SubmissionStatus.REBUILDING, dontUpdateStatusDate = true)
        stuckRebuilding.statusDate = threeDaysAgo
        submissionRepository.save(stuckRebuilding)
        rebuildStatusRepository.save(RebuildStatus(submission = stuckRebuilding, startedAt = twoHoursAgo))

        // 4) REBUILDING submission whose rebuild just started a minute ago -> must NOT be aborted, even though
        // its statusDate is just as old as the stuck one above (this is the regression this test guards against:
        // statusDate can't be used to tell a fresh rebuild from a stuck one)
        val freshRebuilding = submissionRepository.findById(4).get()
        freshRebuilding.setStatus(SubmissionStatus.REBUILDING, dontUpdateStatusDate = true)
        freshRebuilding.statusDate = threeDaysAgo
        submissionRepository.save(freshRebuilding)
        rebuildStatusRepository.save(RebuildStatus(submission = freshRebuilding, startedAt = oneMinuteAgo))

        scheduledTasks.cleanExpiredSubmissions()

        assertEquals(SubmissionStatus.ABORTED_BY_TIMEOUT, submissionRepository.findById(1).get().getStatus())
        assertEquals(SubmissionStatus.ABORTED_BY_TIMEOUT, submissionRepository.findById(2).get().getStatus())
        assertEquals(SubmissionStatus.ABORTED_BY_TIMEOUT, submissionRepository.findById(3).get().getStatus())
        assertEquals(SubmissionStatus.REBUILDING, submissionRepository.findById(4).get().getStatus())

        // the tracking row for the aborted rebuild is cleaned up...
        assertNull(rebuildStatusRepository.findBySubmissionId(stuckRebuilding.id))
        // ...while the one for the still-running rebuild is left alone
        assertEquals(oneMinuteAgo.time, rebuildStatusRepository.findBySubmissionId(freshRebuilding.id)!!.startedAt.time)
    }

    @Test
    @DirtiesContext
    fun `cleanExpiredExports only deletes the exports that are too old`() {

        val zipFile = File.createTempFile("export", ".zip")

        val beforeTheExport = System.currentTimeMillis() - 1000
        pendingTasks.put("taskId", PendingExport("export", zipFile))
        val afterTheExport = System.currentTimeMillis() + 1000

        // while it hasn't expired, the export is kept
        assertEquals(0, scheduledTasks.deleteExportsCreatedBefore(beforeTheExport))
        assertTrue("the export shouldn't have been deleted yet", zipFile.exists())
        assertNotNull(pendingTasks.get("taskId"))

        // ... but once it expires, both the file and the task are disposed of
        assertEquals(1, scheduledTasks.deleteExportsCreatedBefore(afterTheExport))
        assertFalse("the expired export should have been deleted", zipFile.exists())
        assertNull(pendingTasks.get("taskId"))
    }
}