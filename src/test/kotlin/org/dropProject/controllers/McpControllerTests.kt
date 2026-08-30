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

import org.dropproject.AssignmentFixtures
import org.dropproject.SubmissionFixtures
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.dropproject.dao.Assignment
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssignmentRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@DropProjectIntegrationTest
@Tag("integration")
class McpControllerTests: ApiTestSupport {

    @Autowired
    lateinit var assignmentFixtures: AssignmentFixtures

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var submissionFixtures: SubmissionFixtures

    private fun getBearerToken(username: String): String {
        // Generate personal token for user and use it directly as Bearer token
        val personalToken = generateToken(username, mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)
        return "Bearer $personalToken"
    }

    @BeforeEach
    fun setup() {
        // Create test assignments
        assignmentFixtures.createDefaultAssignment(id = "testMcpAssignment", name = "Test MCP Assignment",
            packageName = "org.dropProject.samples.testAssignment")

        // create initial assignments
        assignmentFixtures.createDefaultAssignment()
    }

    @Test
    fun `try to initialize without a bearer token`() {
        // McpBearerTokenFilter lets the unauthenticated requests through, so this is reported by the entry point of
        // the chain. it must be a 401, telling the client to authenticate, and not the default empty 403
        mvc.perform(
            post("/mcp/")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"jsonrpc": "2.0", "id": "init-1", "method": "initialize"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(content().json("""{"error":"Token Authentication failed"}"""))
    }

    @Test
    fun `try to initialize with an invalid bearer token`() {
        mvc.perform(
            post("/mcp/")
                .header("Authorization", "Bearer thisIsNotAToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"jsonrpc": "2.0", "id": "init-1", "method": "initialize"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(content().json("""{"error":"Token Authentication failed"}"""))
    }

    @Test
    fun `mcp initialize`() {
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
    fun `mcp tools list`() {
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
                            "description": "Retrieve a list of source code files from a student submission. Returns a list of file paths that can be individually fetched using the get_file_content tool. This approach allows selective file retrieval and avoids large response payloads. Useful for code review, debugging, providing feedback, or AI-assisted analysis of student work. Requires teacher privileges.",
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
                        },
                        {
                            "name": "get_file_content",
                            "description": "Retrieve the content of a specific source file from a student submission. Returns the file content as text with the appropriate MIME type. Use this after calling get_submission_code to retrieve individual file contents. Requires teacher privileges.",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "submissionId": {
                                        "type": "number",
                                        "description": "The numeric ID of the submission"
                                    },
                                    "path": {
                                        "type": "string",
                                        "description": "The relative path to the file within the submission (e.g., 'src/main/java/Main.java')"
                                    }
                                },
                                "required": ["submissionId", "path"]
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
    fun `mcp get assignment info`() {
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
    fun `mcp search assignments`() {
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
    fun `mcp get submission code`() {
        val authHeader = getBearerToken("teacher1")

        // First, create a submission
        val submissionId = submissionFixtures.uploadProject("projectOK", "testJavaProj",
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("# Submission ${submissionId} - Source Files")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("**Assignment:** testJavaProj")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("## Available Files")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("get_file_content")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("src/main/java/org/dropProject/sampleAssignments/testProj/Main.java")))
    }

    @Test
    fun `mcp get submission info`() {
        val authHeader = getBearerToken("teacher1")

        // First, create a submission
        val submissionId = submissionFixtures.uploadProject("projectOK", "testJavaProj",
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

    @Test
    fun `mcp get file content`() {
        val authHeader = getBearerToken("teacher1")

        // First, create a submission
        val submissionId = submissionFixtures.uploadProject("projectOK", "testJavaProj",
            User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT"))))

        // Read a specific file
        val filePath = "src/main/java/org/dropProject/sampleAssignments/testProj/Main.java"
        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "test-6",
                "method": "tools/call",
                "params": {
                    "name": "get_file_content",
                    "arguments": {
                        "submissionId": ${submissionId},
                        "path": "$filePath"
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":\"test-6\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"text\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"mimeType\":\"text/x-java\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("class Main")))
    }
}