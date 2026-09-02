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
import org.dropproject.TestUsers.STUDENT_1
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.dropproject.config.DropProjectProperties
import org.dropproject.dao.Assignment
import org.dropproject.dao.Author
import org.dropproject.dao.ProjectGroup
import org.dropproject.dao.Submission
import org.dropproject.dao.SubmissionStatus
import org.dropproject.forms.SubmissionMethod
import org.dropproject.dao.Language
import org.dropproject.repository.AssigneeRepository
import org.dropproject.repository.AssignmentACLRepository
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.AuthorRepository
import org.dropproject.repository.ProjectGroupRepository
import org.dropproject.repository.SubmissionRepository
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.Date

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

    @Autowired
    lateinit var authorRepository: AuthorRepository

    @Autowired
    lateinit var projectGroupRepository: ProjectGroupRepository

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var assigneeRepository: AssigneeRepository

    @Autowired
    lateinit var assignmentACLRepository: AssignmentACLRepository

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    private fun getBearerToken(username: String, role: String = "ROLE_TEACHER"): String {
        // Generate personal token for user and use it directly as Bearer token
        val personalToken = generateToken(username, mutableListOf(SimpleGrantedAuthority(role)), mvc)
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
            // the instructions tell the client the order of the steps that create an assignment, which it can't
            // infer from the tools alone
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"instructions\":")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("read-only deploy key")))
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
                            "description": "Get comprehensive information about a programming assignment in Drop Project, including instructions, requirements, due dates, submission methods, and grading criteria. Useful when a student or teacher needs detailed assignment context. The assignment's settings are listed with the names of the create_assignment arguments that set them, so that an assignment can be recreated from them, e.g. for a new edition of the same course.",
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
                        },
                    {
                        "name": "create_assignment",
                        "description": "Create a new assignment in Drop Project and get back the ssh public key that must be installed as a read-only deploy key on its git repository. The repository must already exist and contain the teacher's Maven project (pom.xml, unit tests and instructions) - Drop Project only reads repositories, it never creates or writes to them. The assignment is created inactive and disconnected; call connect_assignment once the deploy key is in place. Only teachers can use this tool.",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "assignmentId": {
                                    "type": "string",
                                    "description": "Unique ID of the assignment, also used as the name of the folder where its repository is cloned. Only letters, numbers, hyphens and underscores"
                                },
                                "assignmentName": {
                                    "type": "string",
                                    "description": "Human readable name of the assignment, shown to the students"
                                },
                                "gitRepositoryUrl": {
                                    "type": "string",
                                    "description": "SSH url of the git repository that defines the assignment, e.g. 'git@github.com:owner/repo.git'. Only SSH urls are accepted"
                                },
                                "language": {
                                    "type": "string",
                                    "enum": [
                                        "JAVA",
                                        "KOTLIN"
                                    ],
                                    "description": "Programming language of the assignment. Defaults to JAVA"
                                },
                                "packageName": {
                                    "type": "string",
                                    "description": "Java/Kotlin package of the assignment, e.g. 'org.dropproject.samples'. Without it, Drop Project can't filter the stacktraces shown to the students"
                                },
                                "submissionMethod": {
                                    "type": "string",
                                    "enum": [
                                        "UPLOAD",
                                        "GIT"
                                    ],
                                    "description": "How students submit: UPLOAD of a zip file or connecting their own GIT repository. Defaults to UPLOAD"
                                },
                                "submissionStructure": {
                                    "type": "string",
                                    "enum": [
                                        "COMPACT",
                                        "MAVEN"
                                    ],
                                    "description": "Expected structure of the submitted projects. Defaults to COMPACT"
                                },
                                "dueDate": {
                                    "type": "string",
                                    "description": "Date after which submissions are marked as late, in ISO-8601 format, e.g. '2026-10-15T23:59'. Optional"
                                },
                                "acceptsStudentTests": {
                                    "type": "boolean",
                                    "description": "Whether students are expected to submit their own unit tests"
                                },
                                "minStudentTests": {
                                    "type": "number",
                                    "description": "Minimum number of unit tests that the students must write. Only valid together with acceptsStudentTests"
                                },
                                "calculateStudentTestsCoverage": {
                                    "type": "boolean",
                                    "description": "Whether to calculate the coverage of the students' own tests. Only valid together with acceptsStudentTests"
                                },
                                "hiddenTestsVisibility": {
                                    "type": "string",
                                    "enum": [
                                        "HIDE_EVERYTHING",
                                        "SHOW_OK_NOK",
                                        "SHOW_PROGRESS"
                                    ],
                                    "description": "How much students get to know about the results of the hidden tests"
                                },
                                "mandatoryTestsSuffix": {
                                    "type": "string",
                                    "description": "Suffix of the test methods that students must pass to have their submission considered valid. Optional"
                                },
                                "leaderboardType": {
                                    "type": "string",
                                    "enum": [
                                        "TESTS_OK",
                                        "ELLAPSED",
                                        "COVERAGE"
                                    ],
                                    "description": "Criterion used to sort the leaderboard. Without it, the assignment has no leaderboard"
                                },
                                "cooloffPeriod": {
                                    "type": "number",
                                    "description": "Minutes that students must wait between submissions. Optional"
                                },
                                "maxMemoryMb": {
                                    "type": "number",
                                    "description": "Memory limit, in MB, of the evaluation of each submission. Must be >= 32. Optional"
                                },
                                "minGroupSize": {
                                    "type": "number",
                                    "description": "Minimum number of students per group. Without it, the assignment has no group restrictions"
                                },
                                "maxGroupSize": {
                                    "type": "number",
                                    "description": "Maximum number of students per group. Only valid together with minGroupSize"
                                },
                                "visibility": {
                                    "type": "string",
                                    "enum": [
                                        "PUBLIC",
                                        "ONLY_BY_LINK",
                                        "PRIVATE"
                                    ],
                                    "description": "PUBLIC (listed to every student), ONLY_BY_LINK (the default) or PRIVATE (only the authorized submitters, which then must be filled in)"
                                },
                                "assignees": {
                                    "type": "string",
                                    "description": "Comma separated user ids of the students who are allowed to submit. Without it, any student can submit"
                                },
                                "acl": {
                                    "type": "string",
                                    "description": "Comma separated user ids of the other teachers who may change this assignment. The owner must not be included"
                                },
                                "tags": {
                                    "type": "string",
                                    "description": "Comma separated tags used to organize assignments, e.g. 'project,25/26'"
                                }
                            },
                            "required": [
                                "assignmentId",
                                "assignmentName",
                                "gitRepositoryUrl"
                            ]
                        }
                    },
                    {
                        "name": "connect_assignment",
                        "description": "Clone the git repository of an assignment into Drop Project and validate the teacher files it contains, returning the validation report. This only works after the public key returned by create_assignment was installed on the repository as a deploy key, and can safely be called again if it wasn't installed yet. Only the owner of the assignment and the teachers authorized on it can use this tool.",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "assignmentId": {
                                    "type": "string",
                                    "description": "The ID of the assignment to connect"
                                }
                            },
                            "required": [
                                "assignmentId"
                            ]
                        }
                    },
                    {
                        "name": "refresh_assignment",
                        "description": "Pull the git repository of an assignment so that Drop Project picks up the commits that were pushed to it, and validate the assignment files again, returning the new validation report. Use it after fixing whatever the previous report complained about. Only the owner of the assignment and the teachers authorized on it can use this tool.",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "assignmentId": {
                                    "type": "string",
                                    "description": "The ID of the assignment to refresh"
                                }
                            },
                            "required": [
                                "assignmentId"
                            ]
                        }
                    },
                    {
                        "name": "set_assignment_active",
                        "description": "Activate or deactivate an assignment, controlling whether students can submit to it. An assignment can only be activated after it was connected to its git repository and its validation report has no errors. Only the owner of the assignment and the teachers authorized on it can use this tool.",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "assignmentId": {
                                    "type": "string",
                                    "description": "The ID of the assignment to activate or deactivate"
                                },
                                "active": {
                                    "type": "boolean",
                                    "description": "true to let students submit to the assignment, false to stop them"
                                }
                            },
                            "required": [
                                "assignmentId",
                                "active"
                            ]
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
    fun `mcp get assignment info lists every setting needed to recreate the assignment`() {

        // a teacher who reuses an assignment in the next school year has to repeat all of its settings in the
        // create_assignment call, so get_assignment_info must report them, including the ones that are not set
        assignmentFixtures.createAndSetupAssignment("reusableAssignment", "Reusable Assignment",
            "org.dropProject.samples.sampleJavaAssignment", "UPLOAD", sampleJavaAssignmentRepo,
            assignees = "student1,student2", acl = "teacher2", tags = "project",
            dueDate = "2026-10-15T23:59", minGroupSize = "1", maxGroupSize = "3", visibility = "PRIVATE")

        try {
            val authHeader = getBearerToken("teacher1")
            val requestJson = """
                {
                    "jsonrpc": "2.0",
                    "id": "test-3",
                    "method": "tools/call",
                    "params": {
                        "name": "get_assignment_info",
                        "arguments": {
                            "assignmentId": "reusableAssignment"
                        }
                    }
                }
            """.trimIndent()

            val response = mvc.perform(
                post("/mcp/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", authHeader)
                    .content(requestJson)
            )
                .andExpect(status().isOk)
                .andReturn().response.contentAsString

            listOf(
                "**assignmentName:** Reusable Assignment",
                "**gitRepositoryUrl:** $sampleJavaAssignmentRepo",
                "**language:** JAVA",
                "**packageName:** org.dropProject.samples.sampleJavaAssignment",
                "**submissionMethod:** UPLOAD",
                "**submissionStructure:** COMPACT",
                "**dueDate:** 2026-10-15T23:59",
                "**acceptsStudentTests:** false",
                "**hiddenTestsVisibility:** SHOW_PROGRESS",
                "**minGroupSize:** 1",
                "**maxGroupSize:** 3",
                "**visibility:** PRIVATE",
                "**assignees:** student1,student2",
                "**acl:** teacher2",
                "**tags:** project",
                "**cooloffPeriod:** not set"  // settings the assignment doesn't use are reported as unset, not omitted
            ).forEach { assertThat(response, containsString(it)) }

        } finally {
            // cleanup assignment files
            File(dropProjectProperties.assignments.rootLocation, "reusableAssignment").deleteRecursively()
        }
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
    fun `try to get the submission code with a student token`() {
        // the token only carries the roles that its owner had when it was generated, so a student token must not be
        // able to reach the teacher-only tools
        val authHeader = getBearerToken("student1", role = "ROLE_STUDENT")

        val submissionId = submissionFixtures.uploadProject("projectOK", "testJavaProj", STUDENT_1)

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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"error\"")))
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("is not allowed to access submission code")))
            .andExpect(content().string(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("## Available Files"))))
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

    @Test
    fun `mcp search student returns the matching students and their submission history`() {
        val authHeader = getBearerToken("teacher1")

        // seed a student with one submission, without going through a real build
        val group = ProjectGroup()
        projectGroupRepository.save(group)
        val author = Author(name = "Gandalf Grey", userId = "gandalf")
        author.group = group
        authorRepository.save(author)
        val submission = Submission(submissionId = "1", submissionDate = Date(),
            status = SubmissionStatus.VALIDATED.code, statusDate = Date(), assignmentId = "testJavaProj",
            assignmentGitHash = null, submitterUserId = "gandalf")
        submission.group = group
        submissionRepository.save(submission)

        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "test-8",
                "method": "tools/call",
                "params": {
                    "name": "search_student",
                    "arguments": {
                        "query": "gand"
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":\"test-8\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Found 1 student(s) matching 'gand'")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("# Student: Gandalf Grey")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("**Student ID:** gandalf")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("## Submission History (1 assignment(s))")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("**Assignment ID:** testJavaProj")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("**Status:** VALIDATED")))
    }

    @Test
    fun `mcp search student without submissions reports the empty history`() {
        val authHeader = getBearerToken("teacher1")

        val group = ProjectGroup()
        projectGroupRepository.save(group)
        val author = Author(name = "Bilbo Baggins", userId = "bilbo")
        author.group = group
        authorRepository.save(author)

        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "test-9",
                "method": "tools/call",
                "params": {
                    "name": "search_student",
                    "arguments": {
                        "query": "bilbo"
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Found 1 student(s) matching 'bilbo'")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("No submissions found for this student.")))
    }

    @Test
    fun `mcp search student with no matches reports it`() {
        val authHeader = getBearerToken("teacher1")

        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "test-10",
                "method": "tools/call",
                "params": {
                    "name": "search_student",
                    "arguments": {
                        "query": "nobodyWithThisName"
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("No students found matching 'nobodyWithThisName'")))
    }

    @Test
    fun `mcp get submission info for a nonexistent submission reports it`() {
        val authHeader = getBearerToken("teacher1")

        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "test-11",
                "method": "tools/call",
                "params": {
                    "name": "get_submission_info",
                    "arguments": {
                        "submissionId": 99999
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":\"test-11\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Submission not found or inaccessible")))
    }

    /**
     * Calls a tool and returns the body of the response, so that the assertions can be made on it.
     */
    private fun callTool(toolName: String, arguments: String, authHeader: String): String {
        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "tool-call",
                "method": "tools/call",
                "params": {
                    "name": "$toolName",
                    "arguments": $arguments
                }
            }
        """.trimIndent()

        return mvc.perform(
            post("/mcp/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
    }

    @Test
    fun `mcp create assignment`() {
        val authHeader = getBearerToken("teacher1")

        val response = callTool("create_assignment", """
            {
                "assignmentId": "mcpCreatedAssignment",
                "assignmentName": "Assignment created through MCP",
                "gitRepositoryUrl": "git@github.com:drop-project-edu/sampleJavaAssignment.git",
                "packageName": "org.dropproject.samples",
                "language": "KOTLIN",
                "tags": "mcp,sample",
                "acl": "teacher2",
                "assignees": "student1,student2"
            }
        """.trimIndent(), authHeader)

        // the public key is what the teacher has to install on the repository, so it must come back
        assertThat(response, containsString("ssh-rsa "))
        assertThat(response, containsString("settings/keys"))
        assertThat(response, containsString("connect_assignment"))
        // the private key must never leave the server
        assertThat(response, not(containsString("PRIVATE KEY")))

        val assignment = assignmentRepository.findById("mcpCreatedAssignment").get()
        assertEquals("Assignment created through MCP", assignment.name)
        assertEquals("teacher1", assignment.ownerUserId)
        assertEquals(Language.KOTLIN, assignment.language)
        assertNotNull(assignment.gitRepositoryPubKey)
        assertNotNull(assignment.gitRepositoryPrivKey)

        // it was not connected to git yet, so it can't be usable
        assertFalse(assignment.active)
        assertNull(assignment.gitCurrentHash)

        assertEquals(listOf("student1", "student2"),
            assigneeRepository.findByAssignmentIdOrderByAuthorUserId("mcpCreatedAssignment").map { it.authorUserId })
        assertEquals(listOf("teacher2"),
            assignmentACLRepository.findByAssignmentId("mcpCreatedAssignment").map { it.userId })
    }

    @Test
    fun `try to create an assignment with an id that is already taken`() {
        val authHeader = getBearerToken("teacher1")

        val response = callTool("create_assignment", """
            {
                "assignmentId": "testMcpAssignment",
                "assignmentName": "Duplicated",
                "gitRepositoryUrl": "git@github.com:drop-project-edu/sampleJavaAssignment.git"
            }
        """.trimIndent(), authHeader)

        assertThat(response, containsString("An assignment already exists with this ID"))
        assertThat(response, containsString("\"isError\":true"))

        // the existing assignment was left alone
        assertEquals("Test MCP Assignment", assignmentRepository.findById("testMcpAssignment").get().name)
    }

    @Test
    fun `try to create an assignment with a non ssh git url`() {
        val authHeader = getBearerToken("teacher1")

        val response = callTool("create_assignment", """
            {
                "assignmentId": "httpsAssignment",
                "assignmentName": "Cloned over https",
                "gitRepositoryUrl": "https://github.com/drop-project-edu/sampleJavaAssignment.git"
            }
        """.trimIndent(), authHeader)

        assertThat(response, containsString("Only SSH style urls are accepted"))
        assertFalse(assignmentRepository.existsById("httpsAssignment"))
    }

    @Test
    fun `try to create an assignment with an invalid id`() {
        val authHeader = getBearerToken("teacher1")

        val response = callTool("create_assignment", """
            {
                "assignmentId": "not a valid id",
                "assignmentName": "Invalid",
                "gitRepositoryUrl": "git@github.com:drop-project-edu/sampleJavaAssignment.git"
            }
        """.trimIndent(), authHeader)

        assertThat(response, containsString("must only contain letters, numbers, hyphens and underscores"))
    }

    @Test
    fun `try to create an assignment with a student token`() {
        val authHeader = getBearerToken("student1", "ROLE_STUDENT")

        val response = callTool("create_assignment", """
            {
                "assignmentId": "studentCreatedAssignment",
                "assignmentName": "Created by a student",
                "gitRepositoryUrl": "git@github.com:drop-project-edu/sampleJavaAssignment.git"
            }
        """.trimIndent(), authHeader)

        assertThat(response, containsString("Only teachers can create assignments"))
        assertFalse(assignmentRepository.existsById("studentCreatedAssignment"))
    }

    @Test
    fun `connect assignment reports that the deploy key is missing`() {
        val authHeader = getBearerToken("teacher1")

        callTool("create_assignment", """
            {
                "assignmentId": "notConnectedYet",
                "assignmentName": "Not connected yet",
                "gitRepositoryUrl": "git@github.com:drop-project-edu/thisRepositoryDoesNotExist.git"
            }
        """.trimIndent(), authHeader)

        val response = callTool("connect_assignment", """{"assignmentId": "notConnectedYet"}""", authHeader)

        assertThat(response, containsString("\"isError\":true"))
        assertThat(response, containsString("Could not connect"))
        // the key is repeated, so that it can be installed without having to go back to create_assignment
        assertThat(response, containsString("ssh-rsa "))
        assertThat(response, containsString("connect_assignment again"))

        assertNull(assignmentRepository.findById("notConnectedYet").get().gitCurrentHash)
    }

    @Test
    fun `try to connect an assignment of another teacher`() {
        val authHeader = getBearerToken("teacher1")

        callTool("create_assignment", """
            {
                "assignmentId": "ownedByTeacher1",
                "assignmentName": "Owned by teacher1",
                "gitRepositoryUrl": "git@github.com:drop-project-edu/sampleJavaAssignment.git"
            }
        """.trimIndent(), authHeader)

        val response = callTool("connect_assignment", """{"assignmentId": "ownedByTeacher1"}""",
            getBearerToken("teacher2"))

        assertThat(response, containsString("can only be changed by its owner"))
    }

    @Test
    fun `try to activate an assignment that is not connected to git`() {
        val authHeader = getBearerToken("teacher1")

        callTool("create_assignment", """
            {
                "assignmentId": "neverConnected",
                "assignmentName": "Never connected",
                "gitRepositoryUrl": "git@github.com:drop-project-edu/sampleJavaAssignment.git"
            }
        """.trimIndent(), authHeader)

        val response = callTool("set_assignment_active",
            """{"assignmentId": "neverConnected", "active": true}""", authHeader)

        assertThat(response, containsString("\"isError\":true"))
        assertThat(response, containsString("not connected to its git repository yet"))
        assertFalse(assignmentRepository.findById("neverConnected").get().active)
    }

    @Test
    fun `deactivate an assignment`() {
        val authHeader = getBearerToken("teacher1")

        // testMcpAssignment is created active by the fixture
        val response = callTool("set_assignment_active",
            """{"assignmentId": "testMcpAssignment", "active": false}""", authHeader)

        assertThat(response, containsString("is now inactive"))
        assertFalse(assignmentRepository.findById("testMcpAssignment").get().active)
    }

    @Test
    fun `try to refresh an assignment that was never connected to git`() {
        val authHeader = getBearerToken("teacher1")

        val response = callTool("refresh_assignment", """{"assignmentId": "testMcpAssignment"}""", authHeader)

        assertThat(response, containsString("\"isError\":true"))
        assertThat(response, containsString("Could not refresh"))
    }
}
