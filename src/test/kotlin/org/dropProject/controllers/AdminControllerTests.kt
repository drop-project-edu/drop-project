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
package org.dropproject.controllers

import org.dropproject.DropProjectIntegrationTest
import org.dropproject.TestsHelper
import org.dropproject.dao.Assignment
import org.dropproject.dao.AssignmentTag
import org.dropproject.dao.RebuildStatus
import org.dropproject.dao.Submission
import org.dropproject.dao.SubmissionStatus
import org.dropproject.data.OrphanedProcess
import org.dropproject.forms.SubmissionMethod
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.hamcrest.beans.HasPropertyWithValue.hasProperty
import org.hamcrest.CoreMatchers.`is`
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.AssignmentTagRepository
import org.dropproject.repository.RebuildStatusRepository
import org.dropproject.repository.SubmissionRepository
import org.dropproject.services.AssignmentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.MethodOrderer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl
import java.io.File
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.MethodName::class)
@DropProjectIntegrationTest
class AdminControllerTests {

    @Autowired
    lateinit var mvc : MockMvc

    @Autowired
    lateinit var testsHelper: TestsHelper

    @Autowired
    lateinit var submissionRepository : SubmissionRepository

    @Autowired
    lateinit var rebuildStatusRepository : RebuildStatusRepository

    @Autowired
    lateinit var assignmentRepository : AssignmentRepository

    @Autowired
    lateinit var assignmentTagRepository : AssignmentTagRepository

    @Autowired
    lateinit var assignmentService : AssignmentService

