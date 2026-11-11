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

import org.dropproject.mcp.commands.SubmissionGitTools.GitIdentity
import org.dropproject.mcp.commands.SubmissionGitTools.cell
import org.dropproject.mcp.commands.SubmissionGitTools.percentage
import org.dropproject.mcp.data.McpContent
import org.dropproject.mcp.data.McpTool
import org.dropproject.mcp.data.McpToolCallResult
import org.dropproject.mcp.services.McpService
import org.dropproject.services.GitAuthorMatcher
import org.dropproject.services.SubmissionGitService.BlameLine
import org.dropproject.services.SubmissionGitService.Companion.SHORT_HASH_LENGTH
import org.dropproject.services.SubmissionGitService.FileBlame
import java.security.Principal

/**
 * Command to retrieve, for the files of the repository behind a git submission at the submitted commit, how many
 * lines are attributed to each author (git blame) or, for a single file, the author of each line.
 *
 * @property submissionId identifies the submission whose repository is analysed
 * @property paths are the path prefixes of the files to analyse
 * @property perLine tells whether the author of each line is returned, instead of just the totals
 * @property ignoreWhitespace tells whether lines that only changed in whitespace keep their original author
 */
data class GetSubmissionGitBlame(val submissionId: Long, val paths: List<String>, val perLine: Boolean,
                                 val ignoreWhitespace: Boolean) : ToolCommand {

    override fun handle(service: McpService, principal: Principal): McpToolCallResult {
        val (submission, assignment) =
            SubmissionGitTools.authorizedGitSubmission(service, principal, submissionId, "git blame")

        val blame = service.submissionGitService.blame(submission, paths, ignoreWhitespace)
        val members = submission.group.authors.sortedBy { it.userId }
        val matcher = GitAuthorMatcher(members)
        val pathsText = paths.joinToString(", ")

        if (perLine && blame.files.size != 1) {
            throw IllegalArgumentException("perLine needs paths to name a single file, but '$pathsText' matches " +
                    "${blame.files.size} text files at the analysed commit")
        }

        val matches = blame.files.asSequence().flatMap { it.lines }
            .map { GitIdentity(it.authorName, it.authorEmail) }
            .distinct()
            .associateWith { matcher.match(it.name, it.email) }

        // the label under which each line is counted: the member it was mapped to or, failing that, its git identity
        fun label(line: BlameLine): String {
            val identity = GitIdentity(line.authorName, line.authorEmail)
            return (matches.getValue(identity) as? GitAuthorMatcher.Match.Mapped)?.member?.userId
                ?: "${identity} (unmapped)"
        }

        val text = buildString {
            appendLine("# Git blame for Submission $submissionId")
            appendLine()
            appendLine("**Assignment:** ${assignment.id}")
            append(SubmissionGitTools.repositoryHeader(blame.gitSubmission, blame.analysedCommit, service))
            appendLine("**Paths:** $pathsText")
            appendLine("**Whitespace changes:** ${if (ignoreWhitespace) "ignored" else "count as changes"}")
            appendLine()

            append(SubmissionGitTools.warnings(blame.analysedCommit))
            append(SubmissionGitTools.groupMembers(members))

            if (blame.files.isEmpty()) {
                appendLine("No text files under $pathsText at the analysed commit.")
                return@buildString
            }

            val allLines = blame.files.flatMap { it.lines }
            appendLine("## Summary by student")
            appendLine("Lines of the files below currently attributed to each author.")
            appendLine()
            appendLine("| Student | Lines | % |")
            appendLine("|---|---|---|")
            val totals = allLines.groupingBy { label(it) }.eachCount()
            val memberRows = members.map { it.userId to (totals[it.userId] ?: 0) }
            val unmappedRows = totals.filterKeys { key -> members.none { it.userId == key } }.toList()
                .sortedByDescending { it.second }
            (memberRows + unmappedRows).forEach { (who, lines) ->
                appendLine("| ${cell(who)} | $lines | ${percentage(lines, allLines.size)} |")
            }
            appendLine("| **Total** | ${allLines.size} | |")
            appendLine()

            if (perLine) {
                appendPerLine(blame.files.single(), ::label)
            } else {
                appendLine("## Files")
                blame.files.forEach { file ->
                    appendLine("### ${file.path} (${file.lines.size} lines)")
                    appendLine("| Student | Lines | % |")
                    appendLine("|---|---|---|")
                    file.lines.groupingBy { label(it) }.eachCount().toList().sortedByDescending { it.second }
                        .forEach { (who, lines) ->
                            appendLine("| ${cell(who)} | $lines | ${percentage(lines, file.lines.size)} |")
                        }
                    appendLine()
                }
            }

            append(SubmissionGitTools.unmappedAuthors(matches) { identity ->
                val lines = allLines.count { GitIdentity(it.authorName, it.authorEmail) == identity }
                if (lines == 1) "1 line" else "$lines lines"
            })
        }

        return McpToolCallResult(content = listOf(McpContent(type = "text", text = text)))
    }

    private fun StringBuilder.appendPerLine(file: FileBlame, label: (BlameLine) -> String) {
        appendLine("## ${file.path}")
        val numberWidth = file.lines.size.toString().length
        val labelWidth = file.lines.maxOfOrNull { label(it).length } ?: 0
        appendLine("```")
        file.lines.forEach { line ->
            appendLine("${line.number.toString().padStart(numberWidth)}  ${line.commitHash.take(SHORT_HASH_LENGTH)}  " +
                    "${label(line).padEnd(labelWidth)}  ${line.content}")
        }
        appendLine("```")
        appendLine()
    }

    companion object {

        private const val DEFAULT_PATHS = "src/main/"

        fun toMcpTool(): McpTool {
            return McpTool(
                name = "get_submission_git_blame",
                description = "Retrieve, for the files of a submission's repository at the submitted commit, how " +
                        "many lines are currently attributed to each author (git blame). Complements " +
                        "get_submission_git_history: commits show activity, blame shows whose code is still in the " +
                        "submission. Same availability and permissions as get_submission_git_history.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "submissionId" to mapOf(
                            "type" to "number",
                            "description" to "The submission to analyse, at its submitted commit"
                        ),
                        "paths" to mapOf(
                            "type" to "string",
                            "description" to "Comma-separated path prefixes of the files to analyse (default " +
                                    "'$DEFAULT_PATHS'), matched by whole file and folder names. Binary files are " +
                                    "skipped."
                        ),
                        "perLine" to mapOf(
                            "type" to "boolean",
                            "description" to "By default false: only the totals per file and per author. If true, " +
                                    "returns the commit and author of each line, in which case paths must name a " +
                                    "single file, to keep the response small."
                        ),
                        "ignoreWhitespace" to mapOf(
                            "type" to "boolean",
                            "description" to "By default true (like git blame -w): reformatting code doesn't " +
                                    "change the author of its lines."
                        )
                    ),
                    "required" to listOf("submissionId")
                )
            )
        }

        fun from(arguments: Map<String, Any>): GetSubmissionGitBlame {
            val submissionId = (arguments["submissionId"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("submissionId is required and must be a number")
            val args = AssignmentArguments(arguments)
            val perLine = args.boolean("perLine") ?: false
            if (perLine && args.string("paths") == null) {
                throw IllegalArgumentException("perLine needs paths to name a single file, " +
                        "e.g. 'src/main/java/pt/ulusofona/lp2/Main.java'")
            }
            val paths = SubmissionGitTools.paths(args.string("paths"), DEFAULT_PATHS)
            if (perLine && paths.size > 1) {
                throw IllegalArgumentException("perLine needs paths to name a single file, but it has ${paths.size}")
            }
            return GetSubmissionGitBlame(
                submissionId = submissionId,
                paths = paths,
                perLine = perLine,
                ignoreWhitespace = args.boolean("ignoreWhitespace") ?: true
            )
        }
    }
}
