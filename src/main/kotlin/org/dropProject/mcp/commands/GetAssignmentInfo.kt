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
import java.time.ZoneId
import java.util.Date

/**
 * Appends a line describing one of the assignment's settings. Empty and null values are described as
 * "not set", to tell the settings that the assignment doesn't use from the ones that this listing forgot.
 */
private fun StringBuilder.appendSetting(name: String, value: Any?) {
    val description = value?.toString()?.ifBlank { null } ?: "not set"
    appendLine("**$name:** $description")
}

/**
 * Formats a date the way create_assignment expects to receive it, e.g. '2026-10-15T23:59'.
 */
private fun Date.toIso8601(): String =
    toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString()

/**
 * Command to retrieve comprehensive information about a programming assignment.
 *
 * @property assignmentId The ID of the assignment to retrieve
 */
data class GetAssignmentInfo(val assignmentId: String) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {
        // Use AssignmentService to get detailed assignment information
        val assignmentDetail = service.assignmentService.getAssignmentDetailData(
            assignmentId, principal, isAdmin = true // MCP users are treated as admins
        )

        val assignment = assignmentDetail.assignment

        // Format assignment detail for MCP response
        val assignmentText = buildString {
            appendLine("# Assignment: ${assignment.name}")
            appendLine("**ID:** ${assignment.id}")
            appendLine("**Owner:** ${assignment.ownerUserId}")
            appendLine("**Active:** ${assignment.active}")
            appendLine("**Archived:** ${assignment.archived}")

            // the whole configuration is listed with the names of the create_assignment arguments that set it, so
            // that an assignment can be recreated from this listing (e.g. for a new edition of the same course)
            appendLine("\n## Configuration")
            appendLine("Named after the create_assignment arguments that set each setting.\n")
            appendSetting("assignmentName", assignment.name)
            appendSetting("gitRepositoryUrl", assignment.gitRepositoryUrl)
            appendSetting("language", assignment.language)
            appendSetting("packageName", assignment.packageName)
            appendSetting("submissionMethod", assignment.submissionMethod)
            appendSetting("submissionStructure", assignment.submissionStructure)
            appendSetting("dueDate", assignment.dueDate?.toIso8601())
            appendSetting("acceptsStudentTests", assignment.acceptsStudentTests)
            appendSetting("minStudentTests", assignment.minStudentTests)
            appendSetting("calculateStudentTestsCoverage", assignment.calculateStudentTestsCoverage)
            appendSetting("hiddenTestsVisibility", assignment.hiddenTestsVisibility)
            appendSetting("mandatoryTestsSuffix", assignment.mandatoryTestsSuffix)
            appendSetting("leaderboardType", assignment.leaderboardType)
            appendSetting("cooloffPeriod", assignment.cooloffPeriod)
            appendSetting("maxMemoryMb", assignment.maxMemoryMb)
            appendSetting("minGroupSize", assignment.projectGroupRestrictions?.minGroupSize)
            appendSetting("maxGroupSize", assignment.projectGroupRestrictions?.maxGroupSize)
            appendSetting("visibility", assignment.visibility)
            appendSetting("assignees", assignmentDetail.assignees.joinToString(",") { it.authorUserId })
            appendSetting("acl", assignmentDetail.acl.joinToString(",") { it.userId })
            appendSetting("tags", assignmentDetail.tags.joinToString(","))

            if (assignment.instructions != null) {
                appendLine("\n## Instructions")
                appendLine(assignment.instructions.toString())
            }

            if (assignmentDetail.tests.isNotEmpty()) {
                appendLine("\n## Tests (${assignmentDetail.tests.size})")
                assignmentDetail.tests.forEach { test ->
                    appendLine("- ${test.testMethod}")
                }
            }

            if (assignmentDetail.reports.isNotEmpty()) {
                appendLine("\n## Validation Report")
                appendLine(assignmentDetail.reportMessage)
                assignmentDetail.reports.forEach { report ->
                    appendLine("- ${report.type}: ${report.message}")
                }
            }

            if (assignmentDetail.lastCommitInfo != null) {
                appendLine("\n## Git Information")
                appendLine("**Last Commit:** ${assignmentDetail.lastCommitInfo}")
                if (assignmentDetail.sshKeyFingerprint != null) {
                    appendLine("**SSH Key Fingerprint:** ${assignmentDetail.sshKeyFingerprint}")
                }
            }
        }

        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = assignmentText
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
                name = "get_assignment_info",
                description = "Get comprehensive information about a programming assignment in Drop Project, " +
                        "including instructions, requirements, due dates, submission methods, and grading criteria. " +
                        "Useful when a student or teacher needs detailed assignment context. The assignment's " +
                        "settings are listed with the names of the create_assignment arguments that set them, so " +
                        "that an assignment can be recreated from them, e.g. for a new edition of the same course.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "assignmentId" to mapOf(
                            "type" to "string",
                            "description" to "The ID of the assignment to retrieve"
                        )
                    ),
                    "required" to listOf("assignmentId")
                )
            )
        }

        /**
         * Factory method to create GetAssignmentInfo from arguments map.
         *
         * @param arguments Map containing the assignmentId
         * @return GetAssignmentInfo instance
         * @throws IllegalArgumentException if assignmentId is missing
         */
        fun from(arguments: Map<String, Any>): GetAssignmentInfo {
            val assignmentId = arguments["assignmentId"] as? String
                ?: throw IllegalArgumentException("assignmentId is required")
            return GetAssignmentInfo(assignmentId)
        }
    }
}
