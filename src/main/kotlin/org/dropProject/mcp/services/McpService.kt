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

import jakarta.servlet.http.HttpServletRequest
import org.dropproject.controllers.TeacherAPIController
import org.dropproject.dao.SubmissionStatus
import org.dropproject.dao.TokenStatus
import org.dropproject.extensions.realName
import org.dropproject.mcp.commands.ToolCommand
import org.dropproject.mcp.data.*
import org.dropproject.repository.PersonalTokenRepository
import org.dropproject.repository.SubmissionRepository
import org.dropproject.services.AssignmentService
import org.dropproject.services.AssignmentTeacherFiles
import org.dropproject.services.ReportService
import org.dropproject.services.StudentService
import org.dropproject.storage.StorageService
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.security.Principal
import java.util.*

@Service
@Transactional(readOnly = true)
class McpService(
    val teacherAPIController: TeacherAPIController,
    val assignmentService: AssignmentService,
    val studentService: StudentService,
    val submissionRepository: SubmissionRepository,
    val assignmentTeacherFiles: AssignmentTeacherFiles,
    val reportService: ReportService,
    val request: HttpServletRequest,
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

    /**
     * List all available MCP tools.
     * Tool metadata is retrieved from each ToolCommand implementation.
     *
     * @return McpToolsListResult containing all available tools
     */
    fun listTools(): McpToolsListResult {
        return McpToolsListResult(
            tools = ToolCommand.getAllTools()
        )
    }

    /**
     * Execute a tool command by delegating to the appropriate ToolCommand implementation.
     *
     * @param toolName The name of the tool to execute
     * @param arguments The arguments for the tool
     * @param principal The authenticated principal making the request
     * @return The tool execution result
     */
    fun callTool(toolName: String, arguments: Map<String, Any>, principal: Principal): McpToolCallResult {
        return ToolCommand.from(toolName, arguments).handle(this, principal)
    }

    /**
     * Check if the current user has the TEACHER role.
     *
     * @return true if the user is a teacher, false otherwise
     */
    fun isTeacher(): Boolean {
        return request.isUserInRole("TEACHER")
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
