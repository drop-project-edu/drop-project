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
import org.dropproject.TestUsers.TEACHER_1
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.dropproject.dao.*
import org.dropproject.repository.*
import org.hamcrest.CoreMatchers.*
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.nio.file.Files
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class UploadGroupTests : UploadTestBase() {

    @Test
    fun `upload in group and then in another group`() {

        val projectRoot = resourceLoader.getResource("file:src/test/sampleProjects/compact/java/projectCompilationErrors").file

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("numSubmissions", 0L))

        val submissionId1 = submissionFixtures.uploadProject("projectCompilationErrors", "testJavaProj", STUDENT_1)
        assertEquals(1, submissionId1.toLong(), "wrong submissionId")

        val submissionId2 = submissionFixtures.uploadProject("projectCompilationErrors", "testJavaProj", STUDENT_1)
        assertEquals(2, submissionId2.toLong(), "wrong submissionId")

        // let's change the AUTHORS
        val path = File(projectRoot, "AUTHORS.txt").toPath()
        val lines = Files.readAllLines(path)
        assertEquals("student1;Student 1", lines[0])
        assertEquals("student2;Student 2", lines[1])

        try {
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write("student3;Student 3")
            writer.close()

            val submissionId3 = submissionFixtures.uploadProject("projectCompilationErrors", "testJavaProj", STUDENT_1)
            assertEquals(3, submissionId3.toLong(), "wrong submissionId")

        } finally {
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write(lines[1])
            writer.close()
        }

        val authors = authorRepository.findAll(Sort.by(Sort.Direction.ASC, "userId"))
        assertEquals(4, authors.size)
        assertEquals(authors[0].userId, "student1")
        assertEquals(authors[0].group.id, 1)
        assertEquals(authors[1].userId, "student1")
        assertEquals(authors[1].group.id, 2)
        assertEquals(authors[2].userId, "student2")
        assertEquals(authors[2].group.id, 1)
        assertEquals(authors[3].userId, "student3")
        assertEquals(authors[3].group.id, 2)

        val submissions = submissionRepository.findAll()
        assertEquals(3, submissions.size)
        for (submission in submissions) {
            assertEquals("student1", submission.submitterUserId)
        }

    }

    @Test
    fun `upload by one element of the group and get report by the other element`() {

        // student1 makes a submission in name of the group (student1, student2)
        val submissionId = submissionFixtures.uploadProject("projectCompilationErrors", "testJavaProj", STUDENT_1,
            listOf(Pair("student1", "Student 1"), Pair("student2", "Student 2")))

        // student1 gets the upload form
        val reportResult = this.mvc.perform(get("/upload/testJavaProj")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-upload-form"))
            .andReturn()

        // student1 should see a "Get Last Report" button
        @Suppress("UNCHECKED_CAST")
        val lastSubmission = reportResult.modelAndView!!.modelMap["uploadSubmission"] as Submission?
        assertNotNull(lastSubmission)
        assertEquals(submissionId.toLong(), lastSubmission!!.id)

        // student2 gets the upload form
        val reportResult2 = this.mvc.perform(get("/upload/testJavaProj")
            .with(user(STUDENT_2)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-upload-form"))
            .andReturn()

        // student1 should see a "Get Last Report" button
        @Suppress("UNCHECKED_CAST")
        val lastSubmission2 = reportResult2.modelAndView!!.modelMap["uploadSubmission"] as Submission?
        assertNotNull(lastSubmission2)
        assertEquals(submissionId.toLong(), lastSubmission2!!.id)

    }

    @Test
    fun `upload project that violates the group restrictions of the assignment`() {

        val projectGroupRestrictions = ProjectGroupRestrictions(minGroupSize = 2, maxGroupSize = 2, exceptions = "student3")
        projectGroupRestrictionsRepository.save(projectGroupRestrictions)

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.projectGroupRestrictions = projectGroupRestrictions
        assignmentRepository.save(assignment)

        val error = submissionFixtures.uploadProject("projectOKIndividual", "testJavaProj", STUDENT_1,
            expectedResultMatcher = status().isInternalServerError())
        assertEquals("This assignment only accepts submissions from groups with 2..2 elements.", error)

        // add this student to exceptions
        projectGroupRestrictions.exceptions = "student1,student3"
        projectGroupRestrictionsRepository.save(projectGroupRestrictions)

        submissionFixtures.uploadProject("projectOKIndividual", "testJavaProj", STUDENT_1,
            expectedResultMatcher = status().isOk())
    }

    @Test
    fun `upload group project when one member is not in whitelist`() {

        // create whitelist with only student1
        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))

        // try to upload a group project with student1 and student2
        // projectOK has AUTHORS.txt with both student1 and student2
        val error = submissionFixtures.uploadProject("projectOK", "testJavaProj", STUDENT_1,
            expectedResultMatcher = status().isInternalServerError())
        assertEquals("Student student2 is not authorized for this assignment.", error)
    }

    @Test
    fun `upload group project as teacher`() {

        val projectGroupRestrictions = ProjectGroupRestrictions(minGroupSize = 2, maxGroupSize = 2)
        projectGroupRestrictionsRepository.save(projectGroupRestrictions)

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.projectGroupRestrictions = projectGroupRestrictions
        assignmentRepository.save(assignment)

        submissionFixtures.uploadProject("projectOKTeacher", "testJavaProj", TEACHER_1,
            expectedResultMatcher = status().isOk())
    }

    @Test
    fun `upload group with duplicate members`() {

        val projectRoot = resourceLoader.getResource("file:src/test/sampleProjects/compact/java/projectCompilationErrors").file

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("numSubmissions", 0L))

        // let's change the AUTHORS to have duplicate authors
        val path = File(projectRoot, "AUTHORS.txt").toPath()
        val lines = Files.readAllLines(path)
        assertEquals("student1;Student 1", lines[0])
        assertEquals("student2;Student 2", lines[1])

        try {
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write("student1;Student 1")
            writer.close()

            val zipFile = zipService.createZipFromFolder("test", projectRoot)
            zipFile.deleteOnExit()

            val multipartFile = MockMultipartFile("file", zipFile.name, "application/zip", zipFile.readBytes())

            this.mvc.perform(multipart("/upload")
                    .file(multipartFile)
                    .param("assignmentId", "testJavaProj")
                    .param("async", "false")
                    .with(user(STUDENT_1)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().json("{ \"error\": \"The AUTHORS.txt file is not correct. It contains duplicate authors.\"}"))

        } finally {
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write(lines[1])
            writer.close()
        }
    }
}
