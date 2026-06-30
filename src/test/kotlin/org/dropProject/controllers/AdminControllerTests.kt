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

import org.dropproject.TestsHelper
import org.dropproject.dao.Assignment
import org.dropproject.dao.AssignmentTag
import org.dropproject.dao.Submission
import org.dropproject.dao.SubmissionStatus
import org.dropproject.forms.SubmissionMethod
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.hamcrest.beans.HasPropertyWithValue.hasProperty
import org.hamcrest.CoreMatchers.`is`
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.AssignmentTagRepository
import org.dropproject.repository.SubmissionRepository
import org.dropproject.services.AssignmentService
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations=["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AdminControllerTests {

    @Autowired
    lateinit var mvc : MockMvc

    @Autowired
    lateinit var testsHelper: TestsHelper

    @Autowired
    lateinit var submissionRepository : SubmissionRepository

    @Autowired
    lateinit var assignmentRepository : AssignmentRepository

    @Autowired
    lateinit var assignmentTagRepository : AssignmentTagRepository

    @Autowired
    lateinit var assignmentService : AssignmentService

    @Test
    @WithMockUser("admin",roles=["DROP_PROJECT_ADMIN"])
    @DirtiesContext
    fun test_00_getDashboard() {
        this.mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk)
    }

    @Test
    @WithMockUser("admin",roles=["DROP_PROJECT_ADMIN"])
    @DirtiesContext
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
    @DirtiesContext
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
    @DirtiesContext
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
    @DirtiesContext
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
    @DirtiesContext
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
    @DirtiesContext
    fun `test getAllAssignments is forbidden for non-admins`() {
        mvc.perform(get("/admin/assignments"))
            .andExpect(forwardedUrl("/access-denied.html"))
    }
}
