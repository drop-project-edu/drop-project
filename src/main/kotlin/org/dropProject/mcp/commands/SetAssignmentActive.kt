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
 * Command to activate or deactivate an assignment, i.e. to control whether students can submit to it.
 *
 * An assignment whose validation report has errors can't be activated, mirroring what the web interface does,
 * since Drop Project wouldn't be able to evaluate the submissions it would receive.
 *
 * @property assignmentId The ID of the assignment to activate or deactivate
 * @property active Whether the assignment should become active
 */
data class SetAssignmentActive(val assignmentId: String, val active: Boolean) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {

        service.requireTeacher("activate assignments")

        val assignment = service.getAssignmentToChange(assignmentId, principal)

        val reports = service.assignmentService.assignmentReportRepository.findByAssignmentId(assignment.id)

        if (active && AssignmentTools.hasErrors(reports)) {
            val text = buildString {
                appendLine("# Assignment '${assignment.id}' can't be activated")
                appendLine()
                appendLine("Its validation report still has errors:")
                appendLine()
                appendLine(AssignmentTools.formatValidationReport(reports))
                appendLine()
                appendLine(AssignmentTools.nextStepAfterValidation(assignment, reports))
            }

            return McpToolCallResult(content = listOf(McpContent(type = "text", text = text)), isError = true)
        }

        if (active && assignment.gitCurrentHash == null) {
            return McpToolCallResult(
                content = listOf(McpContent(type = "text",
                    text = "# Assignment '${assignment.id}' can't be activated\n\n" +
                            "It is not connected to its git repository yet. Call connect_assignment first.")),
                isError = true
            )
        }

        assignment.active = active
        service.assignmentRepository.save(assignment)

        val text = if (active) {
            "Assignment '${assignment.id}' is now active. Students can submit to it."
        } else {
            "Assignment '${assignment.id}' is now inactive. Students can no longer submit to it."
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
                name = "set_assignment_active",
                description = "Activate or deactivate an assignment, controlling whether students can submit to " +
                        "it. An assignment can only be activated after it was connected to its git repository " +
                        "and its validation report has no errors. Only the owner of the assignment and the " +
                        "teachers authorized on it can use this tool.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "assignmentId" to mapOf(
                            "type" to "string",
                            "description" to "The ID of the assignment to activate or deactivate"
                        ),
                        "active" to mapOf(
                            "type" to "boolean",
                            "description" to "true to let students submit to the assignment, false to stop them"
                        )
                    ),
                    "required" to listOf("assignmentId", "active")
                )
            )
        }

        /**
         * Factory method to create SetAssignmentActive from arguments map.
         *
         * @param arguments Map containing the assignmentId and the intended active state
         * @return SetAssignmentActive instance
         * @throws IllegalArgumentException if an argument is missing or invalid
         */
        fun from(arguments: Map<String, Any>): SetAssignmentActive {
            val assignmentId = arguments["assignmentId"] as? String
                ?: throw IllegalArgumentException("assignmentId is required")
            val active = arguments["active"] as? Boolean
                ?: throw IllegalArgumentException("active is required and must be a boolean")
            return SetAssignmentActive(assignmentId, active)
        }
    }
}
