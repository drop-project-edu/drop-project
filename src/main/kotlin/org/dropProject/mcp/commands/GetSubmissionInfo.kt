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
import org.dropproject.extensions.formatDefault
import org.dropproject.extensions.realName
import org.dropproject.mcp.data.McpContent
import org.dropproject.mcp.data.McpTool
import org.dropproject.mcp.data.McpToolCallResult
import org.dropproject.mcp.services.McpService
import org.springframework.security.access.AccessDeniedException
import java.security.Principal

/**
 * Command to retrieve detailed information about a submission (similar to the build report).
 *
 * @property submissionId The ID of the submission to retrieve information from
 */
data class GetSubmissionInfo(val submissionId: Long) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {
        // Build the full report using ReportService
        val fullBuildReport = service.reportService.buildReport(
            submissionId,
            principal,
            service.request
        )

        // Format the report as markdown text
        val reportText = buildString {
            val submission = fullBuildReport.submission
            val assignment = fullBuildReport.assignment
            val buildReport = fullBuildReport.buildReport

            if (submission == null || assignment == null) {
                append("Submission not found or inaccessible")
                return@buildString
            }

            // Header information
            appendLine("# Build Report for Submission ${submission.id}")
            appendLine()
            appendLine("**Assignment:** ${assignment.id}")
            appendLine("**Submitted:** ${submission.submissionDate.formatDefault()}")
            appendLine("**Total Submissions:** ${fullBuildReport.numSubmissions ?: "N/A"}")
            appendLine("**Status:** ${submission.getStatus()}")

            if (submission.overdue == true) {
                appendLine("**⚠️ Overdue**")
            }
            if (submission.markedAsFinal) {
                appendLine("**✓ Marked as Final**")
            }
            appendLine()

            // Git information if available
            fullBuildReport.gitSubmission?.let { gitSubmission ->
                appendLine("## Git Information")
                appendLine("- **Repository:** ${fullBuildReport.gitRepository ?: "N/A"}")
                appendLine("- **Last Commit:** ${gitSubmission.lastCommitDate?.formatDefault()}")
                fullBuildReport.gitRepositoryWithHash?.let { repoWithHash ->
                    appendLine("- **Commit Link:** $repoWithHash")
                }
                appendLine()
            }

            // Group members
            fullBuildReport.authors?.let { authors ->
                appendLine("## Group Members")
                authors.forEach { author ->
                    val submitterMark = if (author.submitter) " (Submitter)" else ""
                    appendLine("- ${author.number} - ${author.name}$submitterMark")
                }
                appendLine()
            }

            // Error message if exists
            fullBuildReport.error?.let { error ->
                appendLine("## Status")
                appendLine(error)
                appendLine()
            }

            // Results summary
            fullBuildReport.summary?.let { summary ->
                appendLine("## Results Summary")
                val isTeacher = service.isTeacher()
                summary.forEach { summaryResult ->
                    val status = when (summaryResult.reportValue) {
                        "OK" -> "✓"
                        "NOK" -> "✗"
                        "Not Enough Tests" -> "?"
                        else -> summaryResult.reportValue
                    }
                    appendLine("- **${summaryResult.indicator.description}:** $status")
                    summaryResult.progressSummary(isTeacher)?.let { progressSummary ->
                        appendLine("  - Progress: $progressSummary")
                    }
                }
                appendLine()
            }

            // Structure errors
            fullBuildReport.structureErrors?.let { errors ->
                if (errors.isNotEmpty()) {
                    appendLine("## Project Structure Errors")
                    errors.forEach { error ->
                        appendLine("- $error")
                    }
                    appendLine()
                }
            }

            // Compilation errors
            buildReport?.compilationErrors?.let { errors ->
                if (errors.isNotEmpty()) {
                    appendLine("## Compilation Errors")
                    errors.forEach { error ->
                        appendLine("```")
                        appendLine(error)
                        appendLine("```")
                    }
                    appendLine()
                }
            }

            // Checkstyle errors
            buildReport?.checkstyleErrors?.let { errors ->
                if (errors.isNotEmpty()) {
                    appendLine("## Code Quality (Checkstyle) Errors")
                    errors.forEach { error ->
                        appendLine("- $error")
                    }
                    appendLine()
                }
            }

            // JUnit Summary (Student Tests)
            if (assignment.acceptsStudentTests && buildReport != null) {
                appendLine("## JUnit Summary (Student Tests)")

                buildReport.jacocoResults.firstOrNull()?.let { jacoco ->
                    appendLine("**Coverage:** ${jacoco.lineCoveragePercent}%")
                }

                buildReport.junitSummaryStudent?.let { summary ->
                    appendLine(summary)
                } ?: run {
                    appendLine(buildReport.notEnoughStudentTestsMessage())
                }

                buildReport.junitErrorsStudent?.let { errors ->
                    appendLine()
                    appendLine("**Errors:**")
                    appendLine("```")
                    appendLine(errors)
                    appendLine("```")
                }
                appendLine()
            }

            // JUnit Summary (Teacher Tests)
            buildReport?.junitSummaryTeacher?.let { summary ->
                appendLine("## JUnit Summary (Teacher Tests)")
                appendLine(summary)
                buildReport.junitSummaryTeacherExtraDescription?.let { extraDesc ->
                    appendLine()
                    appendLine("**Note:** $extraDesc")
                }

                buildReport.junitErrorsTeacher?.let { errors ->
                    appendLine()
                    appendLine("**Errors:**")
                    appendLine("```")
                    appendLine(errors)
                    appendLine("```")
                }
                appendLine()
            }

            // JUnit Summary (Hidden Tests) - only for teachers
            if (service.isTeacher()) {
                buildReport?.junitSummaryHidden?.let { summary ->
                    appendLine("## JUnit Summary (Hidden Tests)")
                    appendLine("*(Students don't see this)*")
                    appendLine()
                    appendLine(summary)

                    buildReport.junitErrorsHidden?.let { errors ->
                        appendLine()
                        appendLine("**Errors:**")
                        appendLine("```")
                        appendLine(errors)
                        appendLine("```")
                    }
                    appendLine()
                }
            }
        }

        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = reportText
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
                name = "get_submission_info",
                description = "Retrieve detailed information about a student submission, including build status, " +
                        "test results, compilation errors, code quality issues, and group member information. " +
                        "This provides a comprehensive report similar to what appears on the build report page. " +
                        "Teachers see additional information including hidden tests. " +
                        "Students can only access their own submissions.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "submissionId" to mapOf(
                            "type" to "number",
                            "description" to "The numeric ID of the submission to retrieve information from"
                        )
                    ),
                    "required" to listOf("submissionId")
                )
            )
        }

        /**
         * Factory method to create GetSubmissionInfo from arguments map.
         *
         * @param arguments Map containing the submissionId
         * @return GetSubmissionInfo instance
         * @throws IllegalArgumentException if submissionId is missing or invalid
         */
        fun from(arguments: Map<String, Any>): GetSubmissionInfo {
            val submissionId = (arguments["submissionId"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("submissionId is required and must be a number")
            return GetSubmissionInfo(submissionId)
        }
    }
}
