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
package org.dropproject.mcp.commands

import org.dropproject.mcp.data.McpContent
import org.dropproject.mcp.data.McpTool
import org.dropproject.mcp.data.McpToolCallResult
import org.dropproject.mcp.services.McpService
import java.security.Principal

/**
 * Command to search for students and retrieve their submission history.
 *
 * @property query Search query to match student IDs or names (case-insensitive partial matching)
 */
data class SearchStudent(val query: String) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {
        val matchingStudents = service.studentService.getStudentList(query)

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

                val studentHistory = service.studentService.getStudentHistory(student.value, principal)

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

        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = studentInfoText
                )
            )
        )
    }

    companion object {
        /**
         * Get the MCP tool metadata for this command.
         *
         * @return The McpTool metadata
         */
        fun toMcpTool(): McpTool {
            return McpTool(
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
        }

        /**
         * Factory method to create SearchStudent from arguments map.
         *
         * @param arguments Map containing the query
         * @return SearchStudent instance
         * @throws IllegalArgumentException if query is missing
         */
        fun from(arguments: Map<String, Any>): SearchStudent {
            val query = arguments["query"] as? String
                ?: throw IllegalArgumentException("query is required")
            return SearchStudent(query)
        }
    }
}
