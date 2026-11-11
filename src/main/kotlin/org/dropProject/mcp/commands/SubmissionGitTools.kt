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
import org.dropproject.dao.Author
import org.dropproject.dao.GitSubmission
import org.dropproject.dao.Submission
import org.dropproject.extensions.formatDefault
import org.dropproject.forms.SubmissionMethod
import org.dropproject.mcp.services.McpService
import org.dropproject.services.GitAuthorMatcher
import org.dropproject.services.SubmissionGitService
import org.dropproject.services.SubmissionGitService.Companion.SHORT_HASH_LENGTH
import java.security.Principal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

/**
 * Helpers shared by the tools that analyse the git repository behind a submission.
 */
object SubmissionGitTools {

    /**
     * A git identity, i.e. the name and email that git recorded for the author of a commit. Emails are compared
     * ignoring case, since git hosts do the same.
     */
    data class GitIdentity(val name: String, val email: String) {
        override fun equals(other: Any?) =
            other is GitIdentity && name == other.name && email.equals(other.email, ignoreCase = true)

        override fun hashCode() = 31 * name.hashCode() + email.lowercase().hashCode()

        override fun toString() = if (email.isBlank()) name else "$name <$email>"
    }

    /**
     * Returns the submission with [submissionId] and its assignment, provided that the current user is a teacher
     * authorized on that assignment and that the assignment takes git submissions.
     *
     * @param analysis names what is being computed, for the error messages (e.g. "git history")
     */
    fun authorizedGitSubmission(service: McpService, principal: Principal, submissionId: Long,
                                analysis: String): Pair<Submission, Assignment> {
        service.requireTeacher("see the $analysis of a submission")

        val submission = service.submissionRepository.findById(submissionId).orElseThrow {
            IllegalArgumentException("Submission with ID $submissionId not found")
        }
        val assignment = service.getAuthorizedAssignment(submission.assignmentId, principal)

        if (assignment.submissionMethod != SubmissionMethod.GIT) {
            throw IllegalArgumentException("Submission method of assignment ${assignment.id} is " +
                    "${assignment.submissionMethod}; $analysis is only available for GIT submissions")
        }

        return Pair(submission, assignment)
    }

    /**
     * Reads a comma separated list of path prefixes, e.g. "src/, test-files/", into the form that
     * [SubmissionGitService] expects (relative, without slashes at the ends).
     */
    fun paths(value: String?, default: String): List<String> {
        val paths = (value ?: default).split(",")
            .map { it.trim().trim('/') }
            .filter { it.isNotEmpty() }

        if (paths.isEmpty()) {
            throw IllegalArgumentException("paths must have at least one path, e.g. '$default'")
        }
        paths.find { path -> path.split("/").any { it == ".." || it == "." } }?.let {
            throw IllegalArgumentException("paths must be relative to the root of the repository, but '$it' isn't")
        }

        return paths
    }

    /**
     * Reads an ISO-8601 date, with or without time and offset, e.g. "2026-10-15", "2026-10-15T23:59" or
     * "2026-10-15T23:59:00Z". Dates without an offset are in the server's time zone.
     *
     * @param endOfDay if true, a date without time means the end of that day, so that it includes the whole day
     */
    fun date(name: String, value: String?, endOfDay: Boolean): Date? {
        if (value == null) {
            return null
        }

        val zone = ZoneId.systemDefault()
        val instant = parseOrNull { OffsetDateTime.parse(value).toInstant() }
            ?: parseOrNull { LocalDateTime.parse(value).atZone(zone).toInstant() }
            ?: parseOrNull {
                val day = LocalDate.parse(value)
                (if (endOfDay) day.atTime(LocalTime.MAX) else day.atStartOfDay()).atZone(zone).toInstant()
            }
            ?: throw IllegalArgumentException("$name must be a date in ISO-8601 format, e.g. '2026-10-15' or " +
                    "'2026-10-15T23:59'")

        return Date.from(instant)
    }

    private fun <T> parseOrNull(parse: () -> T): T? =
        try {
            parse()
        } catch (e: DateTimeParseException) {
            null
        }

    /**
     * The lines that tell which repository and commit were analysed, plus the warnings about that commit, if any.
     */
    fun repositoryHeader(gitSubmission: GitSubmission, analysedCommit: SubmissionGitService.AnalysedCommit,
                         service: McpService): String = buildString {
        val url = gitSubmission.gitRepositoryUrl
        val repository = if (service.gitClient.checkValidSSHGithubURL(url)) {
            service.gitClient.convertSSHGithubURLtoHttpURL(url)
        } else {
            url
        }
        appendLine("**Repository:** $repository")

        if (analysedCommit.isSubmittedCommit) {
            appendLine("**Submitted commit:** ${describe(analysedCommit)}")
        } else {
            appendLine("**Submitted commit:** " +
                    (analysedCommit.submittedCommitHash?.let { "${it.take(SHORT_HASH_LENGTH)} (not found)" }
                        ?: "not registered"))
            appendLine("**Analysed commit:** ${describe(analysedCommit)}")
        }
    }

    fun warnings(analysedCommit: SubmissionGitService.AnalysedCommit): String = buildString {
        if (analysedCommit.warnings.isNotEmpty()) {
            appendLine("## Warnings")
            analysedCommit.warnings.forEach { appendLine("- $it") }
            appendLine()
        }
    }

    private fun describe(analysedCommit: SubmissionGitService.AnalysedCommit) =
        "${analysedCommit.commit.name.take(SHORT_HASH_LENGTH)} (${analysedCommit.date.formatDefault()})"

    fun groupMembers(members: List<Author>): String = buildString {
        appendLine("## Group Members")
        members.forEach { appendLine("- ${it.userId} - ${it.name}") }
        appendLine()
    }

    /**
     * The member that [identity] belongs to, as shown in the "Mapped student" columns.
     */
    fun mappedStudent(match: GitAuthorMatcher.Match): String = when (match) {
        is GitAuthorMatcher.Match.Mapped -> match.member.userId
        is GitAuthorMatcher.Match.Ambiguous -> "-"
        GitAuthorMatcher.Match.Unmapped -> "-"
    }

    /**
     * Lists the git identities that weren't linked to any member, explaining the ones that matched several.
     *
     * @param counts describes how much each identity did (e.g. "3 commits"), to help a teacher decide whose it is
     */
    fun unmappedAuthors(matches: Map<GitIdentity, GitAuthorMatcher.Match>, counts: (GitIdentity) -> String): String =
        buildString {
            appendLine("## Unmapped git authors")
            val unmapped = matches.filterValues { it !is GitAuthorMatcher.Match.Mapped }
            if (unmapped.isEmpty()) {
                appendLine("- (none)")
            }
            unmapped.forEach { (identity, match) ->
                val ambiguity = if (match is GitAuthorMatcher.Match.Ambiguous) {
                    " - matches more than one member: ${match.candidates.joinToString { it.userId }}"
                } else {
                    ""
                }
                appendLine("- ${cell(identity.toString())} (${counts(identity)})$ambiguity")
            }
            appendLine()
            appendLine("Git authors are linked to a member when their email or name contains the member's number, " +
                    "or when their name is the member's name (ignoring case, accents and punctuation). The others " +
                    "are never attributed to anyone.")
            appendLine()
        }

    /**
     * Escapes [text] to be used inside a markdown table cell.
     */
    fun cell(text: String): String = text.replace("|", "\\|").replace("\n", " ")

    fun percentage(part: Int, total: Int): String =
        if (total == 0) "-" else String.format(Locale.ROOT, "%.1f%%", part * 100.0 / total)
}
