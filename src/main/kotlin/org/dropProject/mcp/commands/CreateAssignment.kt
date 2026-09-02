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

import org.dropproject.dao.AssignmentVisibility
import org.dropproject.dao.Language
import org.dropproject.dao.LeaderboardType
import org.dropproject.dao.SubmissionStructure
import org.dropproject.dao.TestVisibility
import org.dropproject.forms.AssignmentForm
import org.dropproject.forms.SubmissionMethod
import org.dropproject.mcp.data.McpContent
import org.dropproject.mcp.data.McpTool
import org.dropproject.mcp.data.McpToolCallResult
import org.dropproject.mcp.services.McpService
import java.security.Principal
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * Command to create a new assignment, together with the ssh key pair that Drop Project will use to read the git
 * repository that defines it.
 *
 * The repository is not cloned here: that only becomes possible once the public key that this command returns is
 * installed on it as a deploy key, which Drop Project can't do on the teacher's behalf. Cloning is therefore left
 * to [ConnectAssignment].
 *
 * @property assignmentForm carries the properties of the assignment to create
 */
data class CreateAssignment(val assignmentForm: AssignmentForm) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {

        service.requireTeacher("create assignments")

        val errors = service.assignmentService.validateAssignmentForm(assignmentForm, principal)
        if (errors.isNotEmpty()) {
            return McpToolCallResult(
                content = listOf(McpContent(type = "text",
                    text = "The assignment was not created:\n" + errors.joinToString("\n") { "- ${it.message}" })),
                isError = true
            )
        }

        val assignment = service.assignmentService.buildAssignmentFromForm(assignmentForm, principal)
        service.assignmentRepository.save(assignment)

        // rolls back everything that was done so far, since an assignment with the wrong submitters is worse than
        // no assignment at all
        service.assignmentService.updateAssignmentACLAndAssignees(assignment, assignmentForm)?.let {
            throw IllegalArgumentException(it.message)
        }

        val publicKey = service.assignmentService.generateGitConnectionKeyPair(assignment)

        val text = buildString {
            appendLine("# Assignment '${assignment.id}' was created")
            appendLine()
            appendLine("It is inactive and not yet connected to ${assignment.gitRepositoryUrl}.")
            appendLine()
            appendLine("## Next step: install the deploy key")
            appendLine()
            appendLine("Drop Project only reads the repository, so this key should be installed as a read-only ")
            appendLine("deploy key. The matching private key stays in Drop Project and is never returned.")
            appendLine()
            appendLine("```")
            appendLine(publicKey.trim())
            appendLine("```")
            appendLine()
            AssignmentTools.deployKeyPageUrl(assignment.gitRepositoryUrl)?.let {
                appendLine("Install it with `gh repo deploy-key add <file with the key above> --title \"Drop Project\"`,")
                appendLine("or by pasting it at $it")
            } ?: appendLine("Install it through the interface or the cli of the git host of the repository.")
            appendLine()
            appendLine("Once the key is installed, call connect_assignment with assignmentId=\"${assignment.id}\".")
        }

