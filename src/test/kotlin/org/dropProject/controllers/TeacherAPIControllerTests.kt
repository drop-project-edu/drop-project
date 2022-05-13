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
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class TeacherAPIControllerTests: APIControllerTests {

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
            get("/api/teacher/assignments/current")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DirtiesContext
    fun `try to get current assignments with invalid token`() {
        this.mvc.perform(
            get("/api/teacher/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", "invalid")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DirtiesContext
    fun `try to get current assignments with a student profile`() {

        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        this.mvc.perform(
            get("/api/teacher/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", token)))
            .andExpect(status().isForbidden)
    }

    @Test
    @DirtiesContext
    @Transactional  // for some reason, this has to be executed transactionally otherwise it will get assignments created by other tests
    fun `try to get current assignments with a teacher profile`() {

        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                [
                   {"id":"testJavaProj",
                    "name":"Test Project (for automatic tests)",
                    "packageName":"org.dropProject.sampleAssignments.testProj",
                    "dueDate":null,
                    "submissionMethod":"UPLOAD",
                    "language":"JAVA",
                    "ownerUserId": "teacher1",
                    "gitRepositoryUrl":"git://dummy",
                    "active":true }
                ]
            """.trimIndent()))
    }
}
