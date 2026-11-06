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

import org.dropproject.DropProjectIntegrationTest
import org.dropproject.FakeBuild
import org.dropproject.FakeBuildRunner
import org.dropproject.FakeClock
import org.dropproject.FakeGitClient
import org.dropproject.GitFixtures
import org.dropproject.TestKeys
import org.dropproject.TestUsers.STUDENT_1
import org.dropproject.TestUsers.STUDENT_2
import org.dropproject.TestUsers.TEACHER_1
import org.dropproject.config.DropProjectProperties
import org.dropproject.dao.Assignment
import org.dropproject.dao.Indicator
import org.dropproject.dao.ProjectGroupRestrictions
import org.dropproject.dao.SubmissionReport
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.GitSubmissionRepository
import org.dropproject.repository.ProjectGroupRestrictionsRepository
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime

/**
 * Experiment: the git submission flow running completely offline and without real Maven builds,
 * using [FakeGitClient] (maps the github url to a local fixture repository) and [FakeBuildRunner]
 * (replaces the Maven build with canned surefire reports). Replicates the "connect and build report"
 * test from [GitSubmissionControllerTests], which needs the network and takes ~1 minute; this one
 * is not tagged "integration" so it also runs with 'mvn test -Pfast'.
 */
@DropProjectIntegrationTest
class GitSubmissionFastTests {

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var gitFixtures: GitFixtures

    @Autowired
    lateinit var fakeGitClient: FakeGitClient

    @Autowired
    lateinit var fakeBuildRunner: FakeBuildRunner

    @Autowired
    lateinit var fakeClock: FakeClock

    @Autowired
    lateinit var gitSubmissionRepository: GitSubmissionRepository

    @Autowired
    lateinit var projectGroupRestrictionsRepository: ProjectGroupRestrictionsRepository

    val defaultAssignmentId = "sampleJavaProject"
    val studentRepositoryUrl = "git@github.com:drop-project-edu/sampleJavaSubmission.git"

    lateinit var studentRepoFolder: File
    lateinit var studentRepoHeadSha: String

