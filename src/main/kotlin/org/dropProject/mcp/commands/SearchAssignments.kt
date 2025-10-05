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
 * Command to search for assignments by name, ID, or programming language tags.
 *
 * @property query Search query to match assignment names, IDs, or tags
 */
data class SearchAssignments(val query: String) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {
        val response = service.teacherAPIController.searchAssignments(query, principal)
        val assignments = response.body ?: throw RuntimeException("Failed to search assignments")

        val assignmentList = assignments.joinToString("\n") { "- ${it.text} (ID: ${it.value})" }

        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = "Found ${assignments.size} assignments matching '$query':\n$assignmentList"
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
            )
        }

        /**
         * Factory method to create SearchAssignments from arguments map.
         *
         * @param arguments Map containing the query
         * @return SearchAssignments instance
         * @throws IllegalArgumentException if query is missing
         */
        fun from(arguments: Map<String, Any>): SearchAssignments {
            val query = arguments["query"] as? String
                ?: throw IllegalArgumentException("query is required")
            return SearchAssignments(query)
        }
    }
}
