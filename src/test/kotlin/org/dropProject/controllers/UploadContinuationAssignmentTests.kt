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
import org.dropproject.FakeBuild
import org.dropproject.FakeBuildRunner
import org.dropproject.TestUsers.STUDENT_1
import org.dropproject.TestUsers.STUDENT_3
import org.dropproject.TestUsers.TEACHER_1
import org.dropproject.dao.AssignmentACL
import org.dropproject.dao.ProjectGroupRestrictions
import org.dropproject.dao.SubmissionStructure
import org.dropproject.services.ReferenceSolutionService
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matchers.hasProperty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Tests for the "continuation" assignments, that is, the next part of a multi-part project, which each group starts
 * from the code they submitted to the previous part (or from its reference solution, if they have none) and which
 * must be done by the same groups.
 *
 * Like [UploadDefenseAssignmentTests], they replace the (slow) Maven build with [FakeBuildRunner], since nothing here
 * depends on the results of the build, so this class also runs with 'mvn test -Pfast'.
 */
@DropProjectIntegrationTest
class UploadContinuationAssignmentTests : UploadTestBase() {

    @Autowired
    lateinit var fakeBuildRunner: FakeBuildRunner

    @Autowired
    lateinit var referenceSolutionService: ReferenceSolutionService

    val baseAssignmentId = "testJavaProj"
    val continuationId = "testJavaProjPart2"

    private val referenceSolutionsFolder get() = File(dropProjectProperties.storage.rootLocation, "reference-solutions")

    @BeforeEach
    fun setupFakeBuild() {
        fakeBuildRunner.fakeNextBuilds(FakeBuild(mapOf(
            "org.dropProject.sampleAssignments.testProj.TestTeacherProject" to
                    listOf("testFuncaoParaTestar", "testFuncaoLentaParaTestar"),
            "org.dropProject.sampleAssignments.testProj.TestTeacherHiddenProject" to
                    listOf("testFuncaoParaTestarQueNaoApareceAosAlunos"))))
        referenceSolutionsFolder.deleteRecursively()
    }

    @AfterEach
    fun cleanReferenceSolutions() {
        referenceSolutionsFolder.deleteRecursively()
    }

    private fun uploadPage(student: User) =
        this.mvc.perform(get("/upload/${continuationId}").with(user(student)))
            .andExpect(status().isOk)

    private fun downloadReferenceSolution(user: User) =
        this.mvc.perform(get("/upload/${continuationId}/reference-solution").with(user(user)))

