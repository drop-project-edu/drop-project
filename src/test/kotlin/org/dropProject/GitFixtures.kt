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
package org.dropproject

import org.dropproject.config.DropProjectProperties
import org.dropproject.dao.Assignment
import org.dropproject.dao.Author
import org.dropproject.dao.GitSubmission
import org.dropproject.dao.ProjectGroup
import org.dropproject.repository.AuthorRepository
import org.dropproject.repository.GitSubmissionRepository
import org.dropproject.repository.ProjectGroupRepository
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Assertions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.stereotype.Service
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.io.File

/**
 * Test fixtures for git-based submissions: connecting student repositories and building
 * local repositories with history.
 */
@Service
class GitFixtures {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var gitSubmissionRepository: GitSubmissionRepository

    @Autowired
    lateinit var projectGroupRepository: ProjectGroupRepository

    @Autowired
    lateinit var authorRepository: AuthorRepository

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    fun connectToGitRepositoryAndBuildReport(assignmentId: String, gitRepository: String,
                                             studentUsername: String,
                                             privateKey: String = TestKeys.sampleJavaSubmissionPrivateKey,
                                             publicKey: String = TestKeys.sampleJavaSubmissionPublicKey): Long {

        val student = User(studentUsername, "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

        /*** POST /student/setup-git ***/
        mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", assignmentId)
                .param("gitRepositoryUrl", gitRepository)
                .with(user(student))
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.view().name("student-setup-git"))


        val id = gitSubmissionRepository.findAll().last().id

        val gitSubmission = gitSubmissionRepository.findById(id).get()
        Assertions.assertFalse(gitSubmission.connected)

        // inject public and private key
        gitSubmission.gitRepositoryPrivKey = privateKey
        gitSubmission.gitRepositoryPubKey = publicKey
        gitSubmissionRepository.save(gitSubmission)

        /*** POST /student/setup-git-2 ***/
        mvc.perform(MockMvcRequestBuilders.post("/student/setup-git-2/${id}")
                .with(user(student)))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/upload/${assignmentId}"))
                .andExpect(MockMvcResultMatchers.flash().attribute("message", "Ligado com sucesso ao repositório git"))

        val updatedGitSubmission = gitSubmissionRepository.findById(id).get()
        Assertions.assertTrue(updatedGitSubmission.connected)

        /*** GET /upload/ ***/
        mvc.perform(MockMvcRequestBuilders.get("/upload/${assignmentId}").with(user(student)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.view().name("student-git-form"))
                .andExpect(MockMvcResultMatchers.model().attribute("gitSubmission", updatedGitSubmission))

        /*** POST /git-submission/generate-report ***/
        mvc.perform(MockMvcRequestBuilders.post("/git-submission/generate-report/${id}")
                .with(user(student)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("{ \"submissionId\": \"1\"}"))

        return gitSubmission.id
    }

    // creates a GitSubmission backed by a real local git repo with two commits, the second one changing
    // the student's source file (marked with "MARKER-STUDENT-CODE-1" / "MARKER-STUDENT-CODE-2").
    // Returns the saved GitSubmission together with both commit hashes.
    fun createHistoricalGitSubmission(assignment: Assignment): Triple<GitSubmission, String, String> {
        val group = ProjectGroup()
        projectGroupRepository.save(group)
        authorRepository.save(Author(name = "Student 1", number = "student1", group = group))

        val gitSubmission = GitSubmission(
            assignmentId = assignment.id, submitterUserId = "student1",
            gitRepositoryUrl = "git@github.com:student1/${assignment.id}.git", group = group
        )
        gitSubmissionRepository.save(gitSubmission)

        val studentRepoFolder =
            File(dropProjectProperties.storage.gitLocation, gitSubmission.getFolderRelativeToStorageRoot())
        studentRepoFolder.mkdirs()
        val git = Git.init().setDirectory(studentRepoFolder).call()

        File(studentRepoFolder, "AUTHORS.txt").writeText("student1;Student 1")
        val mainFile = File(studentRepoFolder, "src/org/dropProject/samples/sampleJavaAssignment/Main.java")
        mainFile.parentFile.mkdirs()
        mainFile.writeText(
            """
            package org.dropProject.samples.sampleJavaAssignment;
            public class Main {
                // MARKER-STUDENT-CODE-1
            }
            """.trimIndent()
        )
        git.add().addFilepattern(".").call()
        val commitA = git.commit().setMessage("first student commit").call()

        mainFile.writeText(mainFile.readText().replace("MARKER-STUDENT-CODE-1", "MARKER-STUDENT-CODE-2"))
        git.add().addFilepattern(".").call()
        val commitB = git.commit().setMessage("second student commit").call()
        git.close()

        return Triple(gitSubmission, commitA.name, commitB.name)
    }
}
