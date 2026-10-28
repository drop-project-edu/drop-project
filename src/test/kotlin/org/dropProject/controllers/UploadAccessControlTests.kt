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

import org.dropproject.TestUsers.STUDENT_1
import org.dropproject.TestUsers.STUDENT_2
import org.dropproject.TestUsers.STUDENT_3
import org.dropproject.TestUsers.TEACHER_1
import org.dropproject.TestUsers.TEACHER_2
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.dropproject.FakeClock
import org.junit.jupiter.api.Assertions.*
import org.dropproject.dao.*
import org.dropproject.repository.*
import org.hamcrest.CoreMatchers.*
import org.hamcrest.collection.IsCollectionWithSize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.beans.factory.annotation.Autowired
import java.io.File
import java.time.LocalDateTime
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class UploadAccessControlTests : UploadTestBase() {

    @Autowired
    lateinit var fakeClock: FakeClock

    @Test
    fun `get upload page`() {

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-upload-form"))
                // .andExpect(model().attribute("uploadSubmission", null))  ?????


    }

    @Test
    fun `home redirects to active assignment only when you are in whitelist`() {

        // remove student1 from any white lists that might exist
        assigneeRepository.deleteByAuthorUserId(authorUserId = "student1")

        this.mvc.perform(get("/")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You don't have any assignments yet.")))

        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))

        this.mvc.perform(get("/").with(user(STUDENT_1)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/upload/testJavaProj"))
    }

    private fun allowOnly(allowedIps: String) {
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.allowedIps = allowedIps
        assignmentRepository.save(assignment)
    }

    @Test
    fun `students can only open an assignment from its allowed ips`() {

        allowOnly("10.12.33.*")

        // mock requests come from 127.0.0.1 unless told otherwise
        this.mvc.perform(get("/upload/testJavaProj").with(user(STUDENT_1)))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/access-denied"))

        this.mvc.perform(get("/upload/testJavaProj").with(user(STUDENT_1)).with { it.remoteAddr = "10.12.33.105"; it })
                .andExpect(status().isOk())

        // the teacher that owns it is not restricted
        this.mvc.perform(get("/upload/testJavaProj").with(user(TEACHER_1)))
                .andExpect(status().isOk())
    }

    @Test
    fun `the access denied page tells the student that the network is not allowed`() {

        allowOnly("10.12.33.*")

        this.mvc.perform(get("/access-denied").with(user(STUDENT_1))
                .requestAttr(org.springframework.security.web.WebAttributes.ACCESS_DENIED_403,
                    Assignment.NetworkNotAllowedException("testJavaProj", "87.196.72.232")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Network not allowed")))
                .andExpect(content().string(containsString("87.196.72.232")))
    }

    @Test
    fun `students can only submit to an assignment from its allowed ips`() {

        allowOnly("10.12.33.*")
        // the address is checked before the file is even looked at
        this.mvc.perform(multipart("/upload")
                .file(MockMultipartFile("file", "project.zip", "application/zip", ByteArray(0)))
                .param("assignmentId", "testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isForbidden())

        allowOnly("10.12.33.*, 127.0.0.*")
        submissionFixtures.uploadProject("projectOK", "testJavaProj", STUDENT_1,
            expectedResultMatcher = status().isOk())
    }

    private fun openOnlyDuring(submissionWindows: String) {
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionWindowsText = submissionWindows
        assignmentRepository.save(assignment)
    }

    @Test
    fun `students can only submit to an assignment during its submission windows`() {

        openOnlyDuring("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00")

        // the window is checked before the file is even looked at
        fakeClock.setNow(LocalDateTime.parse("2026-11-03T11:10:00"))
        this.mvc.perform(multipart("/upload")
                .file(MockMultipartFile("file", "project.zip", "application/zip", ByteArray(0)))
                .param("assignmentId", "testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("2026-11-03 14:00-16:00")))

        // the teacher that owns it is not restricted
        submissionFixtures.uploadProject("projectOK", "testJavaProj", TEACHER_1,
            authors = listOf("teacher1" to "Teacher 1"), expectedResultMatcher = status().isOk())

        fakeClock.setNow(LocalDateTime.parse("2026-11-03T14:30:00"))
        submissionFixtures.uploadProject("projectOK", "testJavaProj", STUDENT_1,
            expectedResultMatcher = status().isOk())
    }

    @Test
    fun `the assignment detail page shows the submission windows only when there are any`() {

        this.mvc.perform(get("/assignment/info/testJavaProj").with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Submission windows:"))))

        openOnlyDuring("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00")

        fakeClock.setNow(LocalDateTime.parse("2026-11-03T12:00:00"))
        this.mvc.perform(get("/assignment/info/testJavaProj").with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Submission windows:")))
                .andExpect(content().string(containsString("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00")))
                .andExpect(content().string(containsString("Closed now. Next window: 2026-11-03 14:00-16:00")))

        fakeClock.setNow(LocalDateTime.parse("2026-11-03T14:30:00"))
        this.mvc.perform(get("/assignment/info/testJavaProj").with(user(TEACHER_1)))
                .andExpect(content().string(containsString("Open now, until 16:00")))
    }

    @Test
    fun `the assignments list warns about the active assignments that are outside of their submission windows`() {

        openOnlyDuring("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00")

        fakeClock.setNow(LocalDateTime.parse("2026-11-03T14:30:00"))
        this.mvc.perform(get("/assignment/my").with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("students cannot submit right now"))))

        fakeClock.setNow(LocalDateTime.parse("2026-11-03T12:00:00"))
        this.mvc.perform(get("/assignment/my").with(user(TEACHER_1)))
                .andExpect(content().string(containsString(
                    "Active, but students cannot submit right now. Closed now. Next window: 2026-11-03 14:00-16:00")))

        // an inactive assignment doesn't accept submissions anyway, so there is nothing to warn about
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.active = false
        assignmentRepository.save(assignment)
        this.mvc.perform(get("/assignment/my").with(user(TEACHER_1)))
                .andExpect(content().string(not(containsString("students cannot submit right now"))))
    }

    @Test
    fun `the upload page tells the student when the submission window closes or when the next one opens`() {

        openOnlyDuring("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00")

        fakeClock.setNow(LocalDateTime.parse("2026-11-03T10:00:00"))
        this.mvc.perform(get("/upload/testJavaProj").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("submissionWindowCloses", "11:00"))
                .andExpect(model().attributeDoesNotExist("submissionWindowClosedMessage"))
                .andExpect(content().string(containsString("dpDropzone")))

        // during the tolerance, submissions are accepted, but the student is still told the time it closes
        fakeClock.setNow(LocalDateTime.parse("2026-11-03T11:05:00"))
        this.mvc.perform(get("/upload/testJavaProj").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("submissionWindowCloses", "11:00"))
                .andExpect(content().string(containsString("dpDropzone")))

        fakeClock.setNow(LocalDateTime.parse("2026-11-03T12:00:00"))
        this.mvc.perform(get("/upload/testJavaProj").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("submissionWindowClosedMessage", containsString("2026-11-03 14:00-16:00")))
                .andExpect(content().string(not(containsString("dpDropzone"))))

        // after the last window there is no next one
        fakeClock.setNow(LocalDateTime.parse("2026-11-03T16:10:00"))
        this.mvc.perform(get("/upload/testJavaProj").with(user(STUDENT_1)))
                .andExpect(model().attribute("submissionWindowClosedMessage", not(containsString("2026-11-03"))))

        // teachers can always submit, so they see the dropzone, but they are told what the students are seeing
        this.mvc.perform(get("/upload/testJavaProj").with(user(TEACHER_1)))
                .andExpect(model().attributeDoesNotExist("submissionWindowClosedMessage"))
                .andExpect(content().string(containsString("dpDropzone")))
                .andExpect(content().string(containsString("Closed. There are no more windows")))
    }

    @Test
    fun `access assignment with whitelist`() {

        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_2)))
                .andExpect(status().isForbidden())
    }

    @Test
    fun `upload project to non-accessible assignment because it's not in whitelist`() {

        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(User("someStudent", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT"))))))
                .andExpect(status().isForbidden())
    }

    @Test
    fun `student in exceptions list but not in allowlist can access and submit individually`() {

        // allowlist only contains student2, not student1
        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student2"))

        // student1 is exempt from the group size restriction, but is not in the allowlist
        val projectGroupRestrictions = ProjectGroupRestrictions(minGroupSize = 2, maxGroupSize = 2, exceptions = "student1")
        projectGroupRestrictionsRepository.save(projectGroupRestrictions)

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.projectGroupRestrictions = projectGroupRestrictions
        assignmentRepository.save(assignment)

        // student1 should still be able to access the assignment, even though it's not in the allowlist
        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())

        // student1 should be able to submit individually, bypassing the group size restriction
        submissionFixtures.uploadProject("projectOKIndividual", "testJavaProj", STUDENT_1,
            expectedResultMatcher = status().isOk())
    }

    @Test
    fun `student home page should show public assignments`() {

        try {// list assigments should return empty
            this.mvc.perform(get("/").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create assignment
            assignmentFixtures.createAndSetupAssignment("dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo, visibility = "PUBLIC",
                teacherId = "p1", activateRightAfterCloning = true
            )

            // list assignments should return one assignment
            val mvcResult = this.mvc.perform(get("/").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", IsCollectionWithSize.hasSize<Assignment>(1)))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView!!.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            Assertions.assertEquals("dummyAssignment4", assignment.id)
            Assertions.assertEquals("Dummy Assignment", assignment.name)
            Assertions.assertEquals(true, assignment.active)
            Assertions.assertEquals(AssignmentVisibility.PUBLIC, assignment.visibility)

        } finally {
            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").deleteRecursively()
            }
        }
    }

    @Test
    fun `teacher home page should show his own assignments and public assignments`() {

        val teacher = User("p1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {// list assigments should return empty
            this.mvc.perform(get("/").with(user(teacher)))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create assignment
            assignmentFixtures.createAndSetupAssignment("dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo, visibility = "PUBLIC",
                teacherId = "p1", activateRightAfterCloning = true
            )

            // list assignments should return one assignment
            val mvcResult = this.mvc.perform(get("/").with(user(teacher)))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", IsCollectionWithSize.hasSize<Assignment>(1)))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView!!.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            Assertions.assertEquals("dummyAssignment4", assignment.id)
            Assertions.assertEquals("Dummy Assignment", assignment.name)
            Assertions.assertEquals(true, assignment.active)
            Assertions.assertEquals(AssignmentVisibility.PUBLIC, assignment.visibility)

        } finally {
            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").deleteRecursively()
            }
        }
    }

    @Test
    fun `download public asset`() {
        val result = this.mvc.perform(get("/upload/testJavaProj/public/test.txt")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result.response.contentAsByteArray
        assertArrayEquals("1".toByteArray(), downloadedFileContent)

        // inexistent file
        this.mvc.perform(get("/upload/testJavaProj/public/test2.txt")
            .with(user(STUDENT_1)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `upload without authentication should return 401 unauthorized`() {
        // Create a mock multipart file by manually creating the zip
        val projectFolder = resourceLoader.getResource("file:src/test/sampleProjects/compact/java/projectOK").file
        val zipFile = zipService.createZipFromFolder("test", projectFolder)
        zipFile.deleteOnExit()
        val mockFile = MockMultipartFile("file", zipFile.name, "application/zip", zipFile.readBytes())

        // Try to upload without authentication - should return 401 Unauthorized
        // This tests that our JavaScript will detect the 401/403/405 status and redirect to login
        // Note: In production, this might return 405 Method Not Allowed due to Spring Security behavior
        this.mvc.perform(multipart("/upload")
            .file(mockFile)
            .param("assignmentId", "testJavaProj"))
            .andExpect(status().isUnauthorized) // 401 Unauthorized
    }

    @Test
    fun `teacher can submit to private assignment with whitelist`() {

        // 1. Vai buscar o assignment 'testJavaProj' (dono é teacher1)
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.visibility = AssignmentVisibility.PRIVATE   // Define como privadopo
        assignmentRepository.save(assignment)

        // 2. Adiciona o student1 à whitelist
        assigneeRepository.save(
            Assignee(
                assignmentId = "testJavaProj",
                authorUserId = "student1"
            )
        )

        // 3. Tenta submeter como teacher1 (que é o dono mas NÃO está na whitelist)
        val submissionId = submissionFixtures.uploadProject("projectOK",
            "testJavaProj",
            TEACHER_1,
            authors = listOf("teacher1" to "Teacher 1")
        )

        // 4. Verifica se obteve um ID de submissão válido (sucesso)
        try {
            submissionId.toLong()
        } catch (e: Exception) {
            fail<Unit>("Deveria ter conseguido submeter, mas deu erro: $submissionId")
        }

    }

    @Test
    fun `teacher2 cannot submit to private assignment without whitelist or authorized people`() {

        // 1. Tornar o assignment privado
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.visibility = AssignmentVisibility.PRIVATE
        assignmentRepository.save(assignment)

        // 2. Adicionar apenas student1 à whitelist
        assigneeRepository.save(
            Assignee(
                assignmentId = "testJavaProj",
                authorUserId = "student1"
            )
        )
        // 3. Tentar submeter → deve falhar com 403 Forbidden
        // Como o GlobalExceptionHandler retorna uma String simples (não JSON) no 403, 
        // o uploadProject vai lançar uma exceção ao tentar fazer o parse do JSON.
        try {
            submissionFixtures.uploadProject("projectOK",
                "testJavaProj",
                TEACHER_2,
                authors = listOf("teacher2" to "Teacher 2"),
                expectedResultMatcher = status().isForbidden
            )
            fail<Unit>("Deveria ter lançado uma exceção de parsing pois a resposta 403 não é JSON")
        } catch (e: Exception) {

        }
    }

    @Test
    fun `teacher2 can submit to private assignment when in authorized people but not in whitelist`() {

        // 1. Tornar o assignment privado
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.visibility = AssignmentVisibility.PRIVATE
        assignmentRepository.save(assignment)

        // 2. Adicionar apenas student1 à whitelist
        assigneeRepository.save(
            Assignee(
                assignmentId = "testJavaProj",
                authorUserId = "student1"
            )
        )

        // 3. Adicionar teacher2 à ACL (Lista de professores autorizados)
        assignmentACLRepository.save(
            AssignmentACL(
                assignmentId = "testJavaProj",
                userId = "teacher2"
            )
        )

        // 4. Tentar submeter → deve ter sucesso
        val submissionId = submissionFixtures.uploadProject("projectOK",
            "testJavaProj",
            TEACHER_2,
            authors = listOf("teacher2" to "Teacher 2")
        )

        // 5. Verifica se obteve um ID de submissão válido
        try {
            submissionId.toLong()
        } catch (e: Exception) {
            fail<Unit>("Deveria ter conseguido submeter (está na ACL), mas deu erro: $submissionId")
        }

        val submissionDB = submissionRepository.findById(submissionId.toLong()).get()
        assertEquals("teacher2", submissionDB.submitterUserId)
    }

    @Test
    fun `show assignment info button is only visible to the assignment's teachers`() {

        val infoLink = "/assignment/info/testJavaProj"

        // make it PUBLIC, so that any teacher can reach the upload page
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.visibility = AssignmentVisibility.PUBLIC
        assignmentRepository.save(assignment)

        // the owner sees the link
        val ownerPage = this.mvc.perform(get("/upload/testJavaProj").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertTrue(ownerPage.contains(infoLink), "the owner should see the info link")

        // a teacher that is neither the owner nor in the ACL doesn't
        val otherTeacherPage = this.mvc.perform(get("/upload/testJavaProj").with(user(TEACHER_2)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertFalse(otherTeacherPage.contains(infoLink), "teacher2 should not be offered a link that gives him an access denied page")

        // students never see it
        val studentPage = this.mvc.perform(get("/upload/testJavaProj").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertFalse(studentPage.contains(infoLink), "students should not see the info link")

        // ... but once teacher2 is added to the ACL, he does
        assignmentACLRepository.save(AssignmentACL(assignmentId = "testJavaProj", userId = "teacher2"))

        val aclTeacherPage = this.mvc.perform(get("/upload/testJavaProj").with(user(TEACHER_2)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertTrue(aclTeacherPage.contains(infoLink), "a teacher in the ACL should see the info link")
    }

    @Test
    fun `can't see other submissions`() {

        submissionFixtures.uploadProject("projectInvalidStructure1", "testJavaProj", STUDENT_1)

        this.mvc.perform(get("/buildReport/1")
                .with(user(STUDENT_3)))
                .andExpect(status().isForbidden())

    }
}
