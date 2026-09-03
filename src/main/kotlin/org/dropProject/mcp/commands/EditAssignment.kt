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

import org.dropproject.dao.Assignment
import org.dropproject.dao.AssignmentVisibility
import org.dropproject.dao.Language
import org.dropproject.dao.LeaderboardType
import org.dropproject.dao.SubmissionStatus
import org.dropproject.dao.SubmissionStructure
import org.dropproject.dao.TestVisibility
import org.dropproject.forms.AssignmentForm
import org.dropproject.forms.SubmissionMethod
import org.dropproject.mcp.data.McpContent
import org.dropproject.mcp.data.McpTool
import org.dropproject.mcp.data.McpToolCallResult
import org.dropproject.mcp.services.McpService
import org.dropproject.services.AssignmentFormError
import org.dropproject.services.AssignmentValidationInputs
import org.dropproject.services.SubmissionEvaluationInputs
import java.security.Principal

/**
 * Command to change the configuration of an assignment that already exists, i.e. everything that
 * [CreateAssignment] sets except the assignment's own id and the git repository that defines it.
 *
 * Only the settings that the caller passes are changed: the ones that are left out keep the value that the
 * assignment already has, and the ones that are passed empty are cleared. This is what makes it usable by a model
 * that only knows about the setting it was asked to change.
 *
 * The contents of the git repository are not touched here (that is what [RefreshAssignment] does), but a change to
 * the settings that are cross-checked against those contents invalidates the stored validation report, so the
 * assignment is validated again, and marked inactive if the new configuration doesn't match its files.
 *
 * @property assignmentId identifies the assignment to change
 * @property changes carries the settings to change, and only those
 */
data class EditAssignment(val assignmentId: String, val changes: AssignmentArguments) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {

        service.requireTeacher("edit assignments")

        val assignmentService = service.assignmentService
        val assignment = service.getAssignmentToChange(assignmentId, principal)

        val form = formFor(assignment, assignmentService.assigneeRepository
            .findByAssignmentIdOrderByAuthorUserId(assignment.id).map { it.authorUserId },
            assignmentService.assignmentACLRepository.findByAssignmentId(assignment.id).map { it.userId })

        val errors = assignmentService.validateAssignmentForm(form, principal).toMutableList()

        // the ACL of a new assignment is validated by validateAssignmentForm, but the owner is only known here
        if (form.acl?.split(",")?.map { it.trim() }?.contains(assignment.ownerUserId) == true) {
            errors.add(AssignmentFormError("acl", "acl.includeOwner",
                "Error: acl must not include the owner of the assignment (${assignment.ownerUserId}), " +
                        "only the other teachers"))
        }

        if (errors.isNotEmpty()) {
            return McpToolCallResult(
                content = listOf(McpContent(type = "text",
                    text = "The assignment was not changed:\n" + errors.joinToString("\n") { "- ${it.message}" })),
                isError = true
            )
        }

        val settingsBefore = settingsOf(assignment, service)

        // the properties that the validation of the assignment's own files depends on, and the ones that the
        // evaluation of the students' submissions depends on. Both are compared again after the update, to find
        // out what became stale
        val validationInputsBefore = AssignmentValidationInputs.from(assignment)
        val evaluationInputsBefore = SubmissionEvaluationInputs.from(assignment)

        assignmentService.updateAssignment(assignment, form)

        // updateAssignmentACLAndAssignees only replaces the ACL when the form has one, so emptying it is done here
        if (form.acl == null) {
            assignmentService.assignmentACLRepository.deleteByAssignmentId(assignment.id)
        }

        // rolls back the whole edit, since an assignment with the wrong submitters is worse than an unchanged one
        assignmentService.updateAssignmentACLAndAssignees(assignment, form)?.let {
            throw IllegalArgumentException(it.message)
        }

        // an assignment that was never connected to its repository has no files to validate against
        val validatedAgain = assignment.gitCurrentHash != null &&
                AssignmentValidationInputs.from(assignment) != validationInputsBefore

        val validationFailed = if (validatedAgain) {
            assignmentService.validateAndStoreReport(assignment, principal)
        } else {
            false
        }

        if (validationFailed) {
            assignment.active = false
        }

        service.assignmentRepository.save(assignment)

        val settingsAfter = settingsOf(assignment, service)

        // the submissions that were already evaluated keep the indicators that were calculated with the previous
        // configuration. They are not rebuilt automatically, since that could mean hundreds of maven executions
        // triggered by a single tool call - instead, the teacher decides which of them are worth rebuilding
        val staleSubmissions =
            if (SubmissionEvaluationInputs.from(assignment) != evaluationInputsBefore) {
                service.submissionRepository
                    .countByAssignmentIdAndStatusNot(assignment.id, SubmissionStatus.DELETED.code)
            } else {
                0
            }

