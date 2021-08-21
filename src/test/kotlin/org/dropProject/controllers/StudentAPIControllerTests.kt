package org.dropProject.controllers

import org.dropProject.dao.Assignee
import org.dropProject.dao.Assignment
import org.dropProject.dao.PersonalToken
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.AssigneeRepository
import org.dropProject.repository.AssignmentRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

private fun header(username: String, personalToken: String) =
    "basic ${Base64.getEncoder().encodeToString("$username:$personalToken".toByteArray())}"

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class APIControllerTests {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var assigneeRepository: AssigneeRepository

    @Before
    fun setup() {
        // create initial assignment
        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testJavaProj")
        assignmentRepository.save(assignment01)
        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))
    }

    @Test
    fun `try to get current assignments without authentication`() {
        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `try to get current assignments with invalid token`() {
        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", header("student1", "invalid")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `try to get current assignments with student1`() {

        // first generate a token
        this.mvc.perform(
            post("/personalToken")
                .with(user("student1")))
            .andExpect(status().isFound)  // redirect

        val mvcResult = this.mvc.perform(
            get("/personalToken")
                .with(user("student1")))
            .andExpect(status().isOk)
            .andReturn()

        val token = (mvcResult.modelAndView!!.modelMap["token"] as PersonalToken).personalToken

        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", header("student1", token)))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                [
                   {"id":"testJavaProj",
                    "name":"Test Project (for automatic tests)",
                    "packageName":"org.dropProject.sampleAssignments.testProj",
                    "dueDate":null,
                    "submissionMethod":"UPLOAD",
                    "language":"JAVA",
                    "active":true }
                ]
            """.trimIndent()))

        // println(result.getResponse().getContentAsString());
    }
}