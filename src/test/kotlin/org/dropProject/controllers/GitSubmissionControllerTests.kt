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
package org.dropProject.controllers

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.dropProject.TestsHelper
import org.dropProject.dao.*
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.*
import org.hamcrest.Matchers.hasProperty
import java.io.File

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class GitSubmissionControllerTests {

    @Value("\${mavenizedProjects.rootLocation}")
    val mavenizedProjectsRootLocation: String = ""

    @Value("\${storage.rootLocation}")
    val submissionsRootLocation: String = ""

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var gitSubmissionRepository: GitSubmissionRepository

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    private lateinit var testsHelper: TestsHelper

    val defaultAssignmentId = "sampleJavaProject"

    val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_2 = User("student2", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

    @Before
    fun initMavenizedFolder() {
        val folder = File(mavenizedProjectsRootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
        folder.mkdirs()
    }

    @Before
    fun initAssignment() {

        // create initial assignment
        val assignment01 = Assignment(id = "sampleJavaProject", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.GIT, active = true, gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = "sampleJavaProject")
        assignmentRepository.save(assignment01)
    }

    @After
    fun cleanup() {
        val folder = File(mavenizedProjectsRootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }

        val submissionsFolder = File(submissionsRootLocation)
        if (submissionsFolder.exists()) {
            submissionsFolder.deleteRecursively()
        }
    }

    @Test
    @DirtiesContext
    fun getGitSubmitPage() {

        this.mvc.perform(get("/upload/${defaultAssignmentId}")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))


    }

    @Test
    @DirtiesContext
    fun test_connectSubmissionWithInvalidGitRepository() {

        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", defaultAssignmentId)
                .param("gitRepositoryUrl", "git@githuu.com:someuser/cs1Assigment1.git")
                .with(user(STUDENT_1))
        )
                .andExpect(status().isOk())
                .andExpect(model().attribute("gitRepoErrorMsg", "O endereço do repositório não está no formato correcto"))



        try {
            gitSubmissionRepository.getById(1)
            fail("git submission shouldn't exist in the database")
        } catch (e: Exception) {
        }

    }

    @Test
    @DirtiesContext
    fun test_connectSubmissionWithInexistentGitRepositoryAndThenTryWithACorrectOne() {

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
    @DirtiesContext
    fun test_connectSubmissionWithValidButInexistentGitRepository() {

        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", defaultAssignmentId)
                .param("gitRepositoryUrl", "git@github.com:someuser/cs1Assignment1.git")
                .with(user(STUDENT_1))
        )
                .andExpect(status().isOk())
                .andExpect(view().name("student-setup-git"))

        try {
            val gitSubmission = gitSubmissionRepository.getOne(1)
            assertTrue("git submission should exist in the database", true)
            assertEquals("git@github.com:someuser/cs1Assignment1.git", gitSubmission.gitRepositoryUrl)
        } catch (e: Exception) {
            fail("git submission should exist in the database")
        }

    }

    @Test
    @DirtiesContext
    fun test_connectAndBuildReport() {

        /*** GET /upload/testJavaPro ***/
        val result = this.mvc.perform(get("/upload/${defaultAssignmentId}")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))
                .andReturn()
        assertNull(result.modelAndView!!.modelMap["gitSubmission"])

        testsHelper.connectToGitRepositoryAndBuildReport(mvc, gitSubmissionRepository, defaultAssignmentId,
                "git@github.com:palves-ulht/sampleJavaSubmission.git", "student1")

        /*** GET /buildReport ***/
        val reportResult = this.mvc.perform(get("/buildReport/1"))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 4 lines", 4, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary.get(0).indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary.get(0).reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary.get(1).indicator)
        assertEquals("compilation should be OK (value)", "OK", summary.get(1).reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary.get(2).indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary.get(2).reportValue)

        /*** POST /rebuildFull/1 ***/
        this.mvc.perform(post("/rebuildFull/1")
                .with(user(TEACHER_1)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/buildReport/2"))

        /*** GET /submissions/1 ***/
        this.mvc.perform(get("/submissions/?assignmentId=${defaultAssignmentId}&groupId=1")
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
    @DirtiesContext
    fun test_connectWithARepositoryWithoutAuthors_txt() {

        /*** POST /student/setup-git ***/
        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", defaultAssignmentId)
                .param("gitRepositoryUrl", "git@github.com:palves-ulht/sampleJavaAssignment.git") // <<< doesn't have AUTHORS.txt
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-setup-git"))


        val gitSubmission = gitSubmissionRepository.getOne(1)
        assertFalse(gitSubmission.connected)

        // inject public and private key
        gitSubmission.gitRepositoryPrivKey = TestsHelper.sampleJavaAssignmentPrivateKey
        gitSubmission.gitRepositoryPubKey = TestsHelper.sampleJavaAssignmentPublicKey
        gitSubmissionRepository.save(gitSubmission)

        /*** POST /student/setup-git-2 ***/
        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git-2/1")
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-setup-git"))
                .andExpect(model().attribute("error", "O projecto localizado no repositório " +
                        "git@github.com:palves-ulht/sampleJavaAssignment.git tem uma " +
                        "estrutura inválida: O projecto não contém o ficheiro AUTHORS.txt na raiz"))

        val updatedGitSubmission = gitSubmissionRepository.getOne(1)
        assertFalse(updatedGitSubmission.connected)

        assertEquals(1, gitSubmissionRepository.count())

        /*** POST /student/setup-git ***/
        this.mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", defaultAssignmentId)
                .param("gitRepositoryUrl", "git@github.com:palves-ulht/sampleJavaAssignment.git") // <<< doesn't have AUTHORS.txt
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-setup-git"))

        assertEquals(1, gitSubmissionRepository.count())  // make sure we didn't create a new git submission

        /*** GET /upload/ ***/
        this.mvc.perform(get("/upload/${defaultAssignmentId}")
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))
                .andExpect(model().attribute("gitSubmission", updatedGitSubmission))

        // now let's put another student who shares a group with this one connecting to github
        val gitSubmissionId = testsHelper.connectToGitRepositoryAndBuildReport(mvc, gitSubmissionRepository, defaultAssignmentId,
                "git@github.com:palves-ulht/sampleJavaSubmission.git", "student1")
        val anotherStudentGitSubmission = gitSubmissionRepository.getOne(gitSubmissionId)

        /*** GET /upload/testJavaPro ***/
        this.mvc.perform(get("/upload/${defaultAssignmentId}")
                .with(user(STUDENT_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("student-git-form"))
                .andExpect(model().attribute("gitSubmission", anotherStudentGitSubmission))  // <<< this should the other student submission since this one was not connected

    }

    @Test
    @DirtiesContext
    fun test_connectAndBuildReportAndDisconnect() {

        testsHelper.connectToGitRepositoryAndBuildReport(mvc, gitSubmissionRepository, defaultAssignmentId,
                "git@github.com:palves-ulht/sampleJavaSubmission.git", "student1")

        assertEquals(1, gitSubmissionRepository.count())
        assertEquals(1, submissionRepository.count())

        /*** POST /reset-git/ ***/
        this.mvc.perform(MockMvcRequestBuilders.post("/student/reset-git/1")
                .with(user(STUDENT_1)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/upload/${defaultAssignmentId}"))
                .andExpect(flash().attribute("message",
                        "Desligado com sucesso do repositório git@github.com:palves-ulht/sampleJavaSubmission.git"))

        assertEquals(0, gitSubmissionRepository.count())
        assertEquals(0, submissionRepository.count())
    }


}



