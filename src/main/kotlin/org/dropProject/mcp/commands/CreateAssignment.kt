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

        // a defense doesn't get to choose its group size nor its package, so they are overwritten before the form is
        // validated and saved, exactly like the web form does
        service.assignmentService.applyDefenseSettings(assignmentForm)

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
            assignment.baseAssignmentId?.let {
                appendLine("## Defense of '$it'")
                appendLine()
                appendLine("Every submission will be compared with the group's last submission to that assignment, ")
                appendLine("so the group size was set to 1 and the package to the one of '$it'")
                appendLine("(${assignment.packageName ?: "which has none"}), neither of which is the defense's to ")
                appendLine("choose.")
                appendLine()
                appendLine("The defense instructions start out unreleased, i.e. the students only get to resubmit ")
                appendLine("their own code from '$it'. Releasing them, which starts the defense itself, is done in ")
                appendLine("the web interface, on the assignments list.")
                appendLine()
            }
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
                        "place, and edit_assignment to change any of these settings later. Only teachers can use " +
                        "this tool.",
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
                        "coverageVisibleToStudents" to mapOf(
                            "type" to "boolean",
                            "description" to "Whether the students get to see the coverage of their own tests. " +
                                    "Only meaningful together with calculateStudentTestsCoverage"
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
                        "baseAssignmentId" to mapOf(
                            "type" to "string",
                            "description" to "Turns this into a defense of the assignment with this id: the " +
                                    "students resubmit the code they had submitted there, changed as the defense " +
                                    "asks, and every submission is compared with that base submission. The caller " +
                                    "must be the owner or an authorized teacher of that assignment. A defense " +
                                    "always uses its package and a group size of 1, so packageName, minGroupSize " +
                                    "and maxGroupSize are overwritten with those values"
                        ),
                        "maxChangedLines" to mapOf(
                            "type" to "number",
                            "description" to "Maximum number of lines (under src) that a submission may change " +
                                    "relatively to the base submission, only enforced after the defense " +
                                    "instructions are released. Only valid together with baseAssignmentId. " +
                                    "Without it, the difference is measured and shown to the teacher, but never " +
                                    "rejected"
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

            val settings = AssignmentArguments(arguments)

            val assignmentId = settings.requiredString("assignmentId")
            if (!assignmentId.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                throw IllegalArgumentException(
                    "assignmentId must only contain letters, numbers, hyphens and underscores")
            }

            val maxMemoryMb = settings.number("maxMemoryMb")
            if (maxMemoryMb != null && maxMemoryMb < 32) {
                throw IllegalArgumentException("maxMemoryMb must be >= 32")
            }

            val baseAssignmentId = settings.string("baseAssignmentId")

            val maxChangedLines = settings.number("maxChangedLines")
            if (maxChangedLines != null && maxChangedLines < 1) {
                throw IllegalArgumentException("maxChangedLines must be >= 1")
            }
            if (maxChangedLines != null && baseAssignmentId == null) {
                throw IllegalArgumentException("maxChangedLines is only valid together with baseAssignmentId, " +
                        "since it limits how much a submission may diverge from the base submission")
            }

            val form = AssignmentForm(
                assignmentId = assignmentId,
                assignmentName = settings.requiredString("assignmentName"),
                gitRepositoryUrl = settings.requiredString("gitRepositoryUrl"),
                assignmentPackage = settings.string("packageName"),
                assignmentTags = settings.string("tags"),
                language = settings.enum("language", Language.entries) ?: Language.JAVA,
                submissionMethod = settings.enum("submissionMethod", SubmissionMethod.entries)
                    ?: SubmissionMethod.UPLOAD,
                submissionStructure = settings.enum("submissionStructure", SubmissionStructure.entries)
                    ?: SubmissionStructure.COMPACT,
                dueDate = settings.dateTime("dueDate"),
                acceptsStudentTests = settings.boolean("acceptsStudentTests") ?: false,
                minStudentTests = settings.number("minStudentTests"),
                calculateStudentTestsCoverage = settings.boolean("calculateStudentTestsCoverage") ?: false,
                coverageVisibleToStudents = settings.boolean("coverageVisibleToStudents") ?: false,
                hiddenTestsVisibility = settings.enum("hiddenTestsVisibility", TestVisibility.entries),
                mandatoryTestsSuffix = settings.string("mandatoryTestsSuffix"),
                leaderboardType = settings.enum("leaderboardType", LeaderboardType.entries),
                cooloffPeriod = settings.number("cooloffPeriod"),
                maxMemoryMb = maxMemoryMb,
                minGroupSize = settings.number("minGroupSize"),
                maxGroupSize = settings.number("maxGroupSize"),
                visibility = settings.enum("visibility", AssignmentVisibility.entries)
                    ?: AssignmentVisibility.ONLY_BY_LINK,
                baseAssignmentId = baseAssignmentId,
                maxChangedLines = maxChangedLines,
                assignees = settings.string("assignees"),
                acl = settings.string("acl")
            )

            return CreateAssignment(form)
        }
    }
}
