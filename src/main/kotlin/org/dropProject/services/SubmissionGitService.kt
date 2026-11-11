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
package org.dropproject.services

import org.apache.commons.io.FileUtils
import org.dropproject.config.DropProjectProperties
import org.dropproject.dao.GitSubmission
import org.dropproject.dao.Submission
import org.dropproject.extensions.formatDefault
import org.dropproject.repository.GitSubmissionRepository
import org.dropproject.repository.SubmissionGitInfoRepository
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilterGroup
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.util.Date

/**
 * Analyses the git repository behind a git submission, as it was when the submission was made: who made each commit
 * and how many lines it changed, and who wrote each line of the submitted code. This is what lets a teacher check how
 * much each member of a group contributed.
 *
 * Drop Project's clone of a group's repository reflects its last pull, which may have happened after the submission,
 * so the analysis always starts at the submitted commit and only follows its ancestors. Commits pushed afterwards, or
 * to other branches, are never considered.
 */
@Service
class SubmissionGitService(
    val gitSubmissionRepository: GitSubmissionRepository,
    val submissionGitInfoRepository: SubmissionGitInfoRepository,
    val gitClient: GitClient,
    val dropProjectProperties: DropProjectProperties
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * The commit at which a submission is analysed.
     *
     * @property commit is the submitted commit or, when that one is no longer available, the closest replacement
     * @property submittedCommitHash is the commit registered for the submission, if any
     * @property isSubmittedCommit tells whether [commit] is the submitted commit
     * @property warnings explain why [commit] is not the submitted commit, when it isn't
     */
    data class AnalysedCommit(val commit: RevCommit, val submittedCommitHash: String?,
                              val isSubmittedCommit: Boolean, val warnings: List<String>) {
        val date: Date get() = commit.authorIdent.`when`
    }

    /**
     * A commit, with the lines it added and removed in the analysed paths, relative to its first parent.
     */
    data class CommitContribution(val hash: String, val authorName: String, val authorEmail: String,
                                  val date: Date, val message: String, val linesAdded: Int, val linesRemoved: Int,
                                  val isMerge: Boolean)

    data class GitHistory(val gitSubmission: GitSubmission, val analysedCommit: AnalysedCommit,
                          val commits: List<CommitContribution>)

    data class BlameLine(val number: Int, val commitHash: String, val authorName: String, val authorEmail: String,
                         val content: String)

    data class FileBlame(val path: String, val lines: List<BlameLine>)

    data class GitBlame(val gitSubmission: GitSubmission, val analysedCommit: AnalysedCommit,
                        val files: List<FileBlame>)

    /**
     * Returns the commits that led to [submission] and changed some file under [paths], most recent first.
     *
     * Commits are dated by their author date, which survives rebases and amends.
     *
     * @param since if not null, only commits from this date on are returned
     * @param until if not null, only commits up to this date are returned
     * @param paths are path prefixes, matched by whole file and folder names (e.g. "src" matches "src/Main.java" but
     * not "srcs/Main.java")
     * @param includeMerges if false, merge commits are left out, since the lines they bring were already counted in
     * the commits that were merged. If true, they count the lines they changed relative to their first parent
     */
    fun history(submission: Submission, since: Date?, until: Date?, paths: List<String>,
                includeMerges: Boolean): GitHistory {
        return withAnalysedCommit(submission, until) { repository, gitSubmission, analysedCommit ->
            val commits = RevWalk(repository).use { walk ->
                walk.markStart(walk.parseCommit(analysedCommit.commit))
                walk.toList()
                    .filter { includeMerges || it.parentCount <= 1 }
                    .filter { commit ->
                        val date = commit.authorIdent.`when`
                        (since == null || !date.before(since)) && (until == null || !date.after(until))
                    }
                    .mapNotNull { commit -> contributionOf(repository, walk, commit, paths) }
                    .sortedByDescending { it.date }
            }
            GitHistory(gitSubmission, analysedCommit, commits)
        }
    }

    /**
     * Returns, for each text file under [paths] at the commit of [submission], the commit and author of each line.
     *
     * @param ignoreWhitespace if true, a line whose only change was in whitespace (e.g. reformatting) keeps its
     * original author
     */
    fun blame(submission: Submission, paths: List<String>, ignoreWhitespace: Boolean): GitBlame {
        return withAnalysedCommit(submission, until = null) { repository, gitSubmission, analysedCommit ->
            val comparator = if (ignoreWhitespace) RawTextComparator.WS_IGNORE_ALL else RawTextComparator.DEFAULT
            val files = textFilesAt(repository, analysedCommit.commit, paths).map { path ->
                val result = Git(repository).blame()
                    .setStartCommit(analysedCommit.commit)
                    .setFilePath(path)
                    .setFollowFileRenames(true)
                    .setTextComparator(comparator)
                    .call()
                    ?: throw IllegalStateException("Couldn't compute the git blame of $path")
                val contents = result.resultContents
                val lines = (0 until contents.size()).map { i ->
                    val author = result.getSourceAuthor(i)
                    BlameLine(number = i + 1, commitHash = result.getSourceCommit(i)?.name ?: "",
                        authorName = author?.name ?: "", authorEmail = author?.emailAddress ?: "",
                        content = contents.getString(i))
                }
                FileBlame(path, lines)
            }
            GitBlame(gitSubmission, analysedCommit, files)
        }
    }

    /**
     * Opens the repository of [submission] and finds the commit to analyse, cleaning up whatever had to be created
     * for that once [block] returns.
     *
     * The submitted commit is normally in Drop Project's clone of the repository. If the clone was removed, the
     * repository is cloned again into a temporary folder; if the commit is missing from it, the repository is fetched,
     * in case the clone is missing objects. If even then the commit isn't found (e.g. the group rewrote its history
     * with a force push), the analysis falls back to the most recent commit of the main branch that is not later than
     * [until] or, if that's null, than the submission date.
     */
    private fun <T> withAnalysedCommit(submission: Submission, until: Date?,
                                       block: (Repository, GitSubmission, AnalysedCommit) -> T): T {
        val gitSubmissionId = submission.gitSubmissionId
            ?: throw IllegalArgumentException("Submission ${submission.id} was not made through git")
        val gitSubmission = gitSubmissionRepository.findById(gitSubmissionId).orElse(null)
            ?: throw IllegalArgumentException("The git connection of submission ${submission.id} no longer exists")
        val privateKey = gitSubmission.gitRepositoryPrivKey?.toByteArray()
        val submittedCommitHash = submissionGitInfoRepository.getBySubmissionId(submission.id)?.gitCommitHash

        val storedClone = File(dropProjectProperties.storage.gitLocation, gitSubmission.getFolderRelativeToStorageRoot())
        var temporaryClone: File? = null
        try {
            val repositoryFolder = if (File(storedClone, ".git").isDirectory) {
                storedClone
            } else {
                LOG.warn("The clone of ${gitSubmission.gitRepositoryUrl} is missing from ${storedClone}, " +
                        "cloning it again into a temporary folder")
                Files.createTempDirectory("dp-git-analysis-").toFile().also { folder ->
                    temporaryClone = folder
                    remoteOperation(gitSubmission) { gitClient.clone(gitSubmission.gitRepositoryUrl, folder, privateKey).close() }
                }
            }

            return Git.open(repositoryFolder).use { git ->
                val repository = git.repository
                var commit = submittedCommitHash?.let { findCommit(repository, it) }
                if (commit == null && submittedCommitHash != null && temporaryClone == null && privateKey != null) {
                    remoteOperation(gitSubmission) { gitClient.fetch(repositoryFolder, privateKey).close() }
                    commit = findCommit(repository, submittedCommitHash)
                }

                val analysedCommit = if (commit != null) {
                    AnalysedCommit(commit, submittedCommitHash, isSubmittedCommit = true, warnings = emptyList())
                } else {
                    replacementCommit(repository, submittedCommitHash, until ?: submission.submissionDate)
                }

                block(repository, gitSubmission, analysedCommit)
            }
        } finally {
            temporaryClone?.let { FileUtils.deleteQuietly(it) }
        }
    }

    private fun replacementCommit(repository: Repository, submittedCommitHash: String?, limit: Date): AnalysedCommit {
        val head = BRANCH_REFS.firstNotNullOfOrNull { repository.resolve(it) }
            ?: throw IllegalStateException("The repository has no main or master branch")

        val replacement = RevWalk(repository).use { walk ->
            walk.markStart(walk.parseCommit(head))
            walk.filter { !it.authorIdent.`when`.after(limit) }.maxByOrNull { it.authorIdent.`when` }
        } ?: throw IllegalStateException("The repository has no commits up to ${limit.formatDefault()}")

        val reason = if (submittedCommitHash == null) {
            "No commit was registered for this submission"
        } else {
            "The submitted commit ${submittedCommitHash.take(SHORT_HASH_LENGTH)} no longer exists in the repository, " +
                    "probably because the group rewrote its history with a force push"
        }
        val warning = "$reason. Using the history of the main branch up to ${limit.formatDefault()} instead, " +
                "starting at commit ${replacement.name.take(SHORT_HASH_LENGTH)}, which may not be the code that " +
                "was submitted."

        return AnalysedCommit(replacement, submittedCommitHash, isSubmittedCommit = false, warnings = listOf(warning))
    }

    private fun findCommit(repository: Repository, hash: String): RevCommit? {
        val objectId = try {
            ObjectId.fromString(hash)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return try {
            RevWalk(repository).use { it.parseCommit(objectId) }
        } catch (e: MissingObjectException) {
            null
        }
    }

    /**
     * Runs an operation that reaches the group's git host, translating its failure into a message that says why the
     * repository couldn't be read.
     */
    private fun remoteOperation(gitSubmission: GitSubmission, operation: () -> Unit) {
        try {
            operation()
        } catch (e: Exception) {
            LOG.warn("Couldn't reach ${gitSubmission.gitRepositoryUrl}: ${e.message}")
            throw IllegalStateException(unreachableRepositoryMessage(gitSubmission.gitRepositoryUrl, e), e)
        }
    }

    private fun contributionOf(repository: Repository, walk: RevWalk, commit: RevCommit,
                               paths: List<String>): CommitContribution? {
        var added = 0
        var removed = 0
        var changedFiles = 0

        repository.newObjectReader().use { reader ->
            DiffFormatter(DisabledOutputStream.INSTANCE).use { formatter ->
                formatter.setRepository(repository)
                formatter.setDiffComparator(RawTextComparator.DEFAULT)
                formatter.isDetectRenames = true
                formatter.pathFilter = PathFilterGroup.createFromStrings(paths)

                val oldTree = if (commit.parentCount == 0) {
                    EmptyTreeIterator()
                } else {
                    CanonicalTreeParser(null, reader, walk.parseCommit(commit.getParent(0)).tree)
                }
                val newTree = CanonicalTreeParser(null, reader, commit.tree)

                for (entry in formatter.scan(oldTree, newTree)) {
                    changedFiles++
                    for (edit in formatter.toFileHeader(entry).toEditList()) {
                        when (edit.type) {
                            Edit.Type.INSERT -> added += edit.lengthB
                            Edit.Type.DELETE -> removed += edit.lengthA
                            Edit.Type.REPLACE -> {
                                added += edit.lengthB
                                removed += edit.lengthA
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        if (changedFiles == 0) {
            return null
        }

        return CommitContribution(hash = commit.name, authorName = commit.authorIdent.name,
            authorEmail = commit.authorIdent.emailAddress, date = commit.authorIdent.`when`,
            message = commit.shortMessage, linesAdded = added, linesRemoved = removed,
            isMerge = commit.parentCount > 1)
    }

    private fun textFilesAt(repository: Repository, commit: RevCommit, paths: List<String>): List<String> {
        val files = mutableListOf<String>()
        TreeWalk(repository).use { treeWalk ->
            treeWalk.addTree(commit.tree)
            treeWalk.isRecursive = true
            treeWalk.filter = PathFilterGroup.createFromStrings(paths)
            while (treeWalk.next()) {
                if (treeWalk.getFileMode(0) == FileMode.GITLINK) {
                    continue
                }
                val start = repository.open(treeWalk.getObjectId(0)).openStream().use { it.readNBytes(BINARY_CHECK_BYTES) }
                if (!RawText.isBinary(start)) {
                    files.add(treeWalk.pathString)
                }
            }
        }
        return files
    }

    companion object {
        const val SHORT_HASH_LENGTH = 7

        // the same number of bytes that git itself looks at to tell binary files apart
        private const val BINARY_CHECK_BYTES = 8000

        private val BRANCH_REFS = listOf("refs/remotes/origin/main", "refs/remotes/origin/master",
            "refs/heads/main", "refs/heads/master")

        /**
         * Says why the repository at [url] couldn't be read, from the error that the attempt raised.
         *
         * GitHub removes the deploy keys of a repository when it is deleted, so a rejected key may also mean that the
         * repository is gone: only the git host can tell them apart, and it won't for a private repository.
         */
        fun unreachableRepositoryMessage(url: String, e: Throwable): String {
            val messages = generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
            return when {
                "auth fail" in messages || "permission denied" in messages || "publickey" in messages ->
                    "The git host rejected Drop Project's deploy key for $url: either the group removed it from " +
                            "the repository, or the repository was deleted (which also removes its deploy keys)."
                "not found" in messages || "does not exist" in messages || "not appear to be a git repository" in messages ->
                    "The repository $url no longer exists: it was deleted, renamed or moved."
                else ->
                    "Couldn't reach the repository $url: ${e.message}"
            }
        }
    }
}
