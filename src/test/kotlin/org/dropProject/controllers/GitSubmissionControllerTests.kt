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
package org.dropproject.controllers

import org.dropproject.GitFixtures
import org.dropproject.TestKeys
import org.dropproject.TestUsers.STUDENT_1
import org.dropproject.TestUsers.STUDENT_2
import org.dropproject.TestUsers.TEACHER_1
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.dropproject.config.DropProjectProperties
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.dropproject.dao.*
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.hamcrest.Matchers.hasProperty
import java.io.File

@DropProjectIntegrationTest
@Tag("integration")
class GitSubmissionControllerTests {

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var gitSubmissionRepository: GitSubmissionRepository

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var gitFixtures: GitFixtures

    val defaultAssignmentId = "sampleJavaProject"

    @BeforeEach
    fun initMavenizedFolder() {
        val folder = File(dropProjectProperties.mavenizedProjects.rootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
        folder.mkdirs()
    }

    @BeforeEach
    fun initAssignment() {

        // create initial assignment
        val assignment01 = Assignment(id = "sampleJavaProject", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.GIT, active = true, gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = "sampleJavaProject")
        assignmentRepository.save(assignment01)
    }

    @AfterEach
    fun cleanup() {
        val folder = File(dropProjectProperties.mavenizedProjects.rootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }

        val submissionsFolder = File(dropProjectProperties.storage.rootLocation)
        if (submissionsFolder.exists()) {
            submissionsFolder.deleteRecursively()
        }
    }

    @Test
    fun getGitSubmitPage() {

        this.mvc.perform(get("/upload/${defaultAssignmentId}")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))


    }

