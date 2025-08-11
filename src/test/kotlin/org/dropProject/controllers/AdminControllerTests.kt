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
package org.dropProject.controllers

import org.dropProject.TestsHelper
import org.dropProject.dao.Assignment
import org.dropProject.dao.AssignmentTag
import org.dropProject.dao.Submission
import org.dropProject.dao.SubmissionStatus
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.AssignmentRepository
import org.dropProject.repository.AssignmentTagRepository
import org.dropProject.repository.SubmissionRepository
import org.dropProject.services.AssignmentService
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
}
