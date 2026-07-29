/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 Pedro Alves
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
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import java.io.File
import java.nio.file.Files


@RunWith(SpringRunner::class)
@ActiveProfiles("test")
class TestGitClient {

    val gitClient = GitClient()

    private val foldersToCleanup = mutableListOf<File>()

    @After
    fun cleanup() {
        foldersToCleanup.forEach { FileUtils.deleteQuietly(it) }
        foldersToCleanup.clear()
    }

    @Test
    fun testCheckValidGithubURL() {

        assertTrue(gitClient.checkValidSSHGithubURL("git@github.com:ULHT-LP2-2018-19/paint-episodio-2-completo.git"))
        assertFalse(gitClient.checkValidSSHGithubURL("https://github.com/ULHT-LP2-2018-19/paint-episodio-2-completo.git"))
        assertFalse(gitClient.checkValidSSHGithubURL("https://github.com/ULHT-LP2-2018-19/paint-episodio-2-completo"))
        assertFalse(gitClient.checkValidSSHGithubURL("paint-episodio-2-completo"))

    }

    @Test
    fun testConvertSSHGithubURLtoHttpURL() {

        assertEquals("https://github.com/ULHT-LP2-2018-19/paint-episodio-2-completo",
                gitClient.convertSSHGithubURLtoHttpURL("git@github.com:ULHT-LP2-2018-19/paint-episodio-2-completo.git"))

    }

    @Test
    fun testIsPubliclyReadable() {

        // a repository owned by the drop project organization that is public and is expected to stay public
        assertTrue(gitClient.isPubliclyReadable("git@github.com:drop-project-edu/sampleJavaAssignment.git"))

        // a repository owned by the drop project organization that is private and is expected to stay private
        assertFalse(gitClient.isPubliclyReadable("git@github.com:drop-project-edu/sampleJavaSubmission.git"))

        // a repository that doesn't exist is indistinguishable from a private one, and must not be flagged
        assertFalse(gitClient.isPubliclyReadable("git@github.com:drop-project-edu/inexistent.git"))

        // an url that can't be checked must not be flagged either
        assertFalse(gitClient.isPubliclyReadable("https://github.com/drop-project-edu/sampleJavaAssignment"))
        assertFalse(gitClient.isPubliclyReadable("git://dummy"))
    }

    // creates a local (non-bare) git repository with two commits over the same file, and returns
    // the repository folder together with each commit's hash
    private fun createLocalGitRepoWithTwoCommits(): Triple<File, String, String> {
        val repoFolder = Files.createTempDirectory("dp-test-repo-").toFile()
        foldersToCleanup.add(repoFolder)

        Git.init().setDirectory(repoFolder).call().use { git ->
            val file = File(repoFolder, "file.txt")

            file.writeText("content-A")
            git.add().addFilepattern("file.txt").call()
            val commitA = git.commit().setMessage("commit A").call()

            file.writeText("content-B")
            git.add().addFilepattern("file.txt").call()
            val commitB = git.commit().setMessage("commit B").call()

            return Triple(repoFolder, commitA.name, commitB.name)
        }
    }

    @Test
    fun testCloneRepositoryAtCommit_returnsContentOfTheGivenCommit() {

        val (repoFolder, commitA, commitB) = createLocalGitRepoWithTwoCommits()

        val cloneAtA = gitClient.cloneRepositoryAtCommit(repoFolder, commitA)
        foldersToCleanup.add(cloneAtA)
        assertEquals("content-A", File(cloneAtA, "file.txt").readText())

        val cloneAtB = gitClient.cloneRepositoryAtCommit(repoFolder, commitB)
        foldersToCleanup.add(cloneAtB)
        assertEquals("content-B", File(cloneAtB, "file.txt").readText())
    }

    @Test
    fun testCloneRepositoryAtCommit_nullHashReturnsHead() {

        val (repoFolder, _, _) = createLocalGitRepoWithTwoCommits()

        val cloneAtHead = gitClient.cloneRepositoryAtCommit(repoFolder, null)
        foldersToCleanup.add(cloneAtHead)
        assertEquals("content-B", File(cloneAtHead, "file.txt").readText())
    }

    @Test
    fun testCloneRepositoryAtCommit_doesNotMutateSourceRepository() {

        val (repoFolder, commitA, _) = createLocalGitRepoWithTwoCommits()

        val sourceBranchBefore = Git.open(repoFolder).use { it.repository.branch }
        val sourceContentBefore = File(repoFolder, "file.txt").readText()

        val clone = gitClient.cloneRepositoryAtCommit(repoFolder, commitA)
        foldersToCleanup.add(clone)

        // the clone reflects the older commit...
        assertEquals("content-A", File(clone, "file.txt").readText())

        // ...but the source repository (its branch and working tree) was never touched
        val sourceBranchAfter = Git.open(repoFolder).use { it.repository.branch }
        val sourceContentAfter = File(repoFolder, "file.txt").readText()
        assertEquals(sourceBranchBefore, sourceBranchAfter)
        assertEquals(sourceContentBefore, sourceContentAfter)
        assertEquals("content-B", sourceContentAfter)
    }

    @Test
    fun testCloneRepositoryAtCommit_concurrentClonesOfDifferentCommitsDontInterfere() {

        val (repoFolder, commitA, commitB) = createLocalGitRepoWithTwoCommits()

        var cloneAtA: File? = null
        var cloneAtB: File? = null

        val threadA = Thread { cloneAtA = gitClient.cloneRepositoryAtCommit(repoFolder, commitA) }
        val threadB = Thread { cloneAtB = gitClient.cloneRepositoryAtCommit(repoFolder, commitB) }

        threadA.start()
        threadB.start()
        threadA.join()
        threadB.join()

        foldersToCleanup.add(cloneAtA!!)
        foldersToCleanup.add(cloneAtB!!)

        assertEquals("content-A", File(cloneAtA, "file.txt").readText())
        assertEquals("content-B", File(cloneAtB, "file.txt").readText())
    }

}