        val text = buildString {
            appendLine("# Assignment '${assignment.id}' was updated")
            appendLine()

            val changedSettings = settingsAfter.filter { (name, value) -> settingsBefore[name] != value }
            if (changedSettings.isEmpty()) {
                appendLine("Nothing changed: the arguments matched the settings that the assignment already had.")
            } else {
                appendLine("## Changes")
                changedSettings.forEach { (name, value) ->
                    appendLine("- $name: ${settingsBefore[name]} -> $value")
                }
            }

            if (validatedAgain) {
                appendLine()
                appendLine("The new configuration is cross-checked against the files of the assignment's git " +
                        "repository, so it was validated again.")
                appendLine()
                appendLine(AssignmentTools.formatValidationReport(
                    assignmentService.assignmentReportRepository.findByAssignmentId(assignment.id)))
            }

            if (validationFailed) {
                appendLine()
                appendLine("The assignment was marked inactive, since students can't submit to an assignment whose " +
                        "validation report has errors. Fix them in the git repository, push them, and call " +
                        "refresh_assignment with assignmentId=\"${assignment.id}\".")
            }

            if (staleSubmissions > 0) {
                appendLine()
                appendLine("$staleSubmissions submission(s) were evaluated with the previous configuration. They " +
                        "keep the results they already had, so rebuild them if the new configuration should apply " +
                        "to them.")
            }
        }

