package org.dropProject.controllers

import org.dropProject.TestsHelper
import org.dropProject.dao.Assignment
import org.dropProject.dao.Submission
import org.dropProject.dao.SubmissionStatus
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.AssignmentRepository
import org.dropProject.repository.SubmissionRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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
                .param("showMavenOutput", "true"))
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
        val submissions = result.modelAndView.modelMap["pendingSubmissions"] as List<Submission>
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
        val submissions2 = result2.modelAndView.modelMap["pendingSubmissions"] as List<Submission>
        assertEquals(1, submissions2.size)

        // abort
        this.mvc.perform(post("/admin/abort/1"))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/admin/showPending"))
    }
}