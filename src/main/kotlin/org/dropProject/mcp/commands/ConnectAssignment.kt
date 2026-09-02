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
 * Command to connect an assignment to the git repository that defines it, by cloning it with the private key that
 * was generated when the assignment was created, and validating the files that come with it.
 *
 * This only succeeds once the matching public key was installed on the repository as a deploy key, so it is meant
 * to be called again after that was done.
 *
 * @property assignmentId The ID of the assignment to connect
 */
data class ConnectAssignment(val assignmentId: String) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {

        service.requireTeacher("connect assignments to their git repository")

        val assignment = service.getAssignmentToChange(assignmentId, principal)

        if (assignment.gitRepositoryPubKey == null) {
            // an assignment that was created before the key pair existed, or one that was imported
            service.assignmentService.generateGitConnectionKeyPair(assignment)
        }

        val result = service.assignmentService.connectAssignmentToGitRepository(assignment, principal)

        if (result.error != null) {
            val text = buildString {
                appendLine("# Could not connect '${assignment.id}' to ${assignment.gitRepositoryUrl}")
                appendLine()
                appendLine(result.error)
                appendLine()
                appendLine("The most common cause is the deploy key not being installed on the repository yet.")
                appendLine("This is the public key that has to be installed there, as a read-only deploy key:")
                appendLine()
                appendLine("```")
                appendLine(assignment.gitRepositoryPubKey?.trim().orEmpty())
                appendLine("```")
                appendLine()
                AssignmentTools.deployKeyPageUrl(assignment.gitRepositoryUrl)?.let {
                    appendLine("Install it with `gh repo deploy-key add <file with the key above> --title \"Drop Project\"`,")
                    appendLine("or by pasting it at $it")
                }
                appendLine()
                appendLine("Call connect_assignment again once that is done.")
            }

            return McpToolCallResult(content = listOf(McpContent(type = "text", text = text)), isError = true)
        }

        val reports = service.assignmentService.assignmentReportRepository.findByAssignmentId(assignment.id)

        val text = buildString {
            appendLine("# Assignment '${assignment.id}' is connected to ${assignment.gitRepositoryUrl}")
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
                name = "connect_assignment",
                description = "Clone the git repository of an assignment into Drop Project and validate the " +
                        "teacher files it contains, returning the validation report. This only works after the " +
                        "public key returned by create_assignment was installed on the repository as a deploy " +
                        "key, and can safely be called again if it wasn't installed yet. Only the owner of the " +
                        "assignment and the teachers authorized on it can use this tool.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "assignmentId" to mapOf(
                            "type" to "string",
                            "description" to "The ID of the assignment to connect"
                        )
                    ),
                    "required" to listOf("assignmentId")
                )
            )
        }

        /**
         * Factory method to create ConnectAssignment from arguments map.
         *
         * @param arguments Map containing the assignmentId
         * @return ConnectAssignment instance
         * @throws IllegalArgumentException if assignmentId is missing
         */
        fun from(arguments: Map<String, Any>): ConnectAssignment {
            val assignmentId = arguments["assignmentId"] as? String
                ?: throw IllegalArgumentException("assignmentId is required")
            return ConnectAssignment(assignmentId)
        }
    }
}