        return McpToolCallResult(content = listOf(McpContent(type = "text", text = text)))
    }

    /**
     * Builds the [AssignmentForm] that describes [assignment] after the requested changes, so that the update goes
     * through the same code as the web form. The settings that were not passed to the tool are filled in with what
     * the assignment already has, which is what keeps this a partial update.
     */
    private fun formFor(assignment: Assignment, assignees: List<String>, acl: List<String>): AssignmentForm {

        val restrictions = assignment.projectGroupRestrictions

        val maxMemoryMb = changes.orCurrent("maxMemoryMb", assignment.maxMemoryMb) { number(it) }
        if (maxMemoryMb != null && maxMemoryMb < 32) {
            throw IllegalArgumentException("maxMemoryMb must be >= 32")
        }

        return AssignmentForm(
            editMode = true,
            assignmentId = assignment.id,
            gitRepositoryUrl = assignment.gitRepositoryUrl,
            assignmentName = changes.orCurrent("assignmentName", assignment.name) { requiredString(it) },
            assignmentPackage = changes.orCurrent("packageName", assignment.packageName) { string(it) },
            assignmentTags = changes.orCurrent("tags", assignment.tagsStr.joinToString(",").ifBlank { null }) {
                string(it)
            },
            language = changes.orCurrent("language", assignment.language) { requiredEnum(it, Language.entries) },
            submissionMethod = changes.orCurrent("submissionMethod", assignment.submissionMethod) {
                requiredEnum(it, SubmissionMethod.entries)
            },
            submissionStructure = changes.orCurrent("submissionStructure", assignment.submissionStructure) {
                requiredEnum(it, SubmissionStructure.entries)
            },
            dueDate = changes.orCurrent("dueDate", assignment.dueDate?.toLocalDateTime()) { dateTime(it) },
            acceptsStudentTests = changes.orCurrent("acceptsStudentTests", assignment.acceptsStudentTests) {
                boolean(it) ?: false
            },
            minStudentTests = changes.orCurrent("minStudentTests", assignment.minStudentTests) { number(it) },
            calculateStudentTestsCoverage = changes.orCurrent("calculateStudentTestsCoverage",
                assignment.calculateStudentTestsCoverage) { boolean(it) ?: false },
            coverageVisibleToStudents = changes.orCurrent("coverageVisibleToStudents",
                assignment.coverageVisibleToStudents) { boolean(it) ?: false },
            hiddenTestsVisibility = changes.orCurrent("hiddenTestsVisibility", assignment.hiddenTestsVisibility) {
                enum(it, TestVisibility.entries)
            },
            mandatoryTestsSuffix = changes.orCurrent("mandatoryTestsSuffix", assignment.mandatoryTestsSuffix) {
                string(it)
            },
            leaderboardType = changes.orCurrent("leaderboardType", assignment.leaderboardType) {
                enum(it, LeaderboardType.entries)
            },
            cooloffPeriod = changes.orCurrent("cooloffPeriod", assignment.cooloffPeriod) { number(it) },
            maxMemoryMb = maxMemoryMb,
            minGroupSize = changes.orCurrent("minGroupSize", restrictions?.minGroupSize) { number(it) },
            maxGroupSize = changes.orCurrent("maxGroupSize", restrictions?.maxGroupSize) { number(it) },
            // not settable through the tools, so it would be lost if it wasn't carried over
            exceptions = restrictions?.exceptions,
            visibility = changes.orCurrent("visibility", assignment.visibility) {
                requiredEnum(it, AssignmentVisibility.entries)
            },
            assignees = changes.orCurrent("assignees", assignees.joinToString(",").ifBlank { null }) { string(it) },
            acl = changes.orCurrent("acl", acl.joinToString(",").ifBlank { null }) { string(it) }
        )
    }

    /**
     * The current configuration of [assignment], to be compared before and after the update so that the answer can
     * describe exactly what this call changed.
     */
    private fun settingsOf(assignment: Assignment, service: McpService): Map<String, String> {
        return AssignmentTools.settings(
            assignment,
            assignees = service.assignmentService.assigneeRepository
                .findByAssignmentIdOrderByAuthorUserId(assignment.id).map { it.authorUserId },
            acl = service.assignmentService.assignmentACLRepository
                .findByAssignmentId(assignment.id).map { it.userId },
            tags = assignment.tagsStr
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
                name = "edit_assignment",
                description = "Change the configuration of an assignment that already exists in Drop Project. " +
                        "Only the settings that are passed are changed: the ones that are left out keep their " +
                        "current value, and the ones that are passed empty are cleared. The id of the assignment " +
                        "and the git repository that defines it can't be changed. Changing a setting that Drop " +
                        "Project cross-checks against the files of that repository validates the assignment again, " +
                        "and marks it inactive if they no longer match. Only the owner of the assignment and the " +
                        "teachers authorized on it can use this tool.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "assignmentId" to mapOf(
                            "type" to "string",
                            "description" to "The ID of the assignment to change. It is not itself changeable"
                        ),
                        "assignmentName" to mapOf(
                            "type" to "string",
                            "description" to "Human readable name of the assignment, shown to the students"
                        ),
                        "language" to mapOf(
                            "type" to "string",
                            "enum" to Language.entries.map { it.name },
                            "description" to "Programming language of the assignment"
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
                                    "GIT repository"
                        ),
                        "submissionStructure" to mapOf(
                            "type" to "string",
                            "enum" to SubmissionStructure.entries.map { it.name },
                            "description" to "Expected structure of the submitted projects"
                        ),
                        "dueDate" to mapOf(
                            "type" to "string",
                            "description" to "Date after which submissions are marked as late, in ISO-8601 " +
                                    "format, e.g. '2026-10-15T23:59'. Pass an empty string to remove the due date"
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
                                    "submission considered valid"
                        ),
                        "leaderboardType" to mapOf(
                            "type" to "string",
                            "enum" to LeaderboardType.entries.map { it.name },
                            "description" to "Criterion used to sort the leaderboard. Pass an empty string to " +
                                    "remove the leaderboard"
                        ),
                        "cooloffPeriod" to mapOf(
                            "type" to "number",
                            "description" to "Minutes that students must wait between submissions"
                        ),
                        "maxMemoryMb" to mapOf(
                            "type" to "number",
                            "description" to "Memory limit, in MB, of the evaluation of each submission. Must be >= 32"
                        ),
                        "minGroupSize" to mapOf(
                            "type" to "number",
                            "description" to "Minimum number of students per group. Pass an empty value to remove " +
                                    "the group restrictions"
                        ),
                        "maxGroupSize" to mapOf(
                            "type" to "number",
                            "description" to "Maximum number of students per group. Only valid together with " +
                                    "minGroupSize"
                        ),
                        "visibility" to mapOf(
                            "type" to "string",
                            "enum" to AssignmentVisibility.entries.map { it.name },
                            "description" to "PUBLIC (listed to every student), ONLY_BY_LINK or PRIVATE (only the " +
                                    "authorized submitters, which then must be filled in)"
                        ),
                        "assignees" to mapOf(
                            "type" to "string",
                            "description" to "Comma separated user ids of the students who are allowed to submit, " +
                                    "replacing the current ones. Pass an empty string to let any student submit"
                        ),
                        "acl" to mapOf(
                            "type" to "string",
                            "description" to "Comma separated user ids of the other teachers who may change this " +
                                    "assignment, replacing the current ones. The owner must not be included"
                        ),
                        "tags" to mapOf(
                            "type" to "string",
                            "description" to "Comma separated tags used to organize assignments, replacing the " +
                                    "current ones, e.g. 'project,25/26'"
                        )
                    ),
                    "required" to listOf("assignmentId")
                )
            )
        }

        /**
         * The arguments that this tool accepts, taken from its own schema so that the two can't drift apart. An
         * argument that isn't one of these is rejected instead of ignored, since silently not applying a change is
         * worse than failing to make it.
         */
        @Suppress("UNCHECKED_CAST")
        private val acceptedArguments: Set<String> =
            (toMcpTool().inputSchema["properties"] as Map<String, Any>).keys

        /**
         * Factory method to create EditAssignment from arguments map.
         *
         * @param arguments Map containing the assignmentId and the settings to change
         * @return EditAssignment instance
         * @throws IllegalArgumentException if the assignmentId is missing or an unchangeable setting was passed
         */
        fun from(arguments: Map<String, Any>): EditAssignment {

            if (arguments.containsKey("gitRepositoryUrl")) {
                throw IllegalArgumentException("gitRepositoryUrl can't be changed, since the assignment is defined " +
                        "by the repository it points at. Create another assignment if it has to be defined by a " +
                        "different repository")
            }

            val unknownArguments = arguments.keys - acceptedArguments
            if (unknownArguments.isNotEmpty()) {
                throw IllegalArgumentException("Unknown argument(s): ${unknownArguments.joinToString()}. " +
                        "The settings that can be changed are ${(acceptedArguments - "assignmentId").joinToString()}")
            }

            val changes = AssignmentArguments(arguments)

            return EditAssignment(changes.requiredString("assignmentId"), changes)
        }
    }
}
