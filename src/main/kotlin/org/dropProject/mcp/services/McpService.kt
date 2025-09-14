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
package org.dropproject.mcp.services

import org.dropproject.controllers.TeacherAPIController
import org.dropproject.dao.TokenStatus
import org.dropproject.mcp.data.*
import org.dropproject.repository.PersonalTokenRepository
import org.dropproject.services.AssignmentService
import org.dropproject.services.StudentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.Principal
import java.util.*

@Service
@Transactional(readOnly = true)
class McpService(
    private val teacherAPIController: TeacherAPIController,
    private val assignmentService: AssignmentService,
    private val studentService: StudentService,
    private val personalTokenRepository: PersonalTokenRepository
) {

    fun getInitializeResult(): McpInitializeResult {
        return McpInitializeResult(
            protocolVersion = "2024-11-05",
            capabilities = McpServerCapabilities(
                tools = McpToolsCapability(listChanged = false)
            ),
            serverInfo = McpServerInfo(
                name = "DropProject",
                version = "1.0.0"
            )
        )
    }

    fun listTools(): McpToolsListResult {
        return McpToolsListResult(
            tools = listOf(
                McpTool(
                    name = "get_assignment_info",
                    description = "Get comprehensive information about a programming assignment in Drop Project, " +
                                 "including instructions, requirements, due dates, submission methods, and grading criteria. " +
                                 "Useful when a student or teacher needs detailed assignment context.",
                    inputSchema = mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "assignmentId" to mapOf(
                                "type" to "string",
                                "description" to "The ID of the assignment to retrieve"
                            )
                        ),
                        "required" to listOf("assignmentId")
                    )
                ),
                McpTool(
                    name = "search_assignments",
                    description = "Search Drop Project assignments by name, ID, or programming language tags. " +
                                 "Returns matching assignments with basic metadata. " +
                                 "Useful for finding relevant assignments or exploring available coursework.",
                    inputSchema = mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf(
                                "type" to "string",
                                "description" to "Search query to match assignment names, IDs, or tags"
                            )
                        ),
                        "required" to listOf("query")
                    )
                ),
                McpTool(
                    name = "search_student",
                    description = "Search for students by student ID or name (partial matching) and retrieve their complete submission history. " +
                                 "Returns student information along with assignment IDs and submission IDs for detailed lookup. " +
                                 "Useful for tracking student progress, identifying submission patterns, or providing academic support.",
                    inputSchema = mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf(
                                "type" to "string",
                                "description" to "Search query to match student IDs or names (case-insensitive partial matching)"
                            )
                        ),
                        "required" to listOf("query")
                    )
                )
            )
        )
    }

    fun callTool(toolName: String, arguments: Map<String, Any>, principal: Principal): McpToolCallResult {
        return when (toolName) {
            "get_assignment_info" -> {
                val assignmentId = arguments["assignmentId"] as? String
                    ?: throw IllegalArgumentException("assignmentId is required")
                
                // Use AssignmentService to get detailed assignment information
                val assignmentDetail = assignmentService.getAssignmentDetailData(
                    assignmentId, principal, isAdmin = true // MCP users are treated as admins
                )
                
                // Format assignment detail for MCP response
                val assignmentText = buildString {
                    appendLine("# Assignment: ${assignmentDetail.assignment.name}")
                    appendLine("**ID:** ${assignmentDetail.assignment.id}")
                    appendLine("**Package:** ${assignmentDetail.assignment.packageName}")
                    appendLine("**Owner:** ${assignmentDetail.assignment.ownerUserId}")
                    appendLine("**Submission Method:** ${assignmentDetail.assignment.submissionMethod}")
                    appendLine("**Active:** ${assignmentDetail.assignment.active}")
                    
                    if (assignmentDetail.assignment.instructions != null) {
                        appendLine("\n## Instructions")
                        appendLine(assignmentDetail.assignment.instructions.toString())
                    }
                    
                    if (assignmentDetail.assignment.dueDate != null) {
                        appendLine("\n**Due Date:** ${assignmentDetail.assignment.dueDate}")
                    }
                    
                    appendLine("**Language:** ${assignmentDetail.assignment.language}")

                    if (assignmentDetail.assignees.isNotEmpty()) {
                        appendLine("\n## Assignees (${assignmentDetail.assignees.size})")
                        assignmentDetail.assignees.forEach { assignee ->
                            appendLine("- ${assignee.authorUserId}")
                        }
                    }
                    
                    if (assignmentDetail.tests.isNotEmpty()) {
                        appendLine("\n## Tests (${assignmentDetail.tests.size})")
                        assignmentDetail.tests.forEach { test ->
                            appendLine("- ${test.testMethod}")
                        }
                    }
                    
                    if (assignmentDetail.reports.isNotEmpty()) {
                        appendLine("\n## Validation Report")
                        appendLine(assignmentDetail.reportMessage)
                        assignmentDetail.reports.forEach { report ->
                            appendLine("- ${report.type}: ${report.message}")
                        }
                    }
                    
                    if (assignmentDetail.lastCommitInfo != null) {
                        appendLine("\n## Git Information")
                        appendLine("**Last Commit:** ${assignmentDetail.lastCommitInfo}")
                        if (assignmentDetail.sshKeyFingerprint != null) {
                            appendLine("**SSH Key Fingerprint:** ${assignmentDetail.sshKeyFingerprint}")
                        }
                    }
                }
                
                McpToolCallResult(
                    content = listOf(
                        McpContent(
                            type = "text",
                            text = assignmentText
                        )
                    )
                )
            }
            "search_assignments" -> {
                val query = arguments["query"] as? String
                    ?: throw IllegalArgumentException("query is required")
                
                val response = teacherAPIController.searchAssignments(query, principal)
                val assignments = response.body ?: throw RuntimeException("Failed to search assignments")
                
                val assignmentList = assignments.joinToString("\n") { "- ${it.text} (ID: ${it.value})" }
                
                McpToolCallResult(
                    content = listOf(
                        McpContent(
                            type = "text",
                            text = "Found ${assignments.size} assignments matching '$query':\n$assignmentList"
                        )
                    )
                )
            }
            "search_student" -> {
                val query = arguments["query"] as? String
                    ?: throw IllegalArgumentException("query is required")
                
                val matchingStudents = studentService.getStudentList(query)
                
                if (matchingStudents.isEmpty()) {
                    return McpToolCallResult(
                        content = listOf(
                            McpContent(
                                type = "text",
                                text = "No students found matching '$query'"
                            )
                        )
                    )
                }
                
                val studentInfoText = buildString {
                    appendLine("Found ${matchingStudents.size} student(s) matching '$query':")
                    appendLine()
                    
                    for (student in matchingStudents) {
                        appendLine("# Student: ${student.text}")
                        appendLine("**Student ID:** ${student.value}")
                        appendLine()
                        
                        val studentHistory = studentService.getStudentHistory(student.value, principal)
                        
                        if (studentHistory == null) {
                            appendLine("No submission history found for this student.")
                            appendLine()
                            continue
                        }
                        
                        val sortedHistory = studentHistory.getHistorySortedByDateDesc()
                        
                        if (sortedHistory.isEmpty()) {
                            appendLine("No submissions found for this student.")
                            appendLine()
                            continue
                        }
                        
                        appendLine("## Submission History (${sortedHistory.size} assignment(s))")
                        appendLine()
                        
                        for ((index, entry) in sortedHistory.withIndex()) {
                            appendLine("### Assignment ${index + 1}: ${entry.assignment.name}")
                            appendLine("**Assignment ID:** ${entry.assignment.id}")
                            appendLine("**Language:** ${entry.assignment.language}")
                            appendLine("**Due Date:** ${entry.assignment.dueDate ?: "Not set"}")
                            appendLine("**Group:** ${entry.group.id} (${entry.group.authorsStr()})")
                            appendLine()
                            
                            entry.ensureSubmissionsAreSorted()
                            val submissions = entry.sortedSubmissions
                            
                            appendLine("**Submissions (${submissions.size}):**")
                            for ((submissionIndex, submission) in submissions.withIndex()) {
                                appendLine("${submissionIndex + 1}. **Submission ID:** ${submission.id}")
                                appendLine("   - **Date:** ${submission.submissionDate}")
                                appendLine("   - **Status:** ${submission.getStatus()}")
                                appendLine("   - **Submitter:** ${submission.submitterName ?: submission.submitterUserId}")
                                if (submission.buildReport != null) {
                                    appendLine("   - **Has Build Report:** Yes")
                                }
                                appendLine()
                            }
                            appendLine("---")
                            appendLine()
                        }
                    }
                }
                
                McpToolCallResult(
                    content = listOf(
                        McpContent(
                            type = "text",
                            text = studentInfoText
                        )
                    )
                )
            }
            else -> throw IllegalArgumentException("Unknown tool: $toolName")
        }
    }

    /**
     * Validates a personal token used as Bearer token and returns the associated user ID.
     */
    fun validateBearerToken(token: String): String? {
        return try {
            val tokenEntity = personalTokenRepository.getByPersonalToken(token)
            
            if (tokenEntity != null && 
                tokenEntity.status == TokenStatus.ACTIVE && 
                tokenEntity.expirationDate.after(Date())) {
                tokenEntity.userId
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