    private fun zipEntries(zip: ByteArray): List<String> {
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { zipIn ->
            while (true) {
                val entry = zipIn.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries.add(entry.name)
                }
            }
        }
        return entries
    }

    @Test
    fun `a student starts from the submission of their group to the previous part, until they submit`() {

        val baseSubmissionId = submissionFixtures.uploadProject("projectOK", baseAssignmentId, STUDENT_1)
        assignmentFixtures.createContinuationAssignment()

        uploadPage(STUDENT_1)
            .andExpect(model().attribute("continuationStart", hasProperty<Any>("baseSubmission",
                hasProperty<Any>("id", equalTo(baseSubmissionId.toLong())))))
            .andExpect(content().string(containsString("/downloadOriginalProject/${baseSubmissionId}")))

        // a submission to the continuation is a regular submission: no checkpoint, no divergence measured
        val submissionId = submissionFixtures.uploadProject("projectOK", continuationId, STUDENT_1)
        val submission = submissionRepository.findById(submissionId.toLong()).get()
        assertFalse(submission.defenseCheckpoint)
        assertNull(submission.baseDivergenceLines)

        // from then on, they continue from their own submissions
        uploadPage(STUDENT_1)
            .andExpect(model().attributeDoesNotExist("continuationStart"))
    }

    @Test
    fun `a student without a submission to the previous part starts from its reference solution`() {

        assignmentFixtures.createContinuationAssignment()

        uploadPage(STUDENT_1)
            .andExpect(model().attribute("continuationStart", hasProperty<Any>("baseSubmission", nullValue())))
            .andExpect(content().string(containsString("/upload/${continuationId}/reference-solution")))

        val zip = downloadReferenceSolution(STUDENT_1)
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=${baseAssignmentId}-reference-solution.zip"))
            .andReturn().response.contentAsByteArray

        // the compact structure has the sources directly under src, and the teacher's tests are never included
        assertEquals(listOf("src/org/dropProject/sampleAssignments/testProj/Main.java"), zipEntries(zip))

        // once they submit, the reference solution is no longer theirs to download
        submissionFixtures.uploadProject("projectOK", continuationId, STUDENT_1)
        uploadPage(STUDENT_1).andExpect(model().attributeDoesNotExist("continuationStart"))
        downloadReferenceSolution(STUDENT_1).andExpect(status().isForbidden)
    }

    @Test
    fun `a student who has their own code to continue can't download the reference solution`() {

        submissionFixtures.uploadProject("projectOK", baseAssignmentId, STUDENT_1)
        assignmentFixtures.createContinuationAssignment()

        downloadReferenceSolution(STUDENT_1).andExpect(status().isForbidden)
    }

    @Test
    fun `the teachers can always download the reference solution, in the structure of the continuation`() {

        submissionFixtures.uploadProject("projectOK", baseAssignmentId, STUDENT_1)
        assignmentFixtures.createContinuationAssignment(submissionStructure = SubmissionStructure.MAVEN)

        uploadPage(TEACHER_1)
            .andExpect(model().attribute("continuationStart", hasProperty<Any>("baseSubmission", nullValue())))

        val zip = downloadReferenceSolution(TEACHER_1)
            .andExpect(status().isOk)
            .andReturn().response.contentAsByteArray

        assertEquals(listOf("src/main/java/org/dropProject/sampleAssignments/testProj/Main.java"), zipEntries(zip))
    }

    @Test
    fun `the reference solution is only built once for each commit of the previous part`() {

        val continuation = assignmentFixtures.createContinuationAssignment()
        val base = assignmentRepository.findById(baseAssignmentId).get()

        val firstZip = referenceSolutionService.referenceSolutionZip(continuation, base)
        firstZip.setLastModified(1_000_000_000L)

        // served from the cache, not built again
        val secondZip = referenceSolutionService.referenceSolutionZip(continuation, base)
        assertEquals(firstZip, secondZip)
        assertEquals(1_000_000_000L, secondZip.lastModified())

        // a refresh of the repository of the previous part builds a new one, and the outdated one is deleted
        base.gitCurrentHash = "anotherhash"
        val newZip = referenceSolutionService.referenceSolutionZip(continuation, base)
        assertNotEquals(firstZip, newZip)
        assertTrue(newZip.exists())
        assertFalse(firstZip.exists(), "the zip of the previous commit should have been deleted")
    }

    @Test
    fun `the reference solution is not available on assignments that are not continuations`() {

        assignmentFixtures.createDefenseAssignment(id = continuationId)

        downloadReferenceSolution(TEACHER_1).andExpect(status().isNotFound)
    }

    @Test
    fun `the group must remain the same as in the previous part`() {

        // student1 and student2 did the previous part together
        submissionFixtures.uploadProject("projectOK", baseAssignmentId, STUDENT_1)
        assignmentFixtures.createContinuationAssignment()

        val error = submissionFixtures.uploadProject("projectOK", continuationId, STUDENT_1,
            authors = listOf("student1" to "Student 1"),
            expectedResultMatcher = status().isInternalServerError())

        assertEquals("student1 submitted to ${baseAssignmentId} in the group student1, student2, which must " +
                "remain the same. If the group changed, talk to your teacher.", error)
        assertTrue(submissionRepository.findAll().none { it.assignmentId == continuationId })

        // the same group is accepted
        submissionFixtures.uploadProject("projectOK", continuationId, STUDENT_1)
    }

    @Test
    fun `a new member can't join a group of the previous part`() {

        submissionFixtures.uploadProject("projectOK", baseAssignmentId, STUDENT_1)
        assignmentFixtures.createContinuationAssignment()

        submissionFixtures.uploadProject("projectOK", continuationId, STUDENT_1,
            authors = listOf("student1" to "Student 1", "student2" to "Student 2", "student3" to "Student 3"),
            expectedResultMatcher = status().isInternalServerError())
    }

    @Test
    fun `the students in the group exceptions may change group`() {

        submissionFixtures.uploadProject("projectOK", baseAssignmentId, STUDENT_1)
        assignmentFixtures.createContinuationAssignment(exceptions = "student1")

        submissionFixtures.uploadProject("projectOK", continuationId, STUDENT_1,
            authors = listOf("student1" to "Student 1"))
    }

    @Test
    fun `students without a submission to the previous part may form a new group`() {

        assignmentFixtures.createContinuationAssignment()

        submissionFixtures.uploadProject("projectOK", continuationId, STUDENT_3,
            authors = listOf("student3" to "Student 3", "student4" to "Student 4"))
    }

    @Test
    fun `picking the previous part of a continuation fills in its teachers, group size and exceptions`() {

        val base = assignmentRepository.findById(baseAssignmentId).get()
        base.projectGroupRestrictions = projectGroupRestrictionsRepository.save(
            ProjectGroupRestrictions(minGroupSize = 2, maxGroupSize = 2, exceptions = "student9"))
        assignmentRepository.save(base)
        assignmentACLRepository.save(AssignmentACL(assignmentId = baseAssignmentId, userId = "teacher2"))

        val fragment = this.mvc.perform(get("/assignment/base-assignment-derived-fields")
            .param("baseAssignmentId", baseAssignmentId)
            .param("baseAssignmentKind", "CONTINUATION")
            .param("assignmentId", "")
            .with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertTrue(fragment.contains("org.dropProject.sampleAssignments.testProj"), "the package is the same")
        assertTrue(Regex("""id="acl"[^>]*value="teacher2"""").containsMatchIn(fragment),
            "the teachers of the previous part should be filled in, but the fragment was ${fragment}")
        assertTrue(Regex("""id="minGroupSize"[^>]*value="2"""").containsMatchIn(fragment))
        assertTrue(Regex("""id="maxGroupSize"[^>]*value="2"""").containsMatchIn(fragment))
        assertTrue(fragment.contains(">student9</textarea>"), "the exceptions should be filled in")
        assertFalse(fragment.contains("changes 0 lines"), "the size of the change is only for defenses")
    }
}
