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

import org.dropproject.extensions.formatDefault
import org.dropproject.mcp.commands.SubmissionGitTools.GitIdentity
import org.dropproject.mcp.commands.SubmissionGitTools.cell
import org.dropproject.mcp.data.McpContent
import org.dropproject.mcp.data.McpTool
import org.dropproject.mcp.data.McpToolCallResult
import org.dropproject.mcp.services.McpService
import org.dropproject.services.GitAuthorMatcher
import org.dropproject.services.SubmissionGitService.Companion.SHORT_HASH_LENGTH
import org.dropproject.services.SubmissionGitService.CommitContribution
import java.security.Principal
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Command to retrieve the commit history of the repository behind a git submission, up to the submitted commit, with
 * the author of each commit and the lines it added and removed, summarized by author.
 *
 * @property submissionId identifies the submission whose repository is analysed
 * @property since if not null, only the commits from this date on are considered
 * @property until if not null, only the commits up to this date are considered
 * @property paths are the path prefixes whose changes are counted
 * @property includeMerges tells whether merge commits are considered
 */
data class GetSubmissionGitHistory(val submissionId: Long, val since: Date?, val until: Date?,
                                   val paths: List<String>, val includeMerges: Boolean) : ToolCommand {

    private data class AuthorSummary(val identity: GitIdentity, val match: GitAuthorMatcher.Match,
                                     val commits: List<CommitContribution>) {
        val linesAdded get() = commits.sumOf { it.linesAdded }
        val linesRemoved get() = commits.sumOf { it.linesRemoved }
    }

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {
        val (submission, assignment) =
            SubmissionGitTools.authorizedGitSubmission(service, principal, submissionId, "git history")

        val history = service.submissionGitService.history(submission, since, until, paths, includeMerges)
        val members = submission.group.authors.sortedBy { it.userId }
        val matcher = GitAuthorMatcher(members)

        val summaries = history.commits
            .groupBy { GitIdentity(it.authorName, it.authorEmail) }
            .map { (identity, commits) -> AuthorSummary(identity, matcher.match(identity.name, identity.email), commits) }
            .sortedByDescending { it.commits.size }
        val matches = summaries.associate { it.identity to it.match }
        val membersWithCommits = summaries.mapNotNull { (it.match as? GitAuthorMatcher.Match.Mapped)?.member?.userId }.toSet()
        val membersWithoutCommits = members.filter { it.userId !in membersWithCommits }

        val text = buildString {
            appendLine("# Git history for Submission $submissionId")
            appendLine()
            appendLine("**Assignment:** ${assignment.id}")
            append(SubmissionGitTools.repositoryHeader(history.gitSubmission, history.analysedCommit, service))
            val end = until?.formatDefault()
                ?: if (history.analysedCommit.isSubmittedCommit) "${history.analysedCommit.date.formatDefault()} (submitted commit)"
                else "${history.analysedCommit.date.formatDefault()} (analysed commit)"
            appendLine("**Period:** ${since?.formatDefault() ?: "(start)"} -> $end")
            appendLine("**Paths:** ${paths.joinToString(", ") { "$it/" }}")
            appendLine("**Merge commits:** ${if (includeMerges) "included" else "excluded"}")
            appendLine()

            append(SubmissionGitTools.warnings(history.analysedCommit))
            append(SubmissionGitTools.groupMembers(members))

            appendLine("## Summary by author")
            appendLine("| Author (git) | Mapped student | Commits | Lines added | Lines removed | First | Last |")
            appendLine("|---|---|---|---|---|---|---|")
            summaries.forEach { summary ->
                appendLine("| ${cell(summary.identity.toString())} | ${SubmissionGitTools.mappedStudent(summary.match)} | " +
                        "${summary.commits.size} | ${summary.linesAdded} | ${summary.linesRemoved} | " +
                        "${day(summary.commits.minOf { it.date })} | ${day(summary.commits.maxOf { it.date })} |")
            }
            membersWithoutCommits.forEach { appendLine("| - | ${it.userId} | 0 | 0 | 0 | - | - |") }
            appendLine()

            appendLine("## Members without commits in the period")
            if (membersWithoutCommits.isEmpty()) {
                appendLine("- (none)")
            }
            membersWithoutCommits.forEach { appendLine("- ${it.userId} - ${it.name}") }
            appendLine()

            append(SubmissionGitTools.unmappedAuthors(matches) { identity ->
                val commits = summaries.first { it.identity == identity }.commits.size
                if (commits == 1) "1 commit" else "$commits commits"
            })

            appendLine("## Commits")
            if (history.commits.size > MAX_LISTED_COMMITS) {
                appendLine("Showing the $MAX_LISTED_COMMITS most recent of ${history.commits.size} commits. The " +
                        "summary above covers all of them.")
                appendLine()
            }
            if (history.commits.isEmpty()) {
                appendLine("No commits changed ${paths.joinToString(", ") { "$it/" }} in the period.")
            } else {
                appendLine("| Hash | Date | Author | Mapped student | +/- | Message |")
                appendLine("|---|---|---|---|---|---|")
                history.commits.take(MAX_LISTED_COMMITS).forEach { commit ->
                    val match = matches.getValue(GitIdentity(commit.authorName, commit.authorEmail))
                    val merge = if (commit.isMerge) " (merge)" else ""
                    appendLine("| ${commit.hash.take(SHORT_HASH_LENGTH)} | ${minute(commit.date)} | " +
                            "${cell(commit.authorName)} | ${SubmissionGitTools.mappedStudent(match)} | " +
                            "+${commit.linesAdded}/-${commit.linesRemoved} | ${cell(commit.message)}$merge |")
                }
            }
        }

        return McpToolCallResult(content = listOf(McpContent(type = "text", text = text)))
    }

    private fun day(date: Date) = SimpleDateFormat("yyyy-MM-dd").format(date)

    private fun minute(date: Date) = SimpleDateFormat("yyyy-MM-dd HH:mm").format(date)

    companion object {

        const val MAX_LISTED_COMMITS = 200

        private const val DEFAULT_PATHS = "src/"

        fun toMcpTool(): McpTool {
            return McpTool(
                name = "get_submission_git_history",
                description = "Retrieve the git commit history of the repository behind a submission, up to the " +
                        "commit that was submitted, with the author and the lines added and removed by each commit. " +
                        "Lets a teacher check how much each group member contributed, for example whether every " +
                        "student made at least one non-trivial commit before the deadline. Only available for " +
                        "assignments whose submission method is GIT, and only to the owner of the assignment and to " +
                        "the teachers it was shared with.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "submissionId" to mapOf(
                            "type" to "number",
                            "description" to "The submission whose repository is analysed. The history ends at the " +
                                    "commit of this submission, not at the current tip of the branch, so that " +
                                    "commits made after the deadline don't count. Commits from other branches " +
                                    "that were never merged into the submitted commit are ignored."
                        ),
                        "since" to mapOf(
                            "type" to "string",
                            "description" to "Only commits from this date on, in ISO-8601 (e.g. '2026-10-15' or " +
                                    "'2026-10-15T23:59', in the server's time zone). Isolates one part of a " +
                                    "multi-part project, e.g. part 2 without the commits of part 1. Commits are " +
                                    "dated by their author date."
                        ),
                        "until" to mapOf(
                            "type" to "string",
                            "description" to "Only commits up to this date, in ISO-8601. A date without time " +
                                    "includes that whole day. By default, every commit up to the submitted one."
                        ),
                        "paths" to mapOf(
                            "type" to "string",
                            "description" to "Comma-separated path prefixes whose changes are counted (default " +
                                    "'$DEFAULT_PATHS'), matched by whole file and folder names. Commits that " +
                                    "change nothing under them are left out, so files like pom.xml or " +
                                    "instructions.md don't count."
                        ),
                        "includeMerges" to mapOf(
                            "type" to "boolean",
                            "description" to "By default false: merge commits are left out, since the lines they " +
                                    "bring were already counted in the commits that were merged. If true, they " +
                                    "are listed and count the lines changed relative to their first parent."
                        )
                    ),
                    "required" to listOf("submissionId")
                )
            )
        }

        fun from(arguments: Map<String, Any>): GetSubmissionGitHistory {
            val submissionId = (arguments["submissionId"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("submissionId is required and must be a number")
            val args = AssignmentArguments(arguments)
            val since = SubmissionGitTools.date("since", args.string("since"), endOfDay = false)
            val until = SubmissionGitTools.date("until", args.string("until"), endOfDay = true)
            if (since != null && until != null && since.after(until)) {
                throw IllegalArgumentException("since must not be later than until")
            }
            return GetSubmissionGitHistory(
                submissionId = submissionId,
                since = since,
                until = until,
                paths = SubmissionGitTools.paths(args.string("paths"), DEFAULT_PATHS),
                includeMerges = args.boolean("includeMerges") ?: false
            )
        }
    }
}
