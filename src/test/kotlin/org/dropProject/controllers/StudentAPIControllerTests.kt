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

import org.dropproject.AssignmentFixtures
import org.dropproject.SubmissionFixtures
import org.dropproject.basicAuthHeader
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.dropproject.dao.Assignee
import org.dropproject.dao.Assignment
import org.dropproject.dao.AssignmentVisibility
import org.dropproject.dao.Language
import org.dropproject.dao.SubmissionStructure
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssigneeRepository
import org.dropproject.repository.AssignmentRepository
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@DropProjectIntegrationTest
@Tag("integration")
class StudentAPIControllerTests: ApiTestSupport {

    @Autowired
    lateinit var assignmentFixtures: AssignmentFixtures

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var assigneeRepository: AssigneeRepository

    @Autowired
    lateinit var submissionFixtures: SubmissionFixtures

    @BeforeEach
    fun setup() {
        // create initial assignment
        assignmentFixtures.createDefaultAssignment()
        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))
        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student2"))

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
    fun `try to get current assignments without authentication`() {
        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `try to get current assignments with invalid token`() {
        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", basicAuthHeader("student1", "invalid")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `try to get current assignments with a malformed authorization header`() {
        // a header that is not valid base64 is a malformed credential, so it must get the same 401 as an
        // invalid token, instead of blowing up into a 500
        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", "basic not-base64!!"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `try to get current assignments with student1`() {

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", basicAuthHeader("student1", token)))
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
    }

    @Test
    fun `try to get current assignments with student2`() {

        val token = generateToken("student2", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", basicAuthHeader("student2", token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()", `is`(2)))
            .andExpect(jsonPath("$[*].id", containsInAnyOrder("testJavaProj", "testJavaProjPublic")))

        // println(result.getResponse().getContentAsString());
    }

    @Test
    fun `upload a submission file with invalid structure`() {

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        val submissionId = submissionFixtures.uploadProjectByAPI("projectInvalidStructure1", "testJavaProj",
            Pair("student1", token))

        assertEquals(1, submissionId)

        this.mvc.perform(
            get("/api/student/submissions/$submissionId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", basicAuthHeader("student1", token)))
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
    fun `upload a submission file with failing tests`() {

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        val submissionId = submissionFixtures.uploadProjectByAPI("projectJUnitErrors", "testJavaProj",
            Pair("student1", token))

        assertEquals(1, submissionId)

        this.mvc.perform(
            get("/api/student/submissions/$submissionId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", basicAuthHeader("student1", token)))
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
    fun `try to get existent assignment information`() {

        val assignmentId = "sampleJavaProject"

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        this.mvc.perform(
            get("/api/student/assignments/${assignmentId}")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", basicAuthHeader("student1", token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.assignment.id", `is`("sampleJavaProject")))
            .andExpect(jsonPath("$.assignment.language", `is`("JAVA")))
            .andExpect(jsonPath("$.assignment.instructions.format", `is`("HTML")))
            .andExpect(jsonPath("$.assignment.instructions.body", containsString("<h2>Sample Java Assignment</h2>")))
            .andExpect(jsonPath("$.errorCode").doesNotExist())

    }

    @Test
    fun `try to get nonexistent assignment information`() {

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        this.mvc.perform(
            get("/api/student/assignments/nonexistentID")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", basicAuthHeader("student1", token)))
            .andExpect(jsonPath("$.assignment").doesNotExist())
            .andExpect(jsonPath("$.errorCode", `is`(404)))
    }

    @Test
    fun `try to get current assignments with student2 including one with instructions_md`() {

        val assignment = Assignment(id = "testKotlinProj2", name = "Test Project (for automatic tests)",
            packageName = "org.dropproject.samples.samplekotlinassignment", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, language = Language.KOTLIN,
            gitRepositoryUrl = "git://dummyRepo", gitRepositoryFolder = "testKotlinProj2",
            visibility = AssignmentVisibility.PUBLIC)
        assignmentRepository.save(assignment)

        val token = generateToken("student2", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        val result = this.mvc.perform(
            get("/api/student/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", basicAuthHeader("student2", token)))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                [
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
                    },
                    {
                        "id": "testKotlinProj2",
                        "name": "Test Project (for automatic tests)",
                        "packageName": "org.dropproject.samples.samplekotlinassignment",
                        "language": "KOTLIN"
                    }
                ]
            """.trimIndent()))
            .andExpect(jsonPath("$[1].instructions.format", `is`("HTML")))
            .andExpect(jsonPath("$[1].instructions.body", stringContainsInOrder("<h2>Sample Java Assignment</h2>")))
            .andExpect(jsonPath("$[2].instructions.format", `is`("HTML")))
            .andExpect(jsonPath("$[2].instructions.body", stringContainsInOrder("<h1>Sample Kotlin Assignment</h1>")))
            .andReturn()

//         println(result.getResponse().getContentAsString());
    }

    /**
     * A maven-structured submission carries the pom.xml of the project, which the plugin only started
     * including in [org.dropproject.Constants.MIN_PLUGIN_VERSION_FOR_MAVEN_SUBMISSIONS]. Whoever cannot be
     * shown to be older than that is served.
     */
    private fun uploadMavenProjectAs(userAgent: String?): ResultActions {
        val mavenAssignment = Assignment(
            id = "testMavenProjAPI",
            name = "Test Maven Project",
            packageName = "org.dropProject.sampleAssignments.testProj",
            ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD,
            active = true,
            submissionStructure = SubmissionStructure.MAVEN,  // <<< Maven structure
            gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "sampleJavaProject"
        )
        assignmentRepository.save(mavenAssignment)
        assigneeRepository.save(Assignee(assignmentId = "testMavenProjAPI", authorUserId = "student1"))
        // the AUTHORS.txt of the sample project names both students, and every author has to be an assignee
        assigneeRepository.save(Assignee(assignmentId = "testMavenProjAPI", authorUserId = "student2"))

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        val projectFolder = submissionFixtures.resourceLoader.getResource("file:src/test/sampleProjects/maven/java/projectOK-maven").file
        val zipFile = submissionFixtures.zipService.createZipFromFolder("test", projectFolder)
        zipFile.deleteOnExit()

        return this.mvc.perform(
            multipart("/api/student/submissions/new")
                .file(MockMultipartFile("file", zipFile.name, "application/zip", zipFile.readBytes()))
                .param("assignmentId", "testMavenProjAPI")
                .header("authorization", basicAuthHeader("student1", token))
                .apply { if (userAgent != null) header(HttpHeaders.USER_AGENT, userAgent) })
    }

    @Test
    fun `upload to a Maven assignment from a plugin that knows how to build the zip`() {
        uploadMavenProjectAs("DropProjectPlugin/0.9.15 (IntelliJ IDEA 2024.3)")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.submissionId").isNumber)
    }

    @Test
    fun `upload to a Maven assignment from a plugin too old to send the pom`() {
        uploadMavenProjectAs("DropProjectPlugin/0.9.14 (IntelliJ IDEA 2024.3)")
            .andExpect(status().isInternalServerError)
            // the student is told that the plugin is the problem, and not that a pom.xml they never wrote is
            // missing, which is what the structure validation would have reported
            .andExpect(jsonPath("$.error", containsString("only knows how to submit from version 0.9.15")))
    }

    @Test
    fun `upload to a Maven assignment from a client that does not identify itself`() {
        // a script written against the api builds its own zip, so it is left to it
        uploadMavenProjectAs(null)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.submissionId").isNumber)
    }

    @Test
    fun `upload to a Maven assignment from something that is not the plugin`() {
        uploadMavenProjectAs("curl/8.7.1")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.submissionId").isNumber)
    }

}