    @BeforeEach
    fun initAssignment() {
        val folder = File(dropProjectProperties.mavenizedProjects.rootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
        folder.mkdirs()

        val assignment = Assignment(id = defaultAssignmentId, name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.GIT, active = true, gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = defaultAssignmentId)
        assignmentRepository.save(assignment)
    }

    @BeforeEach
    fun setupFakeGitRemoteAndFakeBuild() {
        // a local repository with the same content as github.com/drop-project-edu/sampleJavaSubmission
        studentRepoFolder = Files.createTempDirectory("fake-student-repo").toFile()
        Git.init().setDirectory(studentRepoFolder).call().use { git ->
            File(studentRepoFolder, "AUTHORS.txt").writeText("student1;Student 1\nstudent2;Student 2")
            val mainFile = File(studentRepoFolder, "src/org/dropProject/samples/sampleJavaAssignment/Main.java")
            mainFile.parentFile.mkdirs()
            mainFile.writeText(
                """
                package org.dropProject.samples.sampleJavaAssignment;
                public class Main {
                    public static int findMax(int[] values) {
                        if (values == null) { throw new IllegalArgumentException(); }
                        int max = values[0];
                        for (int value : values) { if (value > max) { max = value; } }
                        return max;
                    }
                }
                """.trimIndent()
            )
            git.add().addFilepattern(".").call()
            val commit = git.commit()
                .setAuthor("Student 1", "student1@dropproject.org")
                .setCommitter("Student 1", "student1@dropproject.org")
                .setMessage("student submission").call()
            studentRepoHeadSha = commit.name
        }
        fakeGitClient.registerRemote(studentRepositoryUrl, studentRepoFolder)

        // every build of this submission passes both teacher tests
        fakeBuildRunner.fakeNextBuilds(FakeBuild(mapOf(
            "org.dropProject.samples.sampleJavaAssignment.TestTeacherProject" to
                    listOf("testFindMax", "testFindMaxWithNull"))))
    }

    @AfterEach
    fun cleanup() {
        studentRepoFolder.deleteRecursively()

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
    fun `students can only generate a report during the submission windows`() {

        val assignment = assignmentRepository.findById(defaultAssignmentId).get()
        assignment.submissionWindowsText = "2026-11-03 09:00-11:00"
        assignmentRepository.save(assignment)

        fakeClock.setNow(LocalDateTime.parse("2026-11-03T10:00:00"))
        gitFixtures.connectToGitRepositoryAndBuildReport(defaultAssignmentId, studentRepositoryUrl, "student1")

        fakeClock.setNow(LocalDateTime.parse("2026-11-03T11:30:00"))
        this.mvc.perform(get("/upload/${defaultAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("submissionWindowClosedMessage"))

        this.mvc.perform(post("/git-submission/generate-report/1").with(user(STUDENT_1)))
            .andExpect(status().isConflict())
    }

    @Test
    fun `connect and build report with a fake git remote and a fake build runner`() {

        /*** GET /upload/sampleJavaProject ***/
        val result = this.mvc.perform(get("/upload/${defaultAssignmentId}")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andExpect(view().name("student-git-form"))
            .andReturn()
        assertNull(result.modelAndView!!.modelMap["gitSubmission"])

        gitFixtures.connectToGitRepositoryAndBuildReport(defaultAssignmentId, studentRepositoryUrl, "student1")

        /*** GET /buildReport ***/
        val reportResult = this.mvc.perform(get("/buildReport/1").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("gitRepository", "https://github.com/drop-project-edu/sampleJavaSubmission"))
            .andExpect(model().attribute("gitRepositoryWithHash",
                "https://github.com/drop-project-edu/sampleJavaSubmission/tree/${studentRepoHeadSha}"))
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
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary.get(3).indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary.get(3).reportValue, "teacher tests should be OK (value)")
        assertEquals(2, summary.get(3).reportProgress, "both teacher tests should pass")

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

    private fun restrictGroupSize(minGroupSize: Int, maxGroupSize: Int, exceptions: String? = null) {
        val restrictions = ProjectGroupRestrictions(minGroupSize = minGroupSize, maxGroupSize = maxGroupSize,
            exceptions = exceptions)
        projectGroupRestrictionsRepository.save(restrictions)
        val assignment = assignmentRepository.findById(defaultAssignmentId).get()
        assignment.projectGroupRestrictions = restrictions
        assignmentRepository.save(assignment)
    }

    private fun commitAuthors(authors: String) {
        Git.open(studentRepoFolder).use { git ->
            File(studentRepoFolder, "AUTHORS.txt").writeText(authors)
            git.add().addFilepattern("AUTHORS.txt").call()
            git.commit()
                .setAuthor("Student 1", "student1@dropproject.org")
                .setCommitter("Student 1", "student1@dropproject.org")
                .setMessage("change authors").call()
        }
    }

    // goes through the first step of the git setup and returns the id of the (still unconnected) git submission
    private fun setupGit(): Long {
        this.mvc.perform(post("/student/setup-git")
            .param("assignmentId", defaultAssignmentId)
            .param("gitRepositoryUrl", studentRepositoryUrl)
            .with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andExpect(view().name("student-setup-git"))

        val gitSubmission = gitSubmissionRepository.findAll().last()
        gitSubmission.gitRepositoryPrivKey = TestKeys.sampleJavaSubmissionPrivateKey
        gitSubmission.gitRepositoryPubKey = TestKeys.sampleJavaSubmissionPublicKey
        gitSubmissionRepository.save(gitSubmission)
        return gitSubmission.id
    }

    @Test
    fun `students can't connect a repository whose group violates the group restrictions`() {

        restrictGroupSize(1, 1)
        val gitSubmissionId = setupGit()

        // the repository's AUTHORS.txt has two students
        this.mvc.perform(post("/student/setup-git-2/${gitSubmissionId}").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andExpect(view().name("student-setup-git"))
            .andExpect(model().attribute("error",
                "This assignment only accepts submissions from groups with 1..1 elements."))
        assertFalse(gitSubmissionRepository.findById(gitSubmissionId).get().connected)

        // after fixing AUTHORS.txt, connecting again works
        commitAuthors("student1;Student 1")
        this.mvc.perform(post("/student/setup-git-2/${gitSubmissionId}").with(user(STUDENT_1)))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/upload/${defaultAssignmentId}"))
        val gitSubmission = gitSubmissionRepository.findById(gitSubmissionId).get()
        assertTrue(gitSubmission.connected)
        assertEquals(listOf("student1"), gitSubmission.group.authors.map { it.userId })
    }

    @Test
    fun `students can connect a repository that violates the group restrictions if they are an exception`() {

        restrictGroupSize(1, 1, exceptions = "student1")
        val gitSubmissionId = setupGit()

        this.mvc.perform(post("/student/setup-git-2/${gitSubmissionId}").with(user(STUDENT_1)))
            .andExpect(status().isFound())
        assertTrue(gitSubmissionRepository.findById(gitSubmissionId).get().connected)
    }

    @Test
    fun `students can't generate a report if the group restrictions changed after connecting`() {

        val gitSubmissionId = gitFixtures.connectToGitRepositoryAndBuildReport(defaultAssignmentId,
            studentRepositoryUrl, "student1")

        // the group (with two students) was connected before the teacher restricted the assignment to individuals
        restrictGroupSize(1, 1)

        this.mvc.perform(post("/git-submission/generate-report/${gitSubmissionId}").with(user(STUDENT_1)))
            .andExpect(status().isInternalServerError())
            .andExpect(content().json("""{"error": "This assignment only accepts submissions from groups with 1..1 elements. """ +
                    """To change the group, fix AUTHORS.txt and connect the repository again."}"""))

        // changing AUTHORS.txt without connecting again has no effect, since the group was fixed when connecting
        commitAuthors("student1;Student 1")
        this.mvc.perform(post("/git-submission/refresh-git/${gitSubmissionId}").with(user(STUDENT_1)))
            .andExpect(status().isOk())
        this.mvc.perform(post("/git-submission/generate-report/${gitSubmissionId}").with(user(STUDENT_1)))
            .andExpect(status().isInternalServerError())

        // the assignment's teachers are not restricted
        this.mvc.perform(post("/git-submission/generate-report/${gitSubmissionId}").with(user(TEACHER_1)))
            .andExpect(status().isOk())
    }
}
