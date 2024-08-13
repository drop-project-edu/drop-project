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
import org.dropProject.dao.Assignee
import org.dropProject.dao.Assignment
import org.dropProject.dao.AssignmentVisibility
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.AssigneeRepository
import org.dropProject.repository.AssignmentRepository
import org.hamcrest.Matchers.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class StudentAPIControllerTests: APIControllerTests {

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

        val assignment02 = Assignment(id = "testKotlinProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testKotlinProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = false, gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testKotlinProj")
        assignmentRepository.save(assignment02)
        assigneeRepository.save(Assignee(assignmentId = "testKotlinProj", authorUserId = "student1"))

        val assignmentWithInstructions = Assignment(id = "sampleJavaProject", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.GIT, active = true, gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "sampleJavaProject")
        assignmentRepository.save(assignmentWithInstructions)
        assigneeRepository.save(Assignee(assignmentId = "sampleJavaProject", authorUserId = "student1"))

        val publicAssignment = Assignment(id = "testJavaProjPublic", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "sampleJavaProject", visibility = AssignmentVisibility.PUBLIC)
        assignmentRepository.save(publicAssignment)

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

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", token)))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                [
                    {
                        "id": "sampleJavaProject",
                        "name": "Test Project (for automatic tests)",
                        "packageName": "org.dropProject.samples.sampleJavaAssignment",
                        "dueDate": null,
                        "submissionMethod": "GIT",
                        "language": "JAVA",
                        "active": true
                    },
                    {
                        "id": "testJavaProj",
                        "name": "Test Project (for automatic tests)",
                        "packageName": "org.dropProject.sampleAssignments.testProj",
                        "dueDate": null,
                        "submissionMethod": "UPLOAD",
                        "language": "JAVA",
                        "active": true
                    },
                    {
                        "id": "testJavaProjPublic",
                        "name": "Test Project (for automatic tests)",
                        "packageName": "org.dropProject.sampleAssignments.testProj",
                        "dueDate": null,
                        "submissionMethod": "UPLOAD",
                        "language": "JAVA",
                        "active": true
                    }
                ]
            """.trimIndent()))
            .andExpect(jsonPath("$[0].instructions.format", `is`("HTML")))
            .andExpect(jsonPath("$[1].instructions.format", nullValue()))  // this assignment doesn't have instructions
            .andExpect(jsonPath("$[2].instructions.format", `is`("HTML")))
            .andExpect(jsonPath("$[0].instructions.body", stringContainsInOrder("<h2>Sample Java Assignment</h2>")))

        // println(result.getResponse().getContentAsString());
    }

    @Test
    @DirtiesContext
    fun `try to get current assignments with student2`() {

        val token = generateToken("student2", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student2", token)))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                [
                    {
                        "id": "testJavaProjPublic",
                        "name": "Test Project (for automatic tests)",
                        "packageName": "org.dropProject.sampleAssignments.testProj",
                        "dueDate": null,
                        "submissionMethod": "UPLOAD",
                        "language": "JAVA",
                        "active": true
                    }
                ]
            """.trimIndent()))
            .andExpect(jsonPath("$[0].instructions.format", `is`("HTML")))
            .andExpect(jsonPath("$[0].instructions.body", stringContainsInOrder("<h2>Sample Java Assignment</h2>")))

        // println(result.getResponse().getContentAsString());
    }

    @Test
    @DirtiesContext
    fun `upload a submission file with invalid structure`() {

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        val submissionId = testsHelper.uploadProjectByAPI(this.mvc, "projectInvalidStructure1", "testJavaProj",
            Pair("student1", token))

        assertEquals(1, submissionId)

        this.mvc.perform(
            get("/api/student/submissions/$submissionId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.assignment.id", `is`("testJavaProj")))
            .andExpect(jsonPath("$.assignment.submissionMethod", `is`("UPLOAD")))
            .andExpect(jsonPath("$.submission.status", `is`("VALIDATED")))
            .andExpect(jsonPath("$.structureErrors").isArray)
            .andExpect(jsonPath("$.structureErrors", hasSize<Array<String>>(2)))
     //      .andReturn()


        //println(result.getResponse().getContentAsString());
    }

    @Test
    @DirtiesContext
    fun `upload a submission file with failing tests`() {

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

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

    @Test
    @DirtiesContext
    fun `try to get existent assignment information`() {

        val assignmentId = "sampleJavaProject"

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        this.mvc.perform(
            get("/api/student/assignments/${assignmentId}")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.assignment.id", `is`("sampleJavaProject")))
            .andExpect(jsonPath("$.assignment.language", `is`("JAVA")))
            .andExpect(jsonPath("$.assignment.instructions.format", `is`("HTML")))
            .andExpect(jsonPath("$.assignment.instructions.body", containsString("<h2>Sample Java Assignment</h2>")))
            .andExpect(jsonPath("$.errorCode").doesNotExist())

    }

    @Test
    @DirtiesContext
    fun `try to get nonexistent assignment information`() {

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        this.mvc.perform(
            get("/api/student/assignments/nonexistentID")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", token)))
            .andExpect(jsonPath("$.assignment").doesNotExist())
            .andExpect(jsonPath("$.errorCode", `is`(404)))
    }

}
