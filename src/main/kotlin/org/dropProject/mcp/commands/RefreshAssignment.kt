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
 * Command to pull the git repository of an assignment, so that Drop Project picks up the changes that were pushed
 * to it, and validate the assignment files again.
 *
 * This is what closes the loop while an assignment is being written: fix what the validation report complains
 * about, push, refresh, and read the report again.
 *
 * @property assignmentId The ID of the assignment to refresh
 */
data class RefreshAssignment(val assignmentId: String) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {

        service.requireTeacher("refresh assignments from their git repository")

        val assignment = service.getAssignmentToChange(assignmentId, principal)

        val result = service.assignmentService.refreshAssignmentFromGitRepository(assignment, principal)

        if (result.error != null) {
            return McpToolCallResult(
                content = listOf(McpContent(type = "text",
                    text = "# Could not refresh '${assignment.id}'\n\n${result.error}")),
                isError = true
            )
        }

        val reports = service.assignmentService.assignmentReportRepository.findByAssignmentId(assignment.id)

        val text = buildString {
            appendLine("# Assignment '${assignment.id}' was refreshed from ${assignment.gitRepositoryUrl}")
            appendLine()
            appendLine("Last commit: ${assignment.gitCurrentHash ?: "unknown"}")
            appendLine()
            appendLine(AssignmentTools.formatValidationReport(reports))
            appendLine()
            appendLine(AssignmentTools.nextStepAfterValidation(assignment, reports))
        }

        return McpToolCallResult(content = listOf(McpContent(type = "text", text = text)))
    }

    companion object {

        /**
         * Get the MCP tool metadata for this command.
         *
         * @return The McpTool metadata
         */
        fun toMcpTool(): McpTool {
            return McpTool(
                name = "refresh_assignment",
                description = "Pull the git repository of an assignment so that Drop Project picks up the commits " +
                        "that were pushed to it, and validate the assignment files again, returning the new " +
                        "validation report. Use it after fixing whatever the previous report complained about. " +
                        "Only the owner of the assignment and the teachers authorized on it can use this tool.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "assignmentId" to mapOf(
                            "type" to "string",
                            "description" to "The ID of the assignment to refresh"
                        )
                    ),
                    "required" to listOf("assignmentId")
                )
            )
        }

        /**
         * Factory method to create RefreshAssignment from arguments map.
         *
         * @param arguments Map containing the assignmentId
         * @return RefreshAssignment instance
         * @throws IllegalArgumentException if assignmentId is missing
         */
        fun from(arguments: Map<String, Any>): RefreshAssignment {
            val assignmentId = arguments["assignmentId"] as? String
                ?: throw IllegalArgumentException("assignmentId is required")
            return RefreshAssignment(assignmentId)
        }
    }
}
