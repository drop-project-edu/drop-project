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
import org.dropproject.TestUsers.STUDENT_1
import org.dropproject.TestUsers.STUDENT_3
import org.dropproject.TestUsers.TEACHER_1
import org.dropproject.dao.Submission
import org.dropproject.services.AssignmentTeacherFiles
import org.dropproject.services.SourceDiffService
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matchers.hasProperty
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.dropproject.services.DefenseCheckpointStatus
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File

/**
 * Tests for the "defense" assignments, that is, assignments whose submissions are supposed to be a bounded set of
 * changes on top of the code that the group had already submitted to another (the "project") assignment.
 *
 * These tests exercise the whole upload flow but, since the divergence is measured before the submission is built,
 * they replace the (slow) Maven build with [FakeBuildRunner]. That's why, like [GitSubmissionFastTests], this class
 * is not tagged as "integration": it also runs with 'mvn test -Pfast'.
 */
@DropProjectIntegrationTest
class UploadDefenseAssignmentTests : UploadTestBase() {

    @Autowired
    lateinit var sourceDiffService: SourceDiffService

    @Autowired
    lateinit var fakeBuildRunner: FakeBuildRunner

    @Autowired
    lateinit var assignmentTeacherFiles: AssignmentTeacherFiles

    val projectAssignmentId = "testJavaProj"
    val defenseAssignmentId = "testJavaProjDefense"

    @BeforeEach
    fun setupFakeBuild() {
        // the divergence is measured before the build, so there is no point in running a real Maven build
        fakeBuildRunner.fakeNextBuilds(FakeBuild(mapOf(
            "org.dropProject.sampleAssignments.testProj.TestTeacherProject" to
                    listOf("testFuncaoParaTestar", "testFuncaoLentaParaTestar"),
            "org.dropProject.sampleAssignments.testProj.TestTeacherHiddenProject" to
                    listOf("testFuncaoParaTestarQueNaoApareceAosAlunos"))))
    }

    /**
     * Makes the next build fail one of the project assignment's teacher tests, which is what happens when a student
     * submits code that doesn't work.
     */
    private fun failTheNextBuild() {
        fakeBuildRunner.fakeNextBuilds(FakeBuild(
            passingTests = mapOf("org.dropProject.sampleAssignments.testProj.TestTeacherProject" to
                    listOf("testFuncaoParaTestar")),
            failingTests = mapOf("org.dropProject.sampleAssignments.testProj.TestTeacherProject" to
                    listOf("testFuncaoLentaParaTestar"))))
    }

    private fun sampleProjectFolder(projectName: String) = File("src/test/sampleProjects/compact/java/$projectName")

    /**
     * The number of changed lines that the upload of [newProject], on top of a submission of [referenceProject],
     * is expected to produce. It is calculated with the same service that the upload flow uses, so that these tests
     * don't have to hardcode (and keep in sync) the diff between two sample projects.
     */
    private fun divergenceBetween(referenceProject: String, newProject: String) =
        sourceDiffService.countChangedLines(sampleProjectFolder(referenceProject), sampleProjectFolder(newProject))

    private fun submissionsTo(assignmentId: String) =
        submissionRepository.findAll().filter { it.assignmentId == assignmentId }

    private fun savedSubmission(submissionId: String) = submissionRepository.findById(submissionId.toLong()).get()

    @Test
    fun `submission is rejected when the group has nothing submitted to the project assignment`() {

        assignmentFixtures.createDefenseAssignment()

        val error = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1,
            expectedResultMatcher = status().isInternalServerError())

