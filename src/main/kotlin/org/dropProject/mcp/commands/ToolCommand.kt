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

import org.dropproject.mcp.data.McpTool
import org.dropproject.mcp.data.McpToolCallResult
import org.dropproject.mcp.services.McpService
import java.security.Principal

/**
 * Sealed interface representing MCP tool commands.
 * Each implementation handles a specific tool execution.
 */
sealed interface ToolCommand {
    /**
     * Execute the command and return the result.
     *
     * @param service The McpService instance providing access to business logic
     * @param principal The authenticated principal making the request
     * @return The tool execution result
     */
    fun handle(service: McpService, principal: Principal): McpToolCallResult

    companion object {
        private val commandFactories: Map<String, (Map<String, Any>) -> ToolCommand> = mapOf(
            "get_assignment_info" to GetAssignmentInfo::from,
            "search_assignments" to SearchAssignments::from,
            "search_student" to SearchStudent::from,
            "get_submission_code" to GetSubmissionCode::from,
            "get_submission_info" to GetSubmissionInfo::from
        )

        /**
         * Factory method to create a ToolCommand from tool name and arguments.
         *
         * @param toolName The name of the tool to execute
         * @param arguments The arguments for the tool
         * @return The corresponding ToolCommand instance
         * @throws IllegalArgumentException if the tool name is unknown
         */
        fun from(toolName: String, arguments: Map<String, Any>): ToolCommand {
            return commandFactories[toolName]?.invoke(arguments)
                ?: throw IllegalArgumentException("Unknown tool: $toolName")
        }

        /**
         * Get all available tools as MCP tool metadata.
         * Uses companion object methods to avoid instantiating commands.
         *
         * @return List of McpTool metadata for all available commands
         */
        fun getAllTools(): List<McpTool> {
            return listOf(
                GetAssignmentInfo.toMcpTool(),
                SearchAssignments.toMcpTool(),
                SearchStudent.toMcpTool(),
                GetSubmissionCode.toMcpTool(),
                GetSubmissionInfo.toMcpTool()
            )
        }
    }
}