    @Test
    @WithMockUser("admin",roles=["DROP_PROJECT_ADMIN"])
    fun test_00_getDashboard() {
        this.mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk)
    }

    @Test
    @WithMockUser("admin",roles=["DROP_PROJECT_ADMIN"])
    fun test_01_changeMavenOutput() {
        this.mvc.perform(post("/admin/dashboard")
                .param("showMavenOutput", "true")
                .param("asyncTimeout", "30")
                .param("threadPoolSize", "1"))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/admin/dashboard"))
    }

    @Test
    @WithMockUser("admin",roles=["DROP_PROJECT_ADMIN"])
    fun test_02_showPendingAndAbort() {
        val result = this.mvc.perform(get("/admin/showPending"))
                .andExpect(status().isOk)
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val submissions = result.modelAndView!!.modelMap["pendingSubmissions"] as List<Submission>
        assertEquals(0, submissions.size)

        // make a submission
        // create initial assignments
        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
                gitRepositoryFolder = "testJavaProj")
        assignmentRepository.save(assignment01)
        testsHelper.makeSeveralSubmissions(listOf("projectInvalidStructure1"), mvc)

        // mark this submission as submitted
        val submission = submissionRepository.findById(1)
        submission.get().setStatus(SubmissionStatus.SUBMITTED)
        submissionRepository.save(submission.get())

        // try again
        val result2 = this.mvc.perform(get("/admin/showPending"))
                .andExpect(status().isOk)
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val submissions2 = result2.modelAndView!!.modelMap["pendingSubmissions"] as List<Submission>
        assertEquals(1, submissions2.size)

        // abort
        this.mvc.perform(post("/admin/abort/1"))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/admin/showPending"))
    }

    @Test
    @WithMockUser("admin",roles=["DROP_PROJECT_ADMIN"])
    fun test_03_showPendingIncludesRebuildingAndSubmittedForRebuild() {

        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
                gitRepositoryFolder = "testJavaProj")
        assignmentRepository.save(assignment01)
        testsHelper.makeSeveralSubmissions(listOf("projectInvalidStructure1", "projectInvalidStructure1"), mvc)

        // simulate a submission stuck rebuilding...
        val rebuildingSubmission = submissionRepository.findById(1).get()
        rebuildingSubmission.setStatus(SubmissionStatus.REBUILDING, dontUpdateStatusDate = true)
        submissionRepository.save(rebuildingSubmission)
        rebuildStatusRepository.save(RebuildStatus(submission = rebuildingSubmission))

        // ...and another one that's still waiting to be picked up for a "rebuild full"
        val submittedForRebuild = submissionRepository.findById(2).get()
        submittedForRebuild.setStatus(SubmissionStatus.SUBMITTED_FOR_REBUILD)
        submissionRepository.save(submittedForRebuild)

        val result = this.mvc.perform(get("/admin/showPending"))
                .andExpect(status().isOk)
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val submissions = result.modelAndView!!.modelMap["pendingSubmissions"] as List<Submission>
        assertEquals(setOf(1L, 2L), submissions.map { it.id }.toSet())

        // aborting the stuck rebuild should also clear its RebuildStatus tracking row
        this.mvc.perform(post("/admin/abort/1"))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/admin/showPending"))

        assertEquals(SubmissionStatus.ABORTED_BY_TIMEOUT, submissionRepository.findById(1).get().getStatus())
        assertEquals(null, rebuildStatusRepository.findBySubmissionId(1))
    }

    @Test
    @WithMockUser("admin",roles=["DROP_PROJECT_ADMIN"])
    fun test_04_showPendingListsAndKillsARealOrphanedMavenProcess() {

        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
                gitRepositoryFolder = "testJavaProj", acceptsStudentTests = true)
        assignmentRepository.save(assignment01)

        // submit a project whose own test loops forever. In the "test" profile checkProject runs synchronously
        // (SyncTaskExecutor), so this real (never-ending) Maven build has to be kicked off on a background thread,
        // otherwise it would block this test forever.
        val uploadThread = Thread {
            testsHelper.uploadProject(mvc, "projectWithInfiniteLoop", "testJavaProj", testsHelper.STUDENT_1)
        }
        uploadThread.isDaemon = true
        uploadThread.start()

        try {
            // the submission row is saved (with status SUBMITTED) before the Maven build even starts, so it's
            // safe to assume it'll be submission #1 in this freshly-dirtied context, well before the build itself
            // (which never finishes on its own) completes
            val submissionDeadline = System.currentTimeMillis() + 10_000
            while (!submissionRepository.findById(1).isPresent && System.currentTimeMillis() < submissionDeadline) {
                Thread.sleep(200)
            }
            assertTrue(submissionRepository.findById(1).isPresent, "submission should have been created")

            // poll until the real Maven/Surefire process(es) for this submission show up as orphaned. In the
            // "test" profile the async timeout is hardcoded to 0, so anything alive for at least a second
            // already qualifies - this just waits for Maven itself to actually start them up
            var orphaned: List<OrphanedProcess> = emptyList()
            val orphanDeadline = System.currentTimeMillis() + 60_000
            while (orphaned.isEmpty() && System.currentTimeMillis() < orphanDeadline) {
                val result = this.mvc.perform(get("/admin/showPending"))
                        .andExpect(status().isOk)
                        .andReturn()

                @Suppress("UNCHECKED_CAST")
                val all = result.modelAndView!!.modelMap["orphanedProcesses"] as List<OrphanedProcess>
                orphaned = all.filter { it.submissionId == 1L }
                if (orphaned.isEmpty()) Thread.sleep(1000)
            }
            assertTrue(orphaned.isNotEmpty(), "the real Maven build should have shown up as an orphaned process")

            // killing an unrelated pid must be refused and not affect the real build
            this.mvc.perform(post("/admin/killProcess/999999999"))
                    .andExpect(status().isFound)
                    .andExpect(header().string("Location", "/admin/showPending"))
                    .andExpect(flash().attribute("error", "Process 999999999 is not a recognized orphaned build process"))
            assertTrue(orphaned.all { ProcessHandle.of(it.pid).map { ph -> ph.isAlive }.orElse(false) })

            for (process in orphaned) {
                this.mvc.perform(post("/admin/killProcess/${process.pid}"))
                        .andExpect(status().isFound)
                        .andExpect(header().string("Location", "/admin/showPending"))
            }

            // killing the top-level Maven process should unblock the background thread's call, one way or another
            uploadThread.join(30_000)
            assertFalse(uploadThread.isAlive, "the background upload should have completed once its process was killed")

            for (process in orphaned) {
                assertFalse(ProcessHandle.of(process.pid).map { it.isAlive }.orElse(false), "process ${process.pid} should no longer be alive")
            }

        } finally {
            // safety net: never leave a real hanging process or thread behind, even if an assertion above failed.
            // Force-kill anything still tagged with this test's submission directly, bypassing the app entirely.
            val marker = "-DdropProject.submissionId=1"
            ProcessHandle.allProcesses()
                .filter { it.isAlive }
                .filter { ph -> ph.info().arguments().map { args -> args.contains(marker) }.orElse(false) }
                .forEach { it.destroyForcibly() }
            uploadThread.interrupt()
        }
    }

    @Test
    @WithMockUser("admin",roles=["DROP_PROJECT_ADMIN"])
    fun `test showTags displays all tags with usage counts and then deletes one`() {

        val assignment1 = Assignment(id = "testJavaProj1", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj")
        val assignment2 = Assignment(id = "testJavaProj2", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj")
        assignmentRepository.save(assignment1)
        assignmentRepository.save(assignment2)

        assignmentService.addTagToAssignment(assignment1, "tag1")
        assignmentService.addTagToAssignment(assignment1, "tag2")
        assignmentService.addTagToAssignment(assignment2, "tag2")

        val result = this.mvc.perform(get("/admin/tags"))
            .andExpect(status().isOk)
            .andExpect(view().name("admin-tags"))
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val tagsWithUsage = result.modelAndView!!.modelMap["tagsWithUsage"] as List<Pair<AssignmentTag, Long>>

        assertEquals(2, tagsWithUsage.size)
        val tag1 = tagsWithUsage[0]
        assertEquals("tag1", (tag1.first).name)
        assertEquals(1, tag1.second)

        val tag2 = tagsWithUsage[1]
        assertEquals("tag2", (tag2.first).name)
        assertEquals(2, tag2.second)

        val tagToDelete = assignmentTagRepository.findByName("tag2") ?: throw AssertionError("Tag not found")

        this.mvc.perform(post("/admin/deleteTag")
            .param("tagId", tagToDelete.id.toString()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/tags"))
            .andExpect(flash().attribute("message", "Tag deleted successfully."))

        // Verify that the tag was deleted
        assertEquals(1, assignmentTagRepository.count()) // Only one tag should remain
        assertEquals("tag1", assignmentTagRepository.findAll()[0].name)
    }

    @Test
    @WithMockUser("admin", roles = ["DROP_PROJECT_ADMIN"])
    fun `test getAllAssignments shows only non-archived assignments`() {
        val activeAssignment = Assignment(id = "activeProj", name = "Active Project",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "activeProj")
        val archivedAssignment = Assignment(id = "archivedProj", name = "Archived Project",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher2",
            submissionMethod = SubmissionMethod.UPLOAD, active = false, archived = true,
            gitRepositoryUrl = "git://dummyRepo", gitRepositoryFolder = "archivedProj")
        assignmentRepository.save(activeAssignment)
        assignmentRepository.save(archivedAssignment)

        val result = mvc.perform(get("/admin/assignments"))
            .andExpect(status().isOk)
            .andExpect(view().name("admin-assignments-list"))
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val assignments = result.modelAndView!!.modelMap["assignments"] as List<Assignment>

        assertEquals(1, assignments.size)
        assertEquals("activeProj", assignments[0].id)
        assertEquals("teacher1", assignments[0].ownerUserId)
    }

    @Test
    @WithMockUser("admin", roles = ["DROP_PROJECT_ADMIN"])
    fun `test getAllAssignments shows assignments from all owners`() {
        val assignment1 = Assignment(id = "proj-teacher1", name = "Project Teacher 1",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "proj-teacher1")
        val assignment2 = Assignment(id = "proj-teacher2", name = "Project Teacher 2",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher2",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "proj-teacher2")
        assignmentRepository.save(assignment1)
        assignmentRepository.save(assignment2)

        val result = mvc.perform(get("/admin/assignments"))
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val assignments = result.modelAndView!!.modelMap["assignments"] as List<Assignment>

        assertEquals(2, assignments.size)
        assertEquals("teacher1", assignments.find { it.id == "proj-teacher1" }?.ownerUserId)
        assertEquals("teacher2", assignments.find { it.id == "proj-teacher2" }?.ownerUserId)
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `test getAllAssignments is forbidden for non-admins`() {
        mvc.perform(get("/admin/assignments"))
            .andExpect(forwardedUrl("/access-denied"))
    }
}