    @Test
    fun `connect submission without git repository url`() {

        // without the parameter at all
        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", defaultAssignmentId)
                .with(user(STUDENT_1))
        )
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))
                .andExpect(model().attribute("gitRepoErrorMsg", "You must fill the repository's url"))

        // with an empty parameter
        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", defaultAssignmentId)
                .param("gitRepositoryUrl", "")
                .with(user(STUDENT_1))
        )
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))
                .andExpect(model().attribute("gitRepoErrorMsg", "You must fill the repository's url"))
    }

    @Test
    fun `connect submission with an invalid git repository`() {

        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", defaultAssignmentId)
                .param("gitRepositoryUrl", "git@githuu.com:someuser/cs1Assigment1.git")
                .with(user(STUDENT_1))
        )
                .andExpect(status().isOk())
                .andExpect(model().attribute("gitRepoErrorMsg", "The repository's url is not in the correct format"))
                // the form must keep the url that was typed, so that the student doesn't have to type it again
                .andExpect(model().attribute("gitRepositoryUrl", "git@githuu.com:someuser/cs1Assigment1.git"))



        try {
            gitSubmissionRepository.findById(1).get()
            fail<Unit>("git submission shouldn't exist in the database")
        } catch (e: Exception) {
        }

    }

    @Test
    fun `connect submission with an inexistent git repository and then try with a correct one`() {

        // setup a connection to an inexistent git repo
        this.mvc.perform(post("/student/setup-git")
            .param("assignmentId", defaultAssignmentId)
            .param("gitRepositoryUrl", "git@github.com:someuser/inexistent.git")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-setup-git"))
            .andExpect(model().attribute("repositorySettingsUrl", "https://github.com/someuser/inexistent/settings/keys"))

        // now, setup a connection to an existent git repo
        this.mvc.perform(post("/student/setup-git")
            .param("assignmentId", defaultAssignmentId)
            .param("gitRepositoryUrl", "git@github.com:palves-ulht/sampleJavaSubmission.git")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-setup-git"))
            .andExpect(model().attribute("repositorySettingsUrl", "https://github.com/palves-ulht/sampleJavaSubmission/settings/keys"))

    }

    @Test
    fun `connect submission with a valid but inexistent git repository`() {

        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", defaultAssignmentId)
                .param("gitRepositoryUrl", "git@github.com:someuser/cs1Assignment1.git")
                .with(user(STUDENT_1))
        )
                .andExpect(status().isOk())
                .andExpect(view().name("student-setup-git"))

        try {
            val gitSubmission = gitSubmissionRepository.findById(1).get()
            assertTrue(true, "git submission should exist in the database")
            assertEquals("git@github.com:someuser/cs1Assignment1.git", gitSubmission.gitRepositoryUrl)
        } catch (e: Exception) {
            fail<Unit>("git submission should exist in the database")
        }

    }

    @Test
    fun `connect and build report`() {

        /*** GET /upload/testJavaPro ***/
        val result = this.mvc.perform(get("/upload/${defaultAssignmentId}")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))
                .andReturn()
        assertNull(result.modelAndView!!.modelMap["gitSubmission"])

        gitFixtures.connectToGitRepositoryAndBuildReport(defaultAssignmentId,
                "git@github.com:drop-project-edu/sampleJavaSubmission.git", "student1")

        /*** GET /buildReport ***/
        val reportResult = this.mvc.perform(get("/buildReport/1").with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(model().attribute("gitRepository", "https://github.com/drop-project-edu/sampleJavaSubmission"))
                .andExpect(model().attribute("gitRepositoryWithHash", "https://github.com/drop-project-edu/sampleJavaSubmission/tree/88d14eac0debdc0baf8e3592d4744ce4979f3fd8"))
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(4, summary.size, "Summary should be 4 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary.get(0).indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary.get(0).reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary.get(1).indicator, "compilation should be OK (key)")
        assertEquals("OK", summary.get(1).reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary.get(2).indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary.get(2).reportValue, "checkstyle should be OK (value)")

        /*** POST /rebuildFull/1 ***/
        this.mvc.perform(post("/rebuildFull/1")
                .with(user(TEACHER_1)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/buildReport/2"))

        /*** GET /submissions/1 ***/
        this.mvc.perform(get("/submissions?assignmentId=${defaultAssignmentId}&groupId=1")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())

        /*** if I access with the other student of the group, I should see the submission ***/
        val result2 = this.mvc.perform(get("/upload/${defaultAssignmentId}")
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))
                .andReturn()
        assertNotNull(result2.modelAndView!!.modelMap["gitSubmission"])
    }



    @Test
    fun `connect with a repository without AUTHORS txt`() {

        /*** POST /student/setup-git ***/
        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", defaultAssignmentId)
                .param("gitRepositoryUrl", "git@github.com:palves-ulht/sampleJavaAssignment.git") // <<< doesn't have AUTHORS.txt
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-setup-git"))


        val gitSubmission = gitSubmissionRepository.findById(1).get()
        assertFalse(gitSubmission.connected)

        // inject public and private key
        gitSubmission.gitRepositoryPrivKey = TestKeys.sampleJavaAssignmentPrivateKey
        gitSubmission.gitRepositoryPubKey = TestKeys.sampleJavaAssignmentPublicKey
        gitSubmissionRepository.save(gitSubmission)

        /*** POST /student/setup-git-2 ***/
        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git-2/1")
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-setup-git"))
                .andExpect(model().attribute("error", "The project in repository " +
                        "git@github.com:palves-ulht/sampleJavaAssignment.git has an invalid structure: " +
                        "The project does not contain the AUTHORS.txt file in the root"))

        val updatedGitSubmission = gitSubmissionRepository.findById(1).get()
        assertFalse(updatedGitSubmission.connected)

        assertEquals(1, gitSubmissionRepository.count())

        /*** POST /student/setup-git ***/
        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", defaultAssignmentId)
                .param("gitRepositoryUrl", "git@github.com:palves-ulht/sampleJavaAssignment.git") // <<< doesn't have AUTHORS.txt
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-setup-git"))

        assertEquals(1, gitSubmissionRepository.count())  // make sure we don't have now two git submissions

        val newGitSubmission = gitSubmissionRepository.findById(2).get()

        /*** GET /upload/ ***/
        this.mvc.perform(get("/upload/${defaultAssignmentId}")
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))
                .andExpect(model().attribute("gitSubmission", newGitSubmission))

        // now let's put another student who shares a group with this one connecting to github
        val gitSubmissionId = gitFixtures.connectToGitRepositoryAndBuildReport(defaultAssignmentId,
                "git@github.com:drop-project-edu/sampleJavaSubmission.git", "student1")
        val anotherStudentGitSubmission = gitSubmissionRepository.findById(gitSubmissionId).get()

        /*** GET /upload/testJavaPro ***/
        this.mvc.perform(get("/upload/${defaultAssignmentId}")
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))
                .andExpect(model().attribute("gitSubmission", anotherStudentGitSubmission))  // <<< this should the other student submission since this one was not connected

    }

    @Test
    fun `connect, build report and disconnect`() {

        gitFixtures.connectToGitRepositoryAndBuildReport(defaultAssignmentId,
                "git@github.com:drop-project-edu/sampleJavaSubmission.git", "student1")

        assertEquals(1, gitSubmissionRepository.count())
        assertEquals(1, submissionRepository.count())

        /*** POST /reset-git/ ***/
        this.mvc.perform(MockMvcRequestBuilders.post("/student/reset-git/1")
                .with(user(STUDENT_1)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/upload/${defaultAssignmentId}"))
                .andExpect(flash().attribute("message",
                        "Desligado com sucesso do repositório git@github.com:drop-project-edu/sampleJavaSubmission.git"))

        assertEquals(0, gitSubmissionRepository.count())
        assertEquals(0, submissionRepository.count())
    }

    @Test
    fun `connect and refresh`() {

        // try to refresh a submission that doesn't exist
        this.mvc.perform(
            post("/git-submission/refresh-git/1")
            .with(user(STUDENT_1)))
            .andExpect(status().isInternalServerError)

        val gitSubmissionId = gitFixtures.connectToGitRepositoryAndBuildReport(defaultAssignmentId, "git@github.com:drop-project-edu/sampleJavaSubmission.git", "student1")

        this.mvc.perform(
            post("/git-submission/refresh-git/${gitSubmissionId}")
                .with(user(STUDENT_1)))
            .andExpect(status().isOk)
    }

}



