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
package org.dropproject.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.io.FileUtils
import org.dropproject.AssignmentFixtures
import org.dropproject.DropProjectIntegrationTest
import org.dropproject.FakeGitClient
import org.dropproject.config.DropProjectProperties
import org.dropproject.dao.Assignment
import org.dropproject.dao.Author
import org.dropproject.dao.GitSubmission
import org.dropproject.dao.ProjectGroup
import org.dropproject.dao.Submission
import org.dropproject.dao.SubmissionGitInfo
import org.dropproject.dao.SubmissionStatus
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.AuthorRepository
import org.dropproject.repository.GitSubmissionRepository
import org.dropproject.repository.ProjectGroupRepository
import org.dropproject.repository.SubmissionGitInfoRepository
import org.dropproject.repository.SubmissionRepository
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

@DropProjectIntegrationTest
@Tag("integration")
class McpSubmissionGitToolsTests : ApiTestSupport {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var assignmentFixtures: AssignmentFixtures

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var projectGroupRepository: ProjectGroupRepository

    @Autowired
    lateinit var authorRepository: AuthorRepository

    @Autowired
    lateinit var gitSubmissionRepository: GitSubmissionRepository

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var submissionGitInfoRepository: SubmissionGitInfoRepository

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    @Autowired
    lateinit var fakeGitClient: FakeGitClient

    private val foldersToCleanup = mutableListOf<File>()

    private lateinit var group: ProjectGroup

    private val assignmentId = "gitProj"

    private val teacherTemplate = PersonIdentSpec("Teacher Template", "teacher@ulusofona.pt")
    private val tiago = PersonIdentSpec("tc", "a22504146@alunos.ulusofona.pt")
    private val miguel = PersonIdentSpec("Miguel Belo Duarte", "mbd@gmail.com")

    data class PersonIdentSpec(val name: String, val email: String)

    /**
     * The commits of the sample repository, in the order they were made.
     */
    private data class SampleRepository(val templateCommit: String, val tiagoFirstCommit: String,
                                        val miguelPomCommit: String, val miguelBranchCommit: String,
                                        val tiagoSecondCommit: String, val miguelLateCommit: String)

