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

import org.dropproject.dao.SubmissionStatus
import org.dropproject.extensions.realName
import org.dropproject.mcp.data.McpContent
import org.dropproject.mcp.data.McpTool
import org.dropproject.mcp.data.McpToolCallResult
import org.dropproject.mcp.services.McpService
import org.springframework.security.access.AccessDeniedException
import java.io.File
import java.security.Principal

/**
 * Command to retrieve the content of a specific file from a submission.
 *
 * @property submissionId The ID of the submission
 * @property path The relative path to the file within the submission
 */
data class GetFileContent(val submissionId: Long, val path: String) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {
        // Check that principal is a teacher
        if (!service.isTeacher()) {
            throw AccessDeniedException("${principal.realName()} is not allowed to access submission files")
        }

        // Get submission from repository
        val submission = service.submissionRepository.findById(submissionId).orElseThrow {
            IllegalArgumentException("Submission with ID $submissionId not found")
        }

        // Get the mavenized project folder
        val projectFolder = service.assignmentTeacherFiles.getProjectFolderAsFile(
            submission,
            wasRebuilt = submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT
        )

        // Resolve the file path
        val file = File(projectFolder, path)

        // Security check: ensure file is within project folder
        if (!file.canonicalPath.startsWith(projectFolder.canonicalPath)) {
            throw AccessDeniedException("Access denied to file outside submission folder")
        }

        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("File not found: $path")
        }

        // Read file content
        val content = try {
            file.readText()
        } catch (e: Exception) {
            throw IllegalArgumentException("Error reading file $path: ${e.message}")
        }

        val mimeType = getMimeType(file)

        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = content,
                    mimeType = mimeType
                )
            )
        )
    }

    /**
     * Get MIME type for a file based on extension.
     */
    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "java" -> "text/x-java"
            "kt", "kts" -> "text/x-kotlin"
            "xml" -> "text/xml"
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            else -> "text/plain"
        }
    }

    companion object {
        /**
         * Get the MCP tool metadata for this command.
         *
         * @return The McpTool metadata
         */
        fun toMcpTool(): McpTool {
            return McpTool(
                name = "get_file_content",
                description = "Retrieve the content of a specific source file from a student submission. " +
                        "Returns the file content as text with the appropriate MIME type. " +
                        "Use this after calling get_submission_code to retrieve individual file contents. " +
                        "Requires teacher privileges.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "submissionId" to mapOf(
                            "type" to "number",
                            "description" to "The numeric ID of the submission"
                        ),
                        "path" to mapOf(
                            "type" to "string",
                            "description" to "The relative path to the file within the submission (e.g., 'src/main/java/Main.java')"
                        )
                    ),
                    "required" to listOf("submissionId", "path")
                )
            )
        }

        /**
         * Factory method to create GetFileContent from arguments map.
         *
         * @param arguments Map containing the submissionId and path
         * @return GetFileContent instance
         * @throws IllegalArgumentException if parameters are missing or invalid
         */
        fun from(arguments: Map<String, Any>): GetFileContent {
            val submissionId = (arguments["submissionId"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("submissionId is required and must be a number")
            val path = arguments["path"] as? String
                ?: throw IllegalArgumentException("path is required and must be a string")
            return GetFileContent(submissionId, path)
        }
    }
}
