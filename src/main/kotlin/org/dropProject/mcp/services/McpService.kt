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
                tools = McpToolsCapability(listChanged = false),
                resources = mapOf("listChanged" to false)
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

    /**
     * List all available submission file resources for a given submission.
     * Only teachers can list submission resources.
     *
     * @param submissionId The ID of the submission (optional, for filtering)
     * @param principal The authenticated principal making the request
     * @return McpResourcesListResult containing available resources
     */
    fun listResources(submissionId: Long?, principal: Principal): McpResourcesListResult {
        // Check that principal is a teacher
        if (!isTeacher()) {
            throw AccessDeniedException("${principal.realName()} is not allowed to list submission resources")
        }

        val resources = mutableListOf<McpResource>()

        if (submissionId != null) {
            // List resources for a specific submission
            val submission = submissionRepository.findById(submissionId).orElseThrow {
                IllegalArgumentException("Submission with ID $submissionId not found")
            }

            val projectFolder = assignmentTeacherFiles.getProjectFolderAsFile(
                submission,
                wasRebuilt = submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT
            )

            collectResourcesFromFolder(projectFolder, projectFolder, submissionId, resources)
        }

        return McpResourcesListResult(resources = resources)
    }

    /**
     * Read the content of a specific resource identified by URI.
     * Only teachers can read submission file resources.
     *
     * @param uri The resource URI to read
     * @param principal The authenticated principal making the request
     * @return McpResourcesReadResult containing the resource content
     */
    fun readResource(uri: String, principal: Principal): McpResourcesReadResult {
        // Check that principal is a teacher
        if (!isTeacher()) {
            throw AccessDeniedException("${principal.realName()} is not allowed to read submission resources")
        }

        // Parse URI: dropproject://submission/{submissionId}/file/{relativePath}
        val uriRegex = Regex("^dropproject://submission/(\\d+)/file/(.+)$")
        val matchResult = uriRegex.matchEntire(uri)
            ?: throw IllegalArgumentException("Invalid resource URI: $uri")

        val submissionId = matchResult.groupValues[1].toLong()
        val relativePath = matchResult.groupValues[2]

        // Get submission from repository
        val submission = submissionRepository.findById(submissionId).orElseThrow {
            IllegalArgumentException("Submission with ID $submissionId not found")
        }

        // Get the mavenized project folder
        val projectFolder = assignmentTeacherFiles.getProjectFolderAsFile(
            submission,
            wasRebuilt = submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT
        )

        // Resolve the file path
        val file = File(projectFolder, relativePath)

        // Security check: ensure file is within project folder
        if (!file.canonicalPath.startsWith(projectFolder.canonicalPath)) {
            throw AccessDeniedException("Access denied to file outside submission folder")
        }

        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("File not found: $relativePath")
        }

        // Read file content
        val content = try {
            file.readText()
        } catch (e: Exception) {
            throw IllegalArgumentException("Error reading file $relativePath: ${e.message}")
        }

        val mimeType = getMimeTypeForFile(file)

        return McpResourcesReadResult(
            contents = listOf(
                McpResourceContent(
                    uri = uri,
                    mimeType = mimeType,
                    text = content
                )
            )
        )
    }

    /**
     * Recursively collect resources from a folder.
     */
    private fun collectResourcesFromFolder(
        currentDir: File,
        projectRoot: File,
        submissionId: Long,
        resources: MutableList<McpResource>
    ) {
        val files = currentDir.listFiles()?.sortedBy { it.name } ?: return

        for (file in files) {
            when {
                file.isDirectory -> {
                    // Skip build directories and hidden directories
                    if (!file.name.startsWith(".") &&
                        file.name != "target" &&
                        file.name != "build" &&
                        file.name != "out") {
                        collectResourcesFromFolder(file, projectRoot, submissionId, resources)
                    }
                }
                file.isFile && isSourceFile(file) -> {
                    val relativePath = file.relativeTo(projectRoot).path
                    val isTeacherFile = file.nameWithoutExtension.startsWith("TestTeacher")

                    val description = if (isTeacherFile) {
                        "Teacher file - not part of student submission, merged for testing"
                    } else {
                        "Student source file"
                    }

                    resources.add(
                        McpResource(
                            uri = "dropproject://submission/$submissionId/file/$relativePath",
                            name = relativePath,
                            description = description,
                            mimeType = getMimeTypeForFile(file)
                        )
                    )
                }
            }
        }
    }

    /**
     * Check if a file is a source code file based on extension.
     */
    private fun isSourceFile(file: File): Boolean {
        val sourceExtensions = setOf("java", "kt", "kts", "xml", "md", "txt")
        return file.extension.lowercase() in sourceExtensions
    }

    /**
     * Get MIME type for a file based on extension.
     */
    private fun getMimeTypeForFile(file: File): String {
        return when (file.extension.lowercase()) {
            "java" -> "text/x-java"
            "kt", "kts" -> "text/x-kotlin"
            "xml" -> "text/xml"
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            else -> "text/plain"
        }
    }
}