        assertEquals("Your submission was rejected: you did not submit assignment ${projectAssignmentId} before " +
                "the due date, and that is the code that a defense must be based on.", error)
        assertTrue(submissionsTo(defenseAssignmentId).isEmpty(), "no submission should have been created")
    }

    @Test
    fun `the submission of another group is not used as the reference`() {

        // student1 (together with student2) submits to the project assignment, but student3 doesn't
        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()

        val error = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_3,
            authors = listOf(STUDENT_3.username to "Student 3"),
            expectedResultMatcher = status().isInternalServerError())

        assertEquals("Your submission was rejected: you did not submit assignment ${projectAssignmentId} before " +
                "the due date, and that is the code that a defense must be based on.", error)
    }

    @Test
    fun `a checkpoint submission with the original code is accepted with zero divergence`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(maxChangedLines = 5)

        val submissionId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)

        assertEquals(0, savedSubmission(submissionId).baseDivergenceLines)
    }

    @Test
    fun `on the first phase, a submission that is not the original code is rejected`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)

        // the budget is irrelevant on the first phase: the code has to be exactly the original one
        assignmentFixtures.createDefenseAssignment(maxChangedLines = 100, defenseInstructionsReleased = false)

        val error = submissionFixtures.uploadProject("projectCheckstyleErrors", defenseAssignmentId, STUDENT_1,
            expectedResultMatcher = status().isInternalServerError())

        assertEquals("Your submission was rejected: this is not the code you submitted to assignment " +
                "${projectAssignmentId}. Download that submission and submit it without any change.", error)
        assertTrue(submissionsTo(defenseAssignmentId).isEmpty(), "no submission should have been created")
    }

    @Test
    fun `on the second phase, a submission within the line budget is accepted`() {

        val expectedDivergence = divergenceBetween("projectOK", "projectCheckstyleErrors")

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(maxChangedLines = expectedDivergence)
        startDefenseFor(STUDENT_1)

        val submissionId = submissionFixtures.uploadProject("projectCheckstyleErrors", defenseAssignmentId, STUDENT_1)

        assertEquals(expectedDivergence, savedSubmission(submissionId).baseDivergenceLines)
    }

    @Test
    fun `on the second phase, a submission over the line budget is rejected`() {

        val expectedDivergence = divergenceBetween("projectOK", "projectCheckstyleErrors")
        val maxChangedLines = expectedDivergence - 1

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(maxChangedLines = maxChangedLines)
        startDefenseFor(STUDENT_1)

        val error = submissionFixtures.uploadProject("projectCheckstyleErrors", defenseAssignmentId, STUDENT_1,
            expectedResultMatcher = status().isInternalServerError())

        // the budget itself is deliberately kept out of the message
        assertEquals("This submission changes too much of the code you submitted to the project assignment. " +
                "Undo the changes that were not requested and submit again.", error)
        assertEquals(1, submissionsTo(defenseAssignmentId).size,
            "only the checkpoint should have been created")
    }

    @Test
    fun `on the second phase, the divergence is not limited when the assignment has no line budget`() {

        val expectedDivergence = divergenceBetween("projectOK", "projectCheckstyleErrors")

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(maxChangedLines = null)
        startDefenseFor(STUDENT_1)

        val submissionId = submissionFixtures.uploadProject("projectCheckstyleErrors", defenseAssignmentId, STUDENT_1)

        assertEquals(expectedDivergence, savedSubmission(submissionId).baseDivergenceLines)
    }

    @Test
    fun `submissions to an assignment that is not linked to another one have no divergence`() {

        val submissionId = submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)

        assertNull(savedSubmission(submissionId).baseDivergenceLines)
    }

    @Test
    fun `the submission marked as final is the one used as the reference`() {

        val expectedDivergence = divergenceBetween("projectOK", "projectCheckstyleErrors")

        val firstSubmissionId = submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        submissionFixtures.uploadProject("projectCheckstyleErrors", projectAssignmentId, STUDENT_1)

        // the teacher considers the first submission (the one with the "projectOK" code) to be the group's work
        this.mvc.perform(post("/markAsFinal/${firstSubmissionId}").with(user(TEACHER_1)))
            .andExpect(status().isFound)

        assignmentFixtures.createDefenseAssignment()
        // the reference is the submission marked as final, so that's the code that passes the first phase
        startDefenseFor(STUDENT_1, checkpointProject = "projectOK")

        val submissionId = submissionFixtures.uploadProject("projectCheckstyleErrors", defenseAssignmentId, STUDENT_1)

        // if the most recent submission had been used as the reference, the divergence would be zero
        assertEquals(expectedDivergence, savedSubmission(submissionId).baseDivergenceLines)
    }

    @Test
    fun `the latest submission is used as the reference when none is marked as final`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        submissionFixtures.uploadProject("projectCheckstyleErrors", projectAssignmentId, STUDENT_1)

        assignmentFixtures.createDefenseAssignment()

        val submissionId = submissionFixtures.uploadProject("projectCheckstyleErrors", defenseAssignmentId, STUDENT_1)

        assertEquals(0, savedSubmission(submissionId).baseDivergenceLines)
    }

    @Test
    fun `the upload page of a defense assignment links to the submission of the project assignment`() {

        val baseSubmissionId = submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute<Submission>("baseSubmission",
                hasProperty("id", equalTo(baseSubmissionId.toLong()))))
    }

    @Test
    fun `the student can download the submission that the defense starts from`() {

        val baseSubmissionId = submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()

        // the link is on the page, so the url has to be reachable by a student and not only by a teacher
        this.mvc.perform(get("/downloadOriginalProject/${baseSubmissionId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
    }

    @Test
    fun `a student cannot download the submission of another group`() {

        val baseSubmissionId = submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)

        this.mvc.perform(get("/downloadOriginalProject/${baseSubmissionId}").with(user(STUDENT_3)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a checkpoint submission sends the student back to the upload page, not to the build report`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()

        val response = submissionFixtures.uploadProjectRaw("projectOK", defenseAssignmentId, STUDENT_1)

        assertTrue(response.contains("\"redirectTo\":\"upload/${defenseAssignmentId}\""),
            "the checkpoint should redirect to the upload page, but the response was ${response}")
    }

    @Test
    fun `a submission on the second phase goes to the build report as usual`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()
        startDefenseFor(STUDENT_1)

        val response = submissionFixtures.uploadProjectRaw("projectOK", defenseAssignmentId, STUDENT_1)

        assertFalse(response.contains("redirectTo"),
            "the defense submission should go to its build report, but the response was ${response}")
    }

    @Test
    fun `a teacher is on the second phase from the start and needs no submission to the project assignment`() {

        assignmentFixtures.createDefenseAssignment()

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("defensePhase", equalTo(2)))

        // and the upload is accepted, even without a submission to the project assignment to compare it with
        val submissionId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, TEACHER_1,
            authors = listOf(TEACHER_1.username to "Teacher 1"))

        assertNull(savedSubmission(submissionId).baseDivergenceLines,
            "there is nothing to measure the teacher's submission against")
    }

    @Test
    fun `a student stays on the first phase until the instructions are released`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("defensePhase", equalTo(1)))
    }

    @Test
    fun `a submission made on the first phase is recorded as the original code`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()

        val submissionId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)

        assertTrue(savedSubmission(submissionId).defenseCheckpoint,
            "a first phase submission is the student's original code")
    }

    @Test
    fun `a submission made on the second phase is not the original code`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()
        startDefenseFor(STUDENT_1)

        val submissionId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)

        assertFalse(savedSubmission(submissionId).defenseCheckpoint)
    }

    @Test
    fun `the info page of a defense assignment shows its type and its defense settings`() {

        assignmentFixtures.createDefenseAssignment(maxChangedLines = 7)

        val page = this.mvc.perform(get("/assignment/info/${defenseAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertTrue(page.contains("Defense of:"), "the linked assignment should be shown")
        assertTrue(page.contains(projectAssignmentId), "the linked assignment should be named")
        assertTrue(page.contains("Max changed lines:"), "the line budget should be shown")
        assertTrue(page.contains("Not released"), "the phase should be shown")
    }

    @Test
    fun `the info page of a regular assignment has no defense settings`() {

        val page = this.mvc.perform(get("/assignment/info/${projectAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertFalse(page.contains("Defense of:"), "a regular assignment is not a defense of anything")
        assertFalse(page.contains("Max changed lines:"))
    }

    @Test
    fun `picking a project assignment fills in the package and the size of the change`() {

        // the fixture points the defense at the same repository as the project assignment, so nothing differs
        assignmentFixtures.createDefenseAssignment()

        val fragment = this.mvc.perform(get("/assignment/defense-derived-fields")
            .param("baseAssignmentId", projectAssignmentId)
            .param("assignmentId", defenseAssignmentId)
            .with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertTrue(fragment.contains("hx-swap-oob"), "the fields are applied out of band")
        assertTrue(fragment.contains("org.dropProject.sampleAssignments.testProj"),
            "the package should be the project assignment's, but the fragment was ${fragment}")
        assertTrue(fragment.contains("changes 0 lines"),
            "the size of the change should be shown, but the fragment was ${fragment}")
    }

    @Test
    fun `while the assignment is being created the size of the change is not known yet`() {

        val fragment = this.mvc.perform(get("/assignment/defense-derived-fields")
            .param("baseAssignmentId", projectAssignmentId)
            .param("assignmentId", "")
            .with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertTrue(fragment.contains("org.dropProject.sampleAssignments.testProj"),
            "the package is known from the start")
        assertTrue(fragment.contains("once the assignment is created"),
            "the size of the change can only be known later, but the fragment was ${fragment}")
    }

    @Test
    fun `the derived fields of an assignment the teacher does not manage are not disclosed`() {

        assignmentFixtures.createDefaultAssignment(id = "someoneElsesProj").let {
            it.ownerUserId = "anotherTeacher"
            assignmentRepository.save(it)
        }

        val fragment = this.mvc.perform(get("/assignment/defense-derived-fields")
            .param("baseAssignmentId", "someoneElsesProj")
            .with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertFalse(fragment.contains("org.dropProject.sampleAssignments.testProj"),
            "the package of an assignment of another teacher must not be disclosed")
    }

    @Test
    fun `the upload page of a regular assignment has no link to another submission`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)

        this.mvc.perform(get("/upload/${projectAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attributeDoesNotExist("baseSubmission"))
    }

    @Test
    fun `the teacher can release and hide the defense instructions`() {

        assignmentFixtures.createDefenseAssignment()
        assertFalse(assignmentRepository.findById(defenseAssignmentId).get().defenseInstructionsReleased)

        this.mvc.perform(post("/assignment/toggle-defense-instructions/${defenseAssignmentId}")
            .with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/assignment/my"))
            .andExpect(flash().attribute("message", "Defense instructions of assignment ${defenseAssignmentId} " +
                    "are now visible to the students"))

        assertTrue(assignmentRepository.findById(defenseAssignmentId).get().defenseInstructionsReleased)

        this.mvc.perform(post("/assignment/toggle-defense-instructions/${defenseAssignmentId}")
            .with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(flash().attribute("message", "Defense instructions of assignment ${defenseAssignmentId} " +
                    "are now hidden to the students and the assignment was marked inactive"))

        assertFalse(assignmentRepository.findById(defenseAssignmentId).get().defenseInstructionsReleased)
    }

    @Test
    fun `hiding the defense instructions also closes the assignment to submissions`() {

        assignmentFixtures.createDefenseAssignment(defenseInstructionsReleased = true)
        assertTrue(assignmentRepository.findById(defenseAssignmentId).get().active)

        this.mvc.perform(post("/assignment/toggle-defense-instructions/${defenseAssignmentId}")
            .with(user(TEACHER_1)))
            .andExpect(status().isFound)

        val assignment = assignmentRepository.findById(defenseAssignmentId).get()
        assertFalse(assignment.defenseInstructionsReleased)
        assertFalse(assignment.active, "a defense whose instructions were hidden is over")
    }

    @Test
    fun `the defense instructions can't be released while the assignment is inactive`() {

        assignmentFixtures.createDefenseAssignment()
        assignmentRepository.findById(defenseAssignmentId).get().let {
            it.active = false
            assignmentRepository.save(it)
        }

        this.mvc.perform(post("/assignment/toggle-defense-instructions/${defenseAssignmentId}")
            .with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(flash().attribute("error", "Can't release the defense instructions of assignment " +
                    "${defenseAssignmentId} while it is inactive. Mark it active first."))

        val assignment = assignmentRepository.findById(defenseAssignmentId).get()
        assertFalse(assignment.defenseInstructionsReleased, "the second phase needs the first one to have happened")
        assertFalse(assignment.active)
    }

    @Test
    fun `deactivating the assignment also hides the defense instructions`() {

        assignmentFixtures.createDefenseAssignment(defenseInstructionsReleased = true)

        this.mvc.perform(post("/assignment/toggle-status/${defenseAssignmentId}")
            .with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(flash().attribute("message",
                "Assignment was marked inactive and its defense instructions were hidden"))

        val assignment = assignmentRepository.findById(defenseAssignmentId).get()
        assertFalse(assignment.active)
        assertFalse(assignment.defenseInstructionsReleased)
    }

    @Test
    fun `the defense instructions of an assignment that is not linked to another one can't be released`() {

        this.mvc.perform(post("/assignment/toggle-defense-instructions/${projectAssignmentId}")
            .with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(flash().attributeExists("error"))

        assertFalse(assignmentRepository.findById(projectAssignmentId).get().defenseInstructionsReleased)
    }

    @Test
    fun `the instructions of a defense assignment are only shown to the students after they are released`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(gitRepositoryFolder = "testJavaProj2")

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("instructionsFragment", nullValue()))

        startDefenseFor(STUDENT_1)

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("instructionsFragment", notNullValue()))
    }

    @Test
    fun `the teacher sees the instructions of a defense assignment before they are released`() {

        assignmentFixtures.createDefenseAssignment(gitRepositoryFolder = "testJavaProj2")

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("instructionsFragment", notNullValue()))
    }

    @Test
    fun `on the first phase, the submission is evaluated with the tests of the project assignment`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        // the defense assignment has teacher files of its own, but they are only used on the second phase
        assignmentFixtures.createDefenseAssignment(gitRepositoryFolder = "testJavaProj2")

        val submissionId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)

        val teacherTests = teacherTestFilesOf(submissionId)
        assertTrue(teacherTests.contains("TestTeacherProject.java"),
            "the checkpoint should be evaluated with the tests of ${projectAssignmentId}, but got ${teacherTests}")
        assertFalse(teacherTests.contains("TestProject1.java"),
            "the tests of the defense must not reach the student before the instructions are released")
    }

    @Test
    fun `on the second phase, the submission is evaluated with the tests of the defense assignment`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(gitRepositoryFolder = "testJavaProj2")
        startDefenseFor(STUDENT_1)

        val submissionId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)

        val teacherTests = teacherTestFilesOf(submissionId)
        assertTrue(teacherTests.contains("TestProject1.java"),
            "the defense should be evaluated with its own tests, but got ${teacherTests}")
    }

    /**
     * Takes [student] through the whole first phase - they submit the code they had submitted to the project
     * assignment and it passes the project's tests - and only then does the teacher release the instructions,
     * which is what puts the student on the second phase.
     */
    private fun startDefenseFor(student: User, checkpointProject: String = "projectOK") {
        submissionFixtures.uploadProject(checkpointProject, defenseAssignmentId, student)
        releaseDefenseInstructions()
    }


    @Test
    fun `a student who has not submitted the original code stays on the first phase`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(gitRepositoryFolder = "testJavaProj2")
        releaseDefenseInstructions()

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("defensePhase", equalTo(1)))
            .andExpect(model().attribute("defenseCheckpointStatus", equalTo(DefenseCheckpointStatus.NONE)))
            // the defense exercise is the instructions, so they can't be shown either
            .andExpect(model().attribute("instructionsFragment", nullValue()))
    }

    @Test
    fun `a student whose original code fails a test stays on the first phase`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(gitRepositoryFolder = "testJavaProj2")

        failTheNextBuild()
        submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)
        releaseDefenseInstructions()

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("defensePhase", equalTo(1)))
            .andExpect(model().attribute("defenseCheckpointStatus", equalTo(DefenseCheckpointStatus.FAILED)))
            .andExpect(model().attribute("instructionsFragment", nullValue()))
    }

    @Test
    fun `the upload page tells the student that their original code doesn't pass the tests`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()

        failTheNextBuild()
        val submissionId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)

        val page = this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertTrue(page.contains("does not pass all the tests of assignment ${projectAssignmentId}"),
            "the student should be told why the first phase didn't pass, but the page was ${page}")
        assertTrue(page.contains("buildReport/${submissionId}"),
            "the student should be able to see what is failing")
    }

    @Test
    fun `the upload page confirms an original submission that passed`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()

        val submissionId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)

        val page = this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("defenseCheckpointStatus", equalTo(DefenseCheckpointStatus.VALID)))
            .andReturn().response.contentAsString

        assertTrue(page.contains("Your original submission is done"),
            "the student should be told that the first phase is done, but the page was ${page}")
        assertFalse(page.contains("buildReport/${submissionId}"),
            "a checkpoint that passed has nothing to tell the student")
    }

    @Test
    fun `a submission of a student who is still on the first phase is another checkpoint`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(gitRepositoryFolder = "testJavaProj2")
        releaseDefenseInstructions()

        val submissionId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)

        assertTrue(savedSubmission(submissionId).defenseCheckpoint,
            "the student still has to submit the original code, even though the defense has started")
        assertTrue(teacherTestFilesOf(submissionId).contains("TestTeacherProject.java"),
            "the checkpoint must still be evaluated with the tests of ${projectAssignmentId}")
    }

    @Test
    fun `a student who is still on the first phase can't submit changed code`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(maxChangedLines = 100)
        releaseDefenseInstructions()

        val error = submissionFixtures.uploadProject("projectCheckstyleErrors", defenseAssignmentId, STUDENT_1,
            expectedResultMatcher = status().isInternalServerError())

        assertEquals("Your submission was rejected: this is not the code you submitted to assignment " +
                "${projectAssignmentId}. Download that submission and submit it without any change.", error)
    }

    @Test
    fun `the student moves to the second phase once the original code passes`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(gitRepositoryFolder = "testJavaProj2")
        startDefenseFor(STUDENT_1)

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("defensePhase", equalTo(2)))
            .andExpect(model().attribute("defenseCheckpointStatus", equalTo(DefenseCheckpointStatus.VALID)))
            .andExpect(model().attribute("instructionsFragment", notNullValue()))
    }

    @Test
    fun `the teacher can accept a checkpoint that failed, so that the student can defend it anyway`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment(gitRepositoryFolder = "testJavaProj2")

        // the group's project submission was already failing a test, so their original code fails it again
        failTheNextBuild()
        val checkpointId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)
        releaseDefenseInstructions()

        this.mvc.perform(post("/acceptCheckpoint/${checkpointId}").with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/buildReport/${checkpointId}"))

        assertTrue(savedSubmission(checkpointId).defenseCheckpointAccepted)

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("defensePhase", equalTo(2)))
            .andExpect(model().attribute("defenseCheckpointStatus", equalTo(DefenseCheckpointStatus.VALID)))
            .andExpect(model().attribute("instructionsFragment", notNullValue()))
    }

    @Test
    fun `the teacher can stop accepting a checkpoint`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()

        failTheNextBuild()
        val checkpointId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)
        releaseDefenseInstructions()

        this.mvc.perform(post("/acceptCheckpoint/${checkpointId}").with(user(TEACHER_1)))
            .andExpect(status().isFound)

        this.mvc.perform(post("/acceptCheckpoint/${checkpointId}").with(user(TEACHER_1)))
            .andExpect(status().isFound)

        assertFalse(savedSubmission(checkpointId).defenseCheckpointAccepted)

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("defensePhase", equalTo(1)))
            .andExpect(model().attribute("defenseCheckpointStatus", equalTo(DefenseCheckpointStatus.FAILED)))
    }

    @Test
    fun `the build report of a checkpoint offers the teacher the accept button`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()

        failTheNextBuild()
        val checkpointId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)

        val page = this.mvc.perform(get("/buildReport/${checkpointId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertTrue(page.contains("Accept as original code"),
            "the teacher should be able to accept the checkpoint from its build report")

        this.mvc.perform(post("/acceptCheckpoint/${checkpointId}").with(user(TEACHER_1)))
            .andExpect(status().isFound)

        val pageAfterAccepting = this.mvc.perform(get("/buildReport/${checkpointId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertTrue(pageAfterAccepting.contains("Stop accepting as original code"),
            "the teacher should be able to undo it")
        assertTrue(pageAfterAccepting.contains(">Accepted<"), "the checkpoint should be labelled as accepted")
    }

    @Test
    fun `a submission that is not a checkpoint can't be accepted`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()
        startDefenseFor(STUDENT_1)

        val defenseSubmissionId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)

        this.mvc.perform(post("/acceptCheckpoint/${defenseSubmissionId}").with(user(TEACHER_1)))
            .andExpect(status().isBadRequest)

        assertFalse(savedSubmission(defenseSubmissionId).defenseCheckpointAccepted)
    }

    @Test
    fun `deleting the checkpoint puts the student back on the first phase`() {

        submissionFixtures.uploadProject("projectOK", projectAssignmentId, STUDENT_1)
        assignmentFixtures.createDefenseAssignment()
        val checkpointId = submissionFixtures.uploadProject("projectOK", defenseAssignmentId, STUDENT_1)
        releaseDefenseInstructions()

        this.mvc.perform(post("/delete/${checkpointId}").with(user(TEACHER_1)))
            .andExpect(status().isFound)

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("defensePhase", equalTo(1)))
            .andExpect(model().attribute("defenseCheckpointStatus", equalTo(DefenseCheckpointStatus.NONE)))
            // a deleted submission is not the student's last submission anymore
            .andExpect(model().attribute("uploadSubmission", nullValue()))
    }

    @Test
    fun `a teacher reaches the second phase without any checkpoint`() {

        assignmentFixtures.createDefenseAssignment(gitRepositoryFolder = "testJavaProj2")
        releaseDefenseInstructions()

        this.mvc.perform(get("/upload/${defenseAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute("defensePhase", equalTo(2)))
            .andExpect(model().attribute("instructionsFragment", notNullValue()))
    }

    private fun releaseDefenseInstructions() {
        val assignment = assignmentRepository.findById(defenseAssignmentId).get()
        assignment.defenseInstructionsReleased = true
        assignmentRepository.save(assignment)
    }

    /**
     * The names of the teacher test files that ended up in the folder where [submissionId] was mavenized, that is,
     * the tests that the submission was actually evaluated with.
     */
    private fun teacherTestFilesOf(submissionId: String): List<String> {
        val mavenizedFolder = assignmentTeacherFiles.getProjectFolderAsFile(savedSubmission(submissionId), false)
        return File(mavenizedFolder, "src/test").walkTopDown().filter { it.isFile }.map { it.name }.toList()
    }
}
