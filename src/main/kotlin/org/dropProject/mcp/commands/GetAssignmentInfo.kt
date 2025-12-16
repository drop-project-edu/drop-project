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
 * Command to retrieve comprehensive information about a programming assignment.
 *
 * @property assignmentId The ID of the assignment to retrieve
 */
data class GetAssignmentInfo(val assignmentId: String) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {
        // Use AssignmentService to get detailed assignment information
        val assignmentDetail = service.assignmentService.getAssignmentDetailData(
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

        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = assignmentText
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
            )
        }

        /**
         * Factory method to create GetAssignmentInfo from arguments map.
         *
         * @param arguments Map containing the assignmentId
         * @return GetAssignmentInfo instance
         * @throws IllegalArgumentException if assignmentId is missing
         */
        fun from(arguments: Map<String, Any>): GetAssignmentInfo {
            val assignmentId = arguments["assignmentId"] as? String
                ?: throw IllegalArgumentException("assignmentId is required")
            return GetAssignmentInfo(assignmentId)
        }
    }
}
