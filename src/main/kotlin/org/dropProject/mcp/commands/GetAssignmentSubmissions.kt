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

import org.dropproject.data.SubmissionInfo
import org.dropproject.extensions.formatDefault
import org.dropproject.mcp.data.McpContent
import org.dropproject.mcp.data.McpTool
import org.dropproject.mcp.data.McpToolCallResult
import org.dropproject.mcp.services.McpService
import org.springframework.ui.ModelMap
import java.security.Principal

/**
 * Command to list the latest submission of each group that submitted to an assignment, together with the
 * result of the teacher tests. This is the assignment-wide counterpart of [GetSubmissionInfo], which only
 * covers one submission at a time.
 *
 * @property assignmentId The ID of the assignment whose submissions are to be listed
 */
data class GetAssignmentSubmissions(val assignmentId: String) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {

        service.requireTeacher("list the submissions of an assignment")

        // only the owner of the assignment, or a teacher who was given access to it, may see its submissions
        val assignment = service.getAuthorizedAssignment(assignmentId, principal)

        val model = ModelMap()
        service.assignmentService.getAllSubmissionsForAssignment(
            assignmentId, principal, model, service.request, mode = "summary"
        )

        @Suppress("UNCHECKED_CAST")
        val submissionInfoList = (model["submissions"] as? List<SubmissionInfo>).orEmpty()

        val assignees = service.assigneeRepository
            .findByAssignmentIdOrderByAuthorUserId(assignmentId)
            .map { it.authorUserId }

        val whoSubmitted = submissionInfoList
            .flatMap { it.projectGroup.authors.map { author -> author.userId } }
            .toSet()

        val listing = buildString {

            appendLine("# Submissions for ${assignment.name} (${assignment.id})")
            appendLine()

            appendLine("**Groups with submissions:** ${submissionInfoList.size}")

            val passingAllTeacherTests = submissionInfoList.count { info ->
                info.lastSubmission.teacherTests?.let { it.numFailures == 0 && it.numErrors == 0 } == true
            }
            appendLine("**Passing all the teacher tests:** $passingAllTeacherTests / ${submissionInfoList.size}")

            if (assignees.isNotEmpty()) {
                val didntSubmit = assignees.filterNot { it in whoSubmitted }
                appendLine("**Assignees:** ${assignees.size}")
                appendLine("**Assignees without submissions:** " +
                        if (didntSubmit.isEmpty()) "none" else didntSubmit.joinToString(", "))
            }
            appendLine()

            if (submissionInfoList.isEmpty()) {
                appendLine("No submissions yet.")
                return@buildString
            }

            appendLine("## Latest submission of each group")
            appendLine()

            submissionInfoList
                .sortedBy { it.projectGroup.authorsStr() }
                .forEach { info ->

                    val lastSubmission = info.lastSubmission

                    val authors = info.projectGroup.authors
                        .sortedBy { it.userId }
                        .joinToString(", ") { "${it.userId} - ${it.name}" }

                    appendLine("### $authors")
                    appendLine("- **Submission ID:** ${lastSubmission.id} " +
                            "(${info.allSubmissions.size} submission(s) in total)")
                    appendLine("- **Date:** ${lastSubmission.submissionDate.formatDefault()}")
                    appendLine("- **Status:** ${lastSubmission.getStatus()}")

                    lastSubmission.teacherTests?.let { teacherTests ->
                        appendLine("- **Teacher tests:** ${teacherTests.progress} / ${teacherTests.numTests}")
                    }

                    lastSubmission.structureErrors?.let { structureErrors ->
                        if (structureErrors.isNotBlank()) {
                            appendLine("- **Project structure errors:** $structureErrors")
                        }
                    }

                    if (lastSubmission.markedAsFinal) {
                        appendLine("- **Marked as final**")
                    }
                    if (lastSubmission.overdue == true) {
                        appendLine("- **Overdue**")
                    }

                    // defense assignments: how much the submission diverges from the submission being defended
                    lastSubmission.baseDivergenceLines?.let { divergence ->
                        appendLine("- **Changed lines relative to the base submission:** $divergence")
                    }

                    appendLine()
                }
        }

        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = listing
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
                name = "get_assignment_submissions",
                description = "List the latest submission of every group that submitted to an assignment, with " +
                        "the date, the status, how many teacher tests it passes and, for defense assignments, " +
                        "how many lines it changed relative to the submission being defended. Also reports which " +
                        "of the assignment's assignees have not submitted at all. " +
                        "Useful to see how a whole class did on an assignment, a mini-test or a defense, without " +
                        "looking up each student one by one. " +
                        "Only available to the owner of the assignment and to the teachers it was shared with.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "assignmentId" to mapOf(
                            "type" to "string",
                            "description" to "The ID of the assignment whose submissions are to be listed"
                        )
                    ),
                    "required" to listOf("assignmentId")
                )
            )
        }

        /**
         * Factory method to create GetAssignmentSubmissions from arguments map.
         *
         * @param arguments Map containing the assignmentId
         * @return GetAssignmentSubmissions instance
         * @throws IllegalArgumentException if assignmentId is missing
         */
        fun from(arguments: Map<String, Any>): GetAssignmentSubmissions {
            val assignmentId = arguments["assignmentId"] as? String
                ?: throw IllegalArgumentException("assignmentId is required")
            return GetAssignmentSubmissions(assignmentId)
        }
    }
}
