/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2026 Pedro Alves
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

import org.dropproject.DropProjectIntegrationTest
import org.dropproject.config.DropProjectProperties
import org.dropproject.dao.Assignment
import org.dropproject.dao.Author
import org.dropproject.dao.GitSubmission
import org.dropproject.dao.ProjectGroup
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.AuthorRepository
import org.dropproject.repository.GitSubmissionRepository
import org.dropproject.repository.ProjectGroupRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File

/**
 * Tests the validation that prevents students from using repositories that anyone can read (issue #96).
 *
 * The rest of the test suite runs with this validation turned off (see drop-project-test.properties),
 * so this class turns it on explicitly.
 */
@DropProjectIntegrationTest
// merged with the @TestPropertySource brought in by @DropProjectIntegrationTest
@TestPropertySource(properties = ["drop-project.git.reject-public-student-repositories=true"])
class PublicGitRepositoryTests {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var gitSubmissionRepository: GitSubmissionRepository

    @Autowired
    lateinit var projectGroupRepository: ProjectGroupRepository

    @Autowired
    lateinit var authorRepository: AuthorRepository

    val assignmentId = "sampleJavaProject"

    val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

    // a repository owned by the drop project organization that is public and is expected to stay public
    val publicRepositoryUrl = "git@github.com:drop-project-edu/sampleJavaAssignment.git"

    // a repository owned by the drop project organization that is private and is expected to stay private
    val privateRepositoryUrl = "git@github.com:drop-project-edu/sampleJavaSubmission.git"

    val expectedErrorMsg = "Your repository is public, which allows other students to copy your work. " +
            "Make it private and try again."

    @BeforeEach
    fun initAssignment() {
        val assignment = Assignment(id = assignmentId, name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.GIT, active = true, gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = "sampleJavaProject")
        assignmentRepository.save(assignment)
    }

    @AfterEach
    fun cleanup() {
        val submissionsFolder = File(dropProjectProperties.storage.rootLocation)
        if (submissionsFolder.exists()) {
            submissionsFolder.deleteRecursively()
        }
    }

    @Test
    fun `connecting a public repository is refused`() {

        this.mvc.perform(post("/student/setup-git")
                .param("assignmentId", assignmentId)
                .param("gitRepositoryUrl", publicRepositoryUrl)
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-git-form"))
                .andExpect(model().attribute("gitRepoErrorMsg", expectedErrorMsg))
                // the form must keep the url that was typed, so that the student doesn't have to type it again
                .andExpect(model().attribute("gitRepositoryUrl", publicRepositoryUrl))

        assertEquals(0, gitSubmissionRepository.count(), "no git submission should have been created")
    }

    @Test
    fun `connecting a private repository proceeds to the deploy key step`() {

        this.mvc.perform(post("/student/setup-git")
                .param("assignmentId", assignmentId)
                .param("gitRepositoryUrl", privateRepositoryUrl)
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-setup-git"))

        assertEquals(1, gitSubmissionRepository.count(), "the git submission should have been created")
    }

    @Test
    fun `refreshing a repository that became public is refused`() {

        // simulate a submission that was connected while the repository was still private
        val gitSubmission = createConnectedGitSubmission(publicRepositoryUrl)

        this.mvc.perform(post("/git-submission/refresh-git/${gitSubmission.id}")
                .with(user(STUDENT_1)))
                .andExpect(status().isInternalServerError)
                .andExpect(content().json("{ \"error\": \"${expectedErrorMsg}\"}"))
    }

    private fun createConnectedGitSubmission(gitRepositoryUrl: String): GitSubmission {
        val group = ProjectGroup()
        projectGroupRepository.save(group)
        authorRepository.save(Author(name = "Student 1", number = "student1", group = group))

        val gitSubmission = GitSubmission(assignmentId = assignmentId, submitterUserId = "student1",
                gitRepositoryUrl = gitRepositoryUrl, group = group)
        gitSubmission.connected = true
        gitSubmissionRepository.save(gitSubmission)

        return gitSubmission
    }
}