/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2025 Pedro Alves
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
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssignmentRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
@Transactional
class McpControllerTests: APIControllerTests {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    private lateinit var testsHelper: TestsHelper

    private fun getBearerToken(username: String): String {
        // Generate personal token for user and use it directly as Bearer token
        val personalToken = generateToken(username, mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)
        return "Bearer $personalToken"
    }

    @Before
    fun setup() {
        // Create test assignments
        val assignment1 = Assignment(
            id = "testMcpAssignment",
            name = "Test MCP Assignment",
            packageName = "org.dropProject.samples.testAssignment",
            ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD,
            active = true,
            gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testMcpAssignment"
        )
        assignmentRepository.save(assignment1)

        // create initial assignments
        val assignment02 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj"
        )
        assignmentRepository.save(assignment02)
    }

    @Test
    @DirtiesContext
    fun testMcpInitialize() {
        val authHeader = getBearerToken("teacher1")
        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "init-1",
                "method": "initialize"
            }
        """.trimIndent()

        mvc.perform(
            post("/mcp/")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\":\"DropProject\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"protocolVersion\":\"2024-11-05\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"version\":\"1.0.0\"")))
    }

    @Test
    @DirtiesContext
    fun testMcpToolsList() {
        val authHeader = getBearerToken("teacher1")
        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "test-1",
                "method": "tools/list"
            }
        """.trimIndent()

        val expectedResponse = """
            {
                "jsonrpc": "2.0",
                "id": "test-1",
                "result": {
                    "tools": [
                        {
                            "name": "get_assignment_info",
                            "description": "Get comprehensive information about a programming assignment in Drop Project, including instructions, requirements, due dates, submission methods, and grading criteria. Useful when a student or teacher needs detailed assignment context.",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "assignmentId": {
                                        "type": "string",
                                        "description": "The ID of the assignment to retrieve"
                                    }
                                },
                                "required": ["assignmentId"]
                            }
                        },
                        {
                            "name": "search_assignments",
                            "description": "Search Drop Project assignments by name, ID, or programming language tags. Returns matching assignments with basic metadata. Useful for finding relevant assignments or exploring available coursework.",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "query": {
                                        "type": "string",
                                        "description": "Search query to match assignment names, IDs, or tags"
                                    }
                                },
                                "required": ["query"]
                            }
                        },
                        {
                            "name": "search_student",
                            "description": "Search for students by student ID or name (partial matching) and retrieve their complete submission history. Returns student information along with assignment IDs and submission IDs for detailed lookup. Useful for tracking student progress, identifying submission patterns, or providing academic support.",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "query": {
                                        "type": "string",
                                        "description": "Search query to match student IDs or names (case-insensitive partial matching)"
                                    }
                                },
                                "required": ["query"]
                            }
                        },
                        {
                            "name": "get_submission_code",
                            "description": "Retrieve all source code files from a student submission as a single concatenated document. Returns the complete mavenized source code with each file clearly separated and marked with its path. Useful for code review, debugging, providing feedback, or AI-assisted analysis of student work. Requires teacher privileges.",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "submissionId": {
                                        "type": "number",
                                        "description": "The numeric ID of the submission to retrieve code from"
                                    }
                                },
                                "required": ["submissionId"]
                            }
                        },
                        {
                            "name": "get_submission_info",
                            "description": "Retrieve detailed information about a student submission, including build status, test results, compilation errors, code quality issues, and group member information. This provides a comprehensive report similar to what appears on the build report page. Teachers see additional information including hidden tests. Students can only access their own submissions.",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "submissionId": {
                                        "type": "number",
                                        "description": "The numeric ID of the submission to retrieve information from"
                                    }
                                },
                                "required": ["submissionId"]
                            }
                        }
                    ]
                }
            }
        """.trimIndent()

        mvc.perform(
            post("/mcp/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andExpect(content().json(expectedResponse))
    }

    @Test
    @DirtiesContext
    fun testMcpGetAssignmentInfo() {
        val authHeader = getBearerToken("teacher1")
        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "test-2",
                "method": "tools/call",
                "params": {
                    "name": "get_assignment_info",
                    "arguments": {
                        "assignmentId": "testMcpAssignment"
                    }
                }
            }
        """.trimIndent()

        mvc.perform(
            post("/mcp/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"jsonrpc\":\"2.0\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":\"test-2\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("testMcpAssignment")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Test MCP Assignment")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Assignment:")))
    }

    @Test
    @DirtiesContext
    fun testMcpSearchAssignments() {
        val authHeader = getBearerToken("teacher1")
        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "test-3",
                "method": "tools/call",
                "params": {
                    "name": "search_assignments",
                    "arguments": {
                        "query": "java"
                    }
                }
            }
        """.trimIndent()

        mvc.perform(
            post("/mcp/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"jsonrpc\":\"2.0\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":\"test-3\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Found")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Project (for automatic tests)")))
    }

    @Test
    @DirtiesContext
    fun testMcpGetSubmissionCode() {
        val authHeader = getBearerToken("teacher1")

        // First, create a submission
        val submissionId = testsHelper.uploadProject(this.mvc,
            "projectOK", "testJavaProj",
            User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT"))))

        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "test-4",
                "method": "tools/call",
                "params": {
                    "name": "get_submission_code",
                    "arguments": {
                        "submissionId": ${submissionId}
                    }
                }
            }
        """.trimIndent()

        mvc.perform(
            post("/mcp/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"jsonrpc\":\"2.0\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":\"test-4\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("# Submission ${submissionId}")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("**Assignment:** testJavaProj")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("## File:")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("```java")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("class Main")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Teacher file")))
    }

    @Test
    @DirtiesContext
    fun testMcpGetSubmissionInfo() {
        val authHeader = getBearerToken("teacher1")

        // First, create a submission
        val submissionId = testsHelper.uploadProject(this.mvc,
            "projectOK", "testJavaProj",
            User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT"))))

        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "test-5",
                "method": "tools/call",
                "params": {
                    "name": "get_submission_info",
                    "arguments": {
                        "submissionId": ${submissionId}
                    }
                }
            }
        """.trimIndent()

        mvc.perform(
            post("/mcp/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"jsonrpc\":\"2.0\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":\"test-5\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("# Build Report for Submission ${submissionId}")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("**Assignment:** testJavaProj")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("**Submitted:**")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("**Total Submissions:**")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("## Group Members")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("## Results Summary")))

    }
}