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
import org.dropproject.dao.AssignmentReport
import org.dropproject.services.AssignmentValidator

/**
 * Helpers shared by the tools that create and maintain assignments.
 */
object AssignmentTools {

    /**
     * Renders the validation report of an assignment, i.e. what Drop Project has to say about the contents of the
     * git repository that defines it.
     */
    fun formatValidationReport(reports: List<AssignmentReport>): String {
        if (reports.isEmpty()) {
            return "The assignment has no validation report yet."
        }

        return buildString {
            appendLine("## Validation report")
            reports.forEach { appendLine("- ${it.type}: ${it.message}") }
        }
    }

    /**
     * Tells whether a validation report has errors, which prevent the assignment from being activated.
     */
    fun hasErrors(reports: List<AssignmentReport>): Boolean {
        return reports.any { it.type == AssignmentValidator.InfoType.ERROR }
    }

    /**
     * Describes what should be done next with an assignment whose repository was just cloned or pulled: either fix
     * the problems that the validation found, or activate it so that students can start submitting.
     */
    fun nextStepAfterValidation(assignment: Assignment, reports: List<AssignmentReport>): String {
        return if (hasErrors(reports)) {
            "## Next step\n" +
                    "Fix the errors above in the assignment's git repository, push them, and call " +
                    "refresh_assignment with assignmentId=\"${assignment.id}\" to validate it again. The assignment " +
                    "cannot be activated while its report has errors."
        } else if (!assignment.active) {
            "## Next step\n" +
                    "The report has no errors, so the assignment is ready. Call set_assignment_active with " +
                    "assignmentId=\"${assignment.id}\" and active=true to let students submit to it."
        } else {
            "The assignment is active and students can submit to it."
        }
    }

    /**
     * Builds the page of the git host where a deploy key can be installed by hand, when it is known.
     */
    fun deployKeyPageUrl(gitRepositoryUrl: String): String? {
        if (!gitRepositoryUrl.contains("github")) {
            return null
        }

        // git@github.com:owner/repo.git
        val path = gitRepositoryUrl.substringAfter(":", "").removeSuffix(".git")
        return if (path.count { it == '/' } == 1) "https://github.com/$path/settings/keys" else null
    }
}