    @BeforeEach
    fun setup() {
        assignmentFixtures.createDefaultAssignment()
        assignmentRepository.save(Assignment(id = assignmentId, name = "Git project",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.GIT, active = true, gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testJavaProj"))

        group = projectGroupRepository.save(ProjectGroup())
        authorRepository.save(Author(name = "Tiago Costa", number = "a22504146", group = group))
        authorRepository.save(Author(name = "Miguel Belo Duarte", number = "a22506369", group = group))
        authorRepository.save(Author(name = "Ana Silva", number = "a22500001", group = group))
    }

    @AfterEach
    fun cleanup() {
        foldersToCleanup.forEach { FileUtils.deleteQuietly(it) }
    }

    private fun date(text: String): Date =
        Date.from(LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant())

    private fun Git.commit(author: PersonIdentSpec, date: String, message: String, files: Map<String, String>): String {
        val folder = repository.workTree
        files.forEach { (path, content) ->
            val file = File(folder, path)
            file.parentFile.mkdirs()
            file.writeText(content)
        }
        add().addFilepattern(".").call()
        val ident = PersonIdent(author.name, author.email, date(date), TimeZone.getDefault())
        return commit().setMessage(message).setAuthor(ident).setCommitter(ident).call().name
    }

    /**
     * Fills [folder] with a repository where:
     * - the teacher's template adds Main.java (3 lines), Util.java (1 line) and pom.xml
     * - tiago adds 2 lines to Main.java
     * - miguel only changes pom.xml
     * - miguel adds Other.java on a branch that is never merged
     * - tiago replaces a line of Main.java (the submitted commit)
     * - miguel adds a line to Main.java after the submission
     */
    private fun createSampleRepository(folder: File): SampleRepository {
        folder.mkdirs()
        return Git.init().setDirectory(folder).call().use { git ->
            val template = git.commit(teacherTemplate, "2026-10-01T10:00", "template", mapOf(
                "src/Main.java" to "public class Main {\n    // TODO\n}\n",
                "src/Util.java" to "class Util {}\n",
                "pom.xml" to "<project/>\n"))
            val tiagoFirst = git.commit(tiago, "2026-10-05T10:00", "fields", mapOf(
                "src/Main.java" to "public class Main {\n    // TODO\n    int a = 1;\n    int b = 2;\n}\n"))
            val miguelPom = git.commit(miguel, "2026-10-06T10:00", "pom", mapOf(
                "pom.xml" to "<project></project>\n"))

            git.branchCreate().setName("experiment").call()
            git.checkout().setName("experiment").call()
            val miguelBranch = git.commit(miguel, "2026-10-06T11:00", "experiment", mapOf(
                "src/Other.java" to "class Other {}\n"))
            git.checkout().setName("master").call()

            val tiagoSecond = git.commit(tiago, "2026-10-07T10:00", "done | finally", mapOf(
                "src/Main.java" to "public class Main {\n    // done\n    int a = 1;\n    int b = 2;\n}\n"))
            val miguelLate = git.commit(miguel, "2026-10-08T10:00", "late", mapOf(
                "src/Main.java" to "public class Main {\n    // done\n    int a = 1;\n    int b = 2;\n    int c = 3;\n}\n"))

            SampleRepository(template, tiagoFirst, miguelPom, miguelBranch, tiagoSecond, miguelLate)
        }
    }

    private fun saveGitSubmission(privateKey: String? = null): GitSubmission {
        val gitSubmission = GitSubmission(assignmentId = assignmentId, submitterUserId = "a22504146",
            gitRepositoryUrl = "git@github.com:tiagocosta/projeto-${System.nanoTime()}.git", group = group)
        gitSubmission.connected = true
        gitSubmission.gitRepositoryPrivKey = privateKey
        return gitSubmissionRepository.save(gitSubmission)
    }

    private fun storedCloneOf(gitSubmission: GitSubmission) =
        File(dropProjectProperties.storage.gitLocation, gitSubmission.getFolderRelativeToStorageRoot())
            .also { foldersToCleanup.add(it) }

    private fun saveSubmission(gitSubmission: GitSubmission, commitHash: String?,
                               submissionDate: Date = date("2026-10-07T12:00"),
                               assignmentId: String = this.assignmentId): Long {
        val submission = Submission(gitSubmissionId = gitSubmission.id, submissionDate = submissionDate,
            submitterUserId = "a22504146", status = SubmissionStatus.VALIDATED.code, statusDate = submissionDate,
            assignmentId = assignmentId, assignmentGitHash = "somehash")
        submission.group = group
        submissionRepository.save(submission)
        commitHash?.let { submissionGitInfoRepository.save(SubmissionGitInfo(submissionId = submission.id, gitCommitHash = it)) }
        return submission.id
    }

    /**
     * A submission of the sample repository at tiago's second commit, after which Drop Project pulled miguel's late
     * commit.
     */
    private fun sampleSubmission(): Pair<Long, SampleRepository> {
        val gitSubmission = saveGitSubmission()
        val repository = createSampleRepository(storedCloneOf(gitSubmission))
        return Pair(saveSubmission(gitSubmission, repository.tiagoSecondCommit), repository)
    }

    private fun bearer(username: String, role: String = "ROLE_TEACHER") =
        "Bearer " + generateToken(username, mutableListOf(SimpleGrantedAuthority(role)), mvc)

    /**
     * Calls a tool and returns the text of its result or, if it failed, the message of the error.
     */
    private fun callTool(toolName: String, arguments: String, authHeader: String = bearer("teacher1")): String {
        val body = mvc.perform(
            post("/mcp/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content("""{"jsonrpc": "2.0", "id": "1", "method": "tools/call",
                            "params": {"name": "$toolName", "arguments": $arguments}}""")
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val json = ObjectMapper().readTree(body)
        return json.path("error").path("message").textValue()
            ?: json.path("result").path("content").get(0).path("text").textValue()
    }

    private fun shortHash(hash: String) = hash.take(7)

    @Test
    fun `the git history ends at the submitted commit and only counts the analysed paths`() {
        val (submissionId, repository) = sampleSubmission()

        val text = callTool("get_submission_git_history", """{"submissionId": $submissionId}""")

        assertThat(text, containsString("# Git history for Submission $submissionId"))
        assertThat(text, containsString("**Assignment:** $assignmentId"))
        assertThat(text, containsString("**Submitted commit:** ${shortHash(repository.tiagoSecondCommit)} (2026-10-07 10:00:00)"))
        assertThat(text, containsString("**Period:** (start) -> 2026-10-07 10:00:00 (submitted commit)"))
        assertThat(text, containsString("**Paths:** src/"))

        // tiago's commits: +2/-0 and +1/-1
        assertThat(text, containsString("| tc <a22504146@alunos.ulusofona.pt> | a22504146 | 2 | 3 | 1 | 2026-10-05 | 2026-10-07 |"))
        assertThat(text, containsString("| Teacher Template <teacher@ulusofona.pt> | - | 1 | 4 | 0 | 2026-10-01 | 2026-10-01 |"))
        // miguel only changed the pom, on a branch that was never merged, and after the submission
        assertThat(text, containsString("| - | a22506369 | 0 | 0 | 0 | - | - |"))
        assertThat(text, containsString("## Members without commits in the period\n- a22500001 - Ana Silva\n- a22506369 - Miguel Belo Duarte\n"))
        assertThat(text, containsString("- Teacher Template <teacher@ulusofona.pt> (1 commit)"))

        assertThat(text, containsString("| ${shortHash(repository.tiagoSecondCommit)} | 2026-10-07 10:00 | tc | a22504146 | +1/-1 | done \\| finally |"))
        assertThat(text, containsString("| ${shortHash(repository.tiagoFirstCommit)} | 2026-10-05 10:00 | tc | a22504146 | +2/-0 | fields |"))
        assertThat(text, containsString("| ${shortHash(repository.templateCommit)} |"))
        listOf(repository.miguelPomCommit, repository.miguelBranchCommit, repository.miguelLateCommit).forEach {
            assertThat(text, not(containsString(shortHash(it))))
        }
    }

    @Test
    fun `the git history can be limited to a period and to other paths`() {
        val (submissionId, repository) = sampleSubmission()

        val text = callTool("get_submission_git_history",
            """{"submissionId": $submissionId, "since": "2026-10-02", "until": "2026-10-06", "paths": "src/, pom.xml"}""")

        assertThat(text, containsString("**Paths:** src/, pom.xml/"))
        assertThat(text, containsString("| ${shortHash(repository.tiagoFirstCommit)} |"))
        assertThat(text, containsString("| ${shortHash(repository.miguelPomCommit)} | 2026-10-06 10:00 | Miguel Belo Duarte | a22506369 | +1/-1 | pom |"))
        // the submitted commit is still named in the header, but it is not listed
        assertThat(text, not(containsString("| ${shortHash(repository.templateCommit)} |")))
        assertThat(text, not(containsString("| ${shortHash(repository.tiagoSecondCommit)} |")))
        assertThat(text, containsString("## Unmapped git authors\n- (none)"))
    }

    @Test
    fun `the git history lists only the most recent commits but summarizes all of them`() {
        val gitSubmission = saveGitSubmission()
        val folder = storedCloneOf(gitSubmission)
        folder.mkdirs()
        val lastCommit = Git.init().setDirectory(folder).call().use { git ->
            (1..205).map { i ->
                git.commit(tiago, LocalDateTime.parse("2026-10-01T10:00").plusMinutes(i.toLong()).toString(),
                    "commit $i", mapOf("src/Main.java" to "line $i\n"))
            }.last()
        }
        val submissionId = saveSubmission(gitSubmission, lastCommit, submissionDate = date("2026-10-02T10:00"))

        val text = callTool("get_submission_git_history", """{"submissionId": $submissionId}""")

        assertThat(text, containsString("Showing the 200 most recent of 205 commits"))
        assertThat(text, containsString("| a22504146 | 205 |"))
        assertThat(text, containsString("| commit 205 |"))
        assertThat(text, not(containsString("| commit 5 |")))
    }

    @Test
    fun `merge commits are left out of the git history unless they are asked for`() {
        val gitSubmission = saveGitSubmission()
        val folder = storedCloneOf(gitSubmission)
        folder.mkdirs()
        val mergeCommit = Git.init().setDirectory(folder).call().use { git ->
            git.commit(tiago, "2026-10-01T10:00", "base", mapOf("src/Main.java" to "class Main {}\n"))
            git.branchCreate().setName("feature").call()
            git.checkout().setName("feature").call()
            git.commit(miguel, "2026-10-02T10:00", "feature", mapOf("src/A.java" to "class A {}\n"))
            git.checkout().setName("master").call()
            git.commit(tiago, "2026-10-03T10:00", "master", mapOf("src/B.java" to "class B {}\n"))
            git.merge().include(git.repository.resolve("feature")).setMessage("merge feature").call().newHead.name
        }
        val submissionId = saveSubmission(gitSubmission, mergeCommit)

        val withoutMerges = callTool("get_submission_git_history", """{"submissionId": $submissionId}""")
        assertThat(withoutMerges, containsString("**Merge commits:** excluded"))
        assertThat(withoutMerges, containsString("| a22504146 | 2 | 2 | 0 |"))
        assertThat(withoutMerges, containsString("| a22506369 | 1 | 1 | 0 |"))
        assertThat(withoutMerges, not(containsString("(merge)")))

        // relative to its first parent, the merge brings the line of A.java
        val withMerges = callTool("get_submission_git_history", """{"submissionId": $submissionId, "includeMerges": true}""")
        assertThat(withMerges, containsString("| +1/-0 | merge feature (merge) |"))
    }

    @Test
    fun `if the submitted commit no longer exists, the history goes up to the submission date`() {
        val gitSubmission = saveGitSubmission()
        val repository = createSampleRepository(storedCloneOf(gitSubmission))
        val submissionId = saveSubmission(gitSubmission, "0123456789abcdef0123456789abcdef01234567")

        val text = callTool("get_submission_git_history", """{"submissionId": $submissionId}""")

        assertThat(text, containsString("**Submitted commit:** 0123456 (not found)"))
        assertThat(text, containsString("**Analysed commit:** ${shortHash(repository.tiagoSecondCommit)}"))
        assertThat(text, containsString("## Warnings\n- The submitted commit 0123456 no longer exists in the repository"))
        assertThat(text, not(containsString(shortHash(repository.miguelLateCommit))))
    }

    @Test
    fun `a submitted commit missing from the clone is fetched from the repository`() {
        val remoteFolder = Files.createTempDirectory("dp-remote-").toFile().also { foldersToCleanup.add(it) }
        val repository = createSampleRepository(remoteFolder)

        // a clone that doesn't have the submitted commit (an empty one, here), whose origin has it
        val gitSubmission = saveGitSubmission(privateKey = "dummy key")
        val storedClone = storedCloneOf(gitSubmission)
        storedClone.mkdirs()
        Git.init().setDirectory(storedClone).call().use { git ->
            git.repository.config.apply {
                setString("remote", "origin", "url", remoteFolder.toURI().toString())
                setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
                save()
            }
        }
        val submissionId = saveSubmission(gitSubmission, repository.tiagoSecondCommit)

        val text = callTool("get_submission_git_history", """{"submissionId": $submissionId}""")

        assertThat(text, containsString("**Submitted commit:** ${shortHash(repository.tiagoSecondCommit)}"))
        assertThat(text, not(containsString("## Warnings")))
        assertThat(text, containsString("| a22504146 | 2 | 3 | 1 |"))
    }

    @Test
    fun `a clone that was removed is cloned again from the repository`() {
        val remoteFolder = Files.createTempDirectory("dp-remote-").toFile().also { foldersToCleanup.add(it) }
        val repository = createSampleRepository(remoteFolder)
        val gitSubmission = saveGitSubmission(privateKey = "dummy key")
        fakeGitClient.registerRemote(gitSubmission.gitRepositoryUrl, remoteFolder)
        val submissionId = saveSubmission(gitSubmission, repository.tiagoSecondCommit)

        val text = callTool("get_submission_git_blame", """{"submissionId": $submissionId, "paths": "src/"}""")

        assertThat(text, containsString("**Submitted commit:** ${shortHash(repository.tiagoSecondCommit)}"))
        assertThat(text, containsString("| a22504146 | 3 | 50.0% |"))
    }

    @Test
    fun `the git blame counts the lines of each author at the submitted commit`() {
        val (submissionId, repository) = sampleSubmission()

        val text = callTool("get_submission_git_blame", """{"submissionId": $submissionId, "paths": "src/"}""")

        assertThat(text, containsString("# Git blame for Submission $submissionId"))
        assertThat(text, containsString("**Submitted commit:** ${shortHash(repository.tiagoSecondCommit)}"))
        // Main.java: the template's first and last lines, and tiago's 3 lines. Util.java: the template's line
        assertThat(text, containsString("| a22500001 | 0 | 0.0% |\n| a22504146 | 3 | 50.0% |\n| a22506369 | 0 | 0.0% |\n" +
                "| Teacher Template <teacher@ulusofona.pt> (unmapped) | 3 | 50.0% |\n| **Total** | 6 | |"))
        assertThat(text, containsString("### src/Main.java (5 lines)\n| Student | Lines | % |\n|---|---|---|\n" +
                "| a22504146 | 3 | 60.0% |\n| Teacher Template <teacher@ulusofona.pt> (unmapped) | 2 | 40.0% |"))
        assertThat(text, containsString("### src/Util.java (1 lines)"))
        assertThat(text, containsString("- Teacher Template <teacher@ulusofona.pt> (3 lines)"))
    }

    @Test
    fun `the git blame of a single file can list the author of each line`() {
        val (submissionId, repository) = sampleSubmission()

        val text = callTool("get_submission_git_blame",
            """{"submissionId": $submissionId, "paths": "src/Main.java", "perLine": true}""")

        val template = shortHash(repository.templateCommit)
        val unmapped = "Teacher Template <teacher@ulusofona.pt> (unmapped)"
        assertThat(text, containsString("## src/Main.java\n```\n" +
                "1  $template  $unmapped  public class Main {\n" +
                "2  ${shortHash(repository.tiagoSecondCommit)}  ${"a22504146".padEnd(unmapped.length)}      // done\n" +
                "3  ${shortHash(repository.tiagoFirstCommit)}  ${"a22504146".padEnd(unmapped.length)}      int a = 1;\n"))
    }

    @Test
    fun `try to get the git blame of each line of several files`() {
        val (submissionId, _) = sampleSubmission()

        assertThat(callTool("get_submission_git_blame", """{"submissionId": $submissionId, "paths": "src/", "perLine": true}"""),
            containsString("perLine needs paths to name a single file, but 'src' matches 2 text files"))
        assertThat(callTool("get_submission_git_blame", """{"submissionId": $submissionId, "perLine": true}"""),
            containsString("perLine needs paths to name a single file"))
    }

    @Test
    fun `try to get the git history of an upload submission`() {
        val gitSubmission = saveGitSubmission()
        val submissionId = saveSubmission(gitSubmission, null, assignmentId = "testJavaProj")

        assertThat(callTool("get_submission_git_history", """{"submissionId": $submissionId}"""),
            containsString("Submission method of assignment testJavaProj is UPLOAD; git history is only available for GIT submissions"))
        assertThat(callTool("get_submission_git_blame", """{"submissionId": $submissionId}"""),
            containsString("Submission method of assignment testJavaProj is UPLOAD; git blame is only available for GIT submissions"))
    }

    @Test
    fun `try to get the git history with a student token or as another teacher`() {
        val (submissionId, _) = sampleSubmission()

        assertThat(callTool("get_submission_git_history", """{"submissionId": $submissionId}""",
            bearer("a22504146", role = "ROLE_STUDENT")),
            containsString("Only teachers can see the git history of a submission"))
        assertThat(callTool("get_submission_git_blame", """{"submissionId": $submissionId}""", bearer("teacher2")),
            containsString("is only available to its owner"))
    }

    @Test
    fun `try to get the git history with an invalid date`() {
        val (submissionId, _) = sampleSubmission()

        assertThat(callTool("get_submission_git_history", """{"submissionId": $submissionId, "since": "yesterday"}"""),
            containsString("since must be a date in ISO-8601 format"))
    }
}
