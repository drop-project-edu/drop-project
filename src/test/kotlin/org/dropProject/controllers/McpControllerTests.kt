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
        
        val assignment2 = Assignment(
            id = "javaProject",
            name = "Java Programming Project",
            packageName = "org.dropProject.samples.javaProject",
            ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.GIT,
            active = true,
            gitRepositoryUrl = "git://dummy2",
            gitRepositoryFolder = "javaProject"
        )
        assignmentRepository.save(assignment2)
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Java Programming Project")))
    }
}