package org.dropProject.controllers

import org.dropProject.TestsHelper
import org.dropProject.dao.Assignee
import org.dropProject.dao.Assignment
import org.dropProject.dao.PersonalToken
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.AssigneeRepository
import org.dropProject.repository.AssignmentRepository
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class StudentAPIControllerTests {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var assigneeRepository: AssigneeRepository

    @Autowired
    private lateinit var testsHelper: TestsHelper

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
    @DirtiesContext
    fun `try to get current assignments without authentication`() {
        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DirtiesContext
    fun `try to get current assignments with invalid token`() {
        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", "invalid")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DirtiesContext
    fun `try to get current assignments with student1`() {

        val token = generateToken()

        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", token)))
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

    @Test
    @DirtiesContext
    fun `upload a submission file with invalid structure`() {

        val token = generateToken()

        val submissionId = testsHelper.uploadProjectByAPI(this.mvc, "projectInvalidStructure1", "testJavaProj",
            Pair("student1", token))

        assertEquals(1, submissionId)

        this.mvc.perform(
            get("/api/student/submissions/$submissionId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.assignment.id", `is`("testJavaProj")))
            .andExpect(jsonPath("$.submission.status", `is`("VALIDATED")))
            .andExpect(jsonPath("$.structureErrors").isArray)
            .andExpect(jsonPath("$.structureErrors", hasSize<Array<String>>(2)))
//            .andReturn()

//        println(result.getResponse().getContentAsString());
    }

    @Test
    @DirtiesContext
    fun `upload a submission file with failing tests`() {

        val token = generateToken()

        val submissionId = testsHelper.uploadProjectByAPI(this.mvc, "projectJUnitErrors", "testJavaProj",
            Pair("student1", token))

        assertEquals(1, submissionId)

        this.mvc.perform(
            get("/api/student/submissions/$submissionId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.assignment.id", `is`("testJavaProj")))
            .andExpect(jsonPath("$.submission.status", `is`("VALIDATED")))
            .andExpect(jsonPath("$.summary[0].reportKey", `is`("PS")))
            .andExpect(jsonPath("$.summary[0].reportValue", `is`("OK")))
            .andExpect(jsonPath("$.summary[3].reportKey", `is`("TT")))
            .andExpect(jsonPath("$.summary[3].reportValue", `is`("NOK")))
            .andExpect(jsonPath("$.buildReport.junitSummaryTeacher", startsWith("Tests run: 2, Failures: 1, Errors: 0")))
            .andExpect(jsonPath("$.buildReport.junitErrorsTeacher",
                startsWith("FAILURE: org.dropProject.sampleAssignments.testProj.TestTeacherProject.testFuncaoParaTestar")))

    }

    private fun generateToken(): String {
        // first generate a token
        this.mvc.perform(
            post("/personalToken")
                .with(user("student1"))
        )
            .andExpect(status().isFound)  // redirect

        val mvcResult = this.mvc.perform(
            get("/personalToken")
                .with(user("student1"))
        )
            .andExpect(status().isOk)
            .andReturn()

        val token = (mvcResult.modelAndView!!.modelMap["token"] as PersonalToken).personalToken
        return token
    }
}