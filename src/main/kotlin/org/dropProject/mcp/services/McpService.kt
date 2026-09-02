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
import org.dropproject.dao.PersonalToken
import org.dropproject.dao.SubmissionStatus
import org.dropproject.dao.TokenStatus
import org.dropproject.extensions.realName
import org.dropproject.mcp.commands.ToolCommand
import org.dropproject.mcp.data.*
import org.dropproject.dao.Assignment
import org.dropproject.repository.AssignmentRepository
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
    val assignmentRepository: AssignmentRepository,
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
            ),
            instructions = SERVER_INSTRUCTIONS
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
    @Transactional  // overrides the read-only transaction of the class, since some of the tools write
    fun callTool(toolName: String, arguments: Map<String, Any>, principal: Principal): McpToolCallResult {
        return ToolCommand.from(toolName, arguments).handle(this, principal)
    }

    /**
     * Check that the current user has the TEACHER role, throwing if they don't.
     *
     * @param action describes what the user was trying to do, to be included in the error message
     */
    fun requireTeacher(action: String) {
        if (!isTeacher()) {
            throw AccessDeniedException("Only teachers can $action")
        }
    }

    /**
     * Returns the [Assignment] with the given id, provided that the current user is allowed to change it, i.e.
     * is a teacher and either owns it or was given access to it.
     *
     * @param assignmentId identifies the assignment
     * @param principal is the authenticated principal making the request
     * @throws AccessDeniedException if the user is not allowed to change the assignment
     */
    fun getAssignmentToChange(assignmentId: String, principal: Principal): Assignment {
        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { IllegalArgumentException("Assignment $assignmentId not found") }

        if (!assignmentService.isAuthorizedTeacher(assignment, principal.realName(), request)) {
            throw AccessDeniedException("Assignment $assignmentId can only be changed by its owner " +
                    "(${assignment.ownerUserId}) or by the teachers that were given access to it")
        }

        return assignment
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
     * Validates a personal token used as Bearer token, returning it so that the caller can use both the user id and
     * the roles that it carries. Returns null if the token doesn't exist, was revoked or has expired.
     */
    fun validateBearerToken(token: String): PersonalToken? {
        return try {
            val tokenEntity = personalTokenRepository.getByPersonalToken(token)

            if (tokenEntity != null &&
                tokenEntity.status == TokenStatus.ACTIVE &&
                tokenEntity.expirationDate.after(Date())) {
                tokenEntity
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {

        /**
         * Given to the client at initialization, to be used in the same way as a system prompt. It only describes
         * what can't be inferred from the tools themselves, i.e. that an assignment is defined outside of Drop
         * Project and the order in which the steps that create one have to happen.
         */
        val SERVER_INSTRUCTIONS = """
            Drop Project is a platform where teachers create programming assignments and students submit their
            code to be automatically compiled, tested and graded.

            An assignment is not defined inside Drop Project: it is defined by a git repository owned by the
            teacher, containing a Maven project with the teacher's unit tests and the instructions shown to the
            students. Drop Project keeps a read-only clone of that repository. Creating an assignment is therefore
            a sequence of steps, and only some of them happen here:

            1. Write the assignment's Maven project and push it to a git repository. Drop Project never creates or
               writes to repositories, so this has to be done with git and the git host's own tooling.
            2. create_assignment - registers the assignment and returns an ssh public key.
            3. Install that public key on the repository as a read-only deploy key. Drop Project doesn't do this
               either, for the same reason.
            4. connect_assignment - clones the repository and validates the files that came with it.
            5. Fix whatever the validation report complains about, push, and call refresh_assignment. Repeat until
               the report has no errors.
            6. set_assignment_active - students can only submit after this.

            Only SSH git urls (git@...) are accepted, and an assignment can't be activated while its validation
            report has errors.
        """.trimIndent()
    }
}