        return McpToolCallResult(content = listOf(McpContent(type = "text", text = text)))
    }

    companion object {

        /**
         * Get the MCP tool metadata for this command.
         *
         * @return The McpTool metadata
         */
        fun toMcpTool(): McpTool {
            return McpTool(
                name = "create_assignment",
                description = "Create a new assignment in Drop Project and get back the ssh public key that must " +
                        "be installed as a read-only deploy key on its git repository. The repository must already " +
                        "exist and contain the teacher's Maven project (pom.xml, unit tests and instructions) - " +
                        "Drop Project only reads repositories, it never creates or writes to them. The assignment " +
                        "is created inactive and disconnected; call connect_assignment once the deploy key is in " +
                        "place. Only teachers can use this tool.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "assignmentId" to mapOf(
                            "type" to "string",
                            "description" to "Unique ID of the assignment, also used as the name of the folder " +
                                    "where its repository is cloned. Only letters, numbers, hyphens and underscores"
                        ),
                        "assignmentName" to mapOf(
                            "type" to "string",
                            "description" to "Human readable name of the assignment, shown to the students"
                        ),
                        "gitRepositoryUrl" to mapOf(
                            "type" to "string",
                            "description" to "SSH url of the git repository that defines the assignment, e.g. " +
                                    "'git@github.com:owner/repo.git'. Only SSH urls are accepted"
                        ),
                        "language" to mapOf(
                            "type" to "string",
                            "enum" to Language.entries.map { it.name },
                            "description" to "Programming language of the assignment. Defaults to JAVA"
                        ),
                        "packageName" to mapOf(
                            "type" to "string",
                            "description" to "Java/Kotlin package of the assignment, e.g. 'org.dropproject.samples'. " +
                                    "Without it, Drop Project can't filter the stacktraces shown to the students"
                        ),
                        "submissionMethod" to mapOf(
                            "type" to "string",
                            "enum" to SubmissionMethod.entries.map { it.name },
                            "description" to "How students submit: UPLOAD of a zip file or connecting their own " +
                                    "GIT repository. Defaults to UPLOAD"
                        ),
                        "submissionStructure" to mapOf(
                            "type" to "string",
                            "enum" to SubmissionStructure.entries.map { it.name },
                            "description" to "Expected structure of the submitted projects. Defaults to COMPACT"
                        ),
                        "dueDate" to mapOf(
                            "type" to "string",
                            "description" to "Date after which submissions are marked as late, in ISO-8601 " +
                                    "format, e.g. '2026-10-15T23:59'. Optional"
                        ),
                        "acceptsStudentTests" to mapOf(
                            "type" to "boolean",
                            "description" to "Whether students are expected to submit their own unit tests"
                        ),
                        "minStudentTests" to mapOf(
                            "type" to "number",
                            "description" to "Minimum number of unit tests that the students must write. Only " +
                                    "valid together with acceptsStudentTests"
                        ),
                        "calculateStudentTestsCoverage" to mapOf(
                            "type" to "boolean",
                            "description" to "Whether to calculate the coverage of the students' own tests. Only " +
                                    "valid together with acceptsStudentTests"
                        ),
                        "hiddenTestsVisibility" to mapOf(
                            "type" to "string",
                            "enum" to TestVisibility.entries.map { it.name },
                            "description" to "How much students get to know about the results of the hidden tests"
                        ),
                        "mandatoryTestsSuffix" to mapOf(
                            "type" to "string",
                            "description" to "Suffix of the test methods that students must pass to have their " +
                                    "submission considered valid. Optional"
                        ),
                        "leaderboardType" to mapOf(
                            "type" to "string",
                            "enum" to LeaderboardType.entries.map { it.name },
                            "description" to "Criterion used to sort the leaderboard. Without it, the assignment " +
                                    "has no leaderboard"
                        ),
                        "cooloffPeriod" to mapOf(
                            "type" to "number",
                            "description" to "Minutes that students must wait between submissions. Optional"
                        ),
                        "maxMemoryMb" to mapOf(
                            "type" to "number",
                            "description" to "Memory limit, in MB, of the evaluation of each submission. Must be " +
                                    ">= 32. Optional"
                        ),
                        "minGroupSize" to mapOf(
                            "type" to "number",
                            "description" to "Minimum number of students per group. Without it, the assignment " +
                                    "has no group restrictions"
                        ),
                        "maxGroupSize" to mapOf(
                            "type" to "number",
                            "description" to "Maximum number of students per group. Only valid together with " +
                                    "minGroupSize"
                        ),
                        "visibility" to mapOf(
                            "type" to "string",
                            "enum" to AssignmentVisibility.entries.map { it.name },
                            "description" to "PUBLIC (listed to every student), ONLY_BY_LINK (the default) or " +
                                    "PRIVATE (only the authorized submitters, which then must be filled in)"
                        ),
                        "assignees" to mapOf(
                            "type" to "string",
                            "description" to "Comma separated user ids of the students who are allowed to submit. " +
                                    "Without it, any student can submit"
                        ),
                        "acl" to mapOf(
                            "type" to "string",
                            "description" to "Comma separated user ids of the other teachers who may change this " +
                                    "assignment. The owner must not be included"
                        ),
                        "tags" to mapOf(
                            "type" to "string",
                            "description" to "Comma separated tags used to organize assignments, e.g. 'project,25/26'"
                        )
                    ),
                    "required" to listOf("assignmentId", "assignmentName", "gitRepositoryUrl")
                )
            )
        }

        /**
         * Factory method to create CreateAssignment from arguments map.
         *
         * This is where the constraints that the web form delegates to bean validation are checked, since there is
         * no form binding here to do it.
         *
         * @param arguments Map containing the properties of the assignment
         * @return CreateAssignment instance
         * @throws IllegalArgumentException if a required argument is missing or an argument is invalid
         */
        fun from(arguments: Map<String, Any>): CreateAssignment {

            val assignmentId = requiredString(arguments, "assignmentId")
            if (!assignmentId.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                throw IllegalArgumentException(
                    "assignmentId must only contain letters, numbers, hyphens and underscores")
            }

            val maxMemoryMb = number(arguments, "maxMemoryMb")
            if (maxMemoryMb != null && maxMemoryMb < 32) {
                throw IllegalArgumentException("maxMemoryMb must be >= 32")
            }

            val form = AssignmentForm(
                assignmentId = assignmentId,
                assignmentName = requiredString(arguments, "assignmentName"),
                gitRepositoryUrl = requiredString(arguments, "gitRepositoryUrl"),
                assignmentPackage = string(arguments, "packageName"),
                assignmentTags = string(arguments, "tags"),
                language = enum(arguments, "language", Language.entries) ?: Language.JAVA,
                submissionMethod = enum(arguments, "submissionMethod", SubmissionMethod.entries)
                    ?: SubmissionMethod.UPLOAD,
                submissionStructure = enum(arguments, "submissionStructure", SubmissionStructure.entries)
                    ?: SubmissionStructure.COMPACT,
                dueDate = dateTime(arguments, "dueDate"),
                acceptsStudentTests = boolean(arguments, "acceptsStudentTests") ?: false,
                minStudentTests = number(arguments, "minStudentTests"),
                calculateStudentTestsCoverage = boolean(arguments, "calculateStudentTestsCoverage") ?: false,
                coverageVisibleToStudents = boolean(arguments, "coverageVisibleToStudents") ?: false,
                hiddenTestsVisibility = enum(arguments, "hiddenTestsVisibility", TestVisibility.entries),
                mandatoryTestsSuffix = string(arguments, "mandatoryTestsSuffix"),
                leaderboardType = enum(arguments, "leaderboardType", LeaderboardType.entries),
                cooloffPeriod = number(arguments, "cooloffPeriod"),
                maxMemoryMb = maxMemoryMb,
                minGroupSize = number(arguments, "minGroupSize"),
                maxGroupSize = number(arguments, "maxGroupSize"),
                visibility = enum(arguments, "visibility", AssignmentVisibility.entries)
                    ?: AssignmentVisibility.ONLY_BY_LINK,
                assignees = string(arguments, "assignees"),
                acl = string(arguments, "acl")
            )

            return CreateAssignment(form)
        }

        private fun requiredString(arguments: Map<String, Any>, name: String): String {
            return string(arguments, name)
                ?: throw IllegalArgumentException("$name is required and must be a non empty string")
        }

        private fun string(arguments: Map<String, Any>, name: String): String? {
            val value = arguments[name] ?: return null
            if (value !is String) {
                throw IllegalArgumentException("$name must be a string")
            }
            return value.ifBlank { null }
        }

        private fun number(arguments: Map<String, Any>, name: String): Int? {
            val value = arguments[name] ?: return null
            return (value as? Number)?.toInt()
                ?: throw IllegalArgumentException("$name must be a number")
        }

        private fun boolean(arguments: Map<String, Any>, name: String): Boolean? {
            val value = arguments[name] ?: return null
            return value as? Boolean
                ?: throw IllegalArgumentException("$name must be a boolean")
        }

        private fun <T : Enum<T>> enum(arguments: Map<String, Any>, name: String, values: List<T>): T? {
            val value = string(arguments, name) ?: return null
            return values.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("$name must be one of ${values.joinToString { it.name }}")
        }

        private fun dateTime(arguments: Map<String, Any>, name: String): LocalDateTime? {
            val value = string(arguments, name) ?: return null
            return try {
                LocalDateTime.parse(value)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("$name must be a date in ISO-8601 format, e.g. '2026-10-15T23:59'")
            }
        }
    }
}
