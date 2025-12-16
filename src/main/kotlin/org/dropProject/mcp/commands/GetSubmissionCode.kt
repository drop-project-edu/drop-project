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
 * Command to retrieve the list of source code files from a submission.
 *
 * @property submissionId The ID of the submission to retrieve code from
 */
data class GetSubmissionCode(val submissionId: Long) : ToolCommand {

    data class FileInfo(val path: String, val isTeacherFile: Boolean)

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {
        // Check that principal is a teacher
        if (!service.isTeacher()) {
            throw AccessDeniedException("${principal.realName()} is not allowed to access submission code")
        }

        // Get submission from repository
        val submission = service.submissionRepository.findById(submissionId).orElseThrow {
            IllegalArgumentException("Submission with ID $submissionId not found")
        }

        // Get the mavenized project folder (same as downloadMavenProject)
        val projectFolder = service.assignmentTeacherFiles.getProjectFolderAsFile(
            submission,
            wasRebuilt = submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT
        )

        // Collect all source file paths
        val files = mutableListOf<FileInfo>()
        collectSourceFiles(projectFolder, projectFolder, files)

        // Create summary text
        val summaryText = buildString {
            appendLine("# Submission $submissionId - Source Files")
            appendLine()
            appendLine("**Assignment:** ${submission.assignmentId}")
            appendLine("**Submitted by:** ${submission.submitterUserId}")
            appendLine("**Date:** ${submission.submissionDate}")
            appendLine("**Status:** ${submission.getStatus()}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Available Files (${files.size} files)")
            appendLine()
            appendLine("The following source files are available in this submission.")
            appendLine("Use the `get_file_content` tool with the submission ID and file path to retrieve individual file contents.")
            appendLine()
            appendLine("Note: Files marked with [TEACHER] are teacher test files that were merged with the student submission for testing purposes - they are not part of the student's original submission.")
            appendLine()
            files.forEach { fileInfo ->
                val prefix = if (fileInfo.isTeacherFile) "[TEACHER] " else ""
                appendLine("- $prefix${fileInfo.path}")
            }
        }

        // Return summary
        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = summaryText
                )
            )
        )
    }

    /**
     * Recursively collect source file paths.
     */
    private fun collectSourceFiles(
        currentDir: File,
        projectRoot: File,
        files: MutableList<FileInfo>
    ) {
        val dirFiles = currentDir.listFiles()?.sortedBy { it.name } ?: return

        for (file in dirFiles) {
            when {
                file.isDirectory -> {
                    // Skip build directories and hidden directories
                    if (!file.name.startsWith(".") &&
                        file.name != "target" &&
                        file.name != "build" &&
                        file.name != "out") {
                        collectSourceFiles(file, projectRoot, files)
                    }
                }
                file.isFile && isSourceFile(file) -> {
                    val relativePath = file.relativeTo(projectRoot).path
                    val isTeacherFile = file.nameWithoutExtension.startsWith("TestTeacher")

                    files.add(FileInfo(relativePath, isTeacherFile))
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
                name = "get_submission_code",
                description = "Retrieve a list of source code files from a student submission. " +
                        "Returns a list of file paths that can be individually fetched using the get_file_content tool. " +
                        "This approach allows selective file retrieval and avoids large response payloads. " +
                        "Useful for code review, debugging, providing feedback, or AI-assisted analysis of student work. " +
                        "Requires teacher privileges.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "submissionId" to mapOf(
                            "type" to "number",
                            "description" to "The numeric ID of the submission to retrieve code from"
                        )
                    ),
                    "required" to listOf("submissionId")
                )
            )
        }

        /**
         * Factory method to create GetSubmissionCode from arguments map.
         *
         * @param arguments Map containing the submissionId
         * @return GetSubmissionCode instance
         * @throws IllegalArgumentException if submissionId is missing or invalid
         */
        fun from(arguments: Map<String, Any>): GetSubmissionCode {
            val submissionId = (arguments["submissionId"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("submissionId is required and must be a number")
            return GetSubmissionCode(submissionId)
        }
    }
}
