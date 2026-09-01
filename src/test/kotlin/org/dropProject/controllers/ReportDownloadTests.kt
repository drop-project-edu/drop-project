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
import org.dropproject.extensions.formatDefault
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import net.lingala.zip4j.ZipFile
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.nio.file.Files
import org.dropproject.dao.*
import org.dropproject.data.*
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.hamcrest.Matchers.*
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class ReportDownloadTests : ReportTestBase() {

    @Test
    fun `download maven project`() {

        val submissionId = submissionFixtures.uploadProject("projectCompilationErrors", defaultAssignmentId, STUDENT_1)

        this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())

        this.mvc.perform(
            get("/downloadMavenProject/$submissionId").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(TEACHER_1))
        )
            .andExpect(header().string("Content-Disposition", "attachment; filename=student1_student2_mavenized.zip"))
            .andExpect(status().isOk)

    }

    @Test
    fun `download original project`() {

        val originalZipFile =
            zipService.createZipFromFolder("original", resourceLoader.getResource("file:src/test/sampleProjects/compact/java/projectCompilationErrors").file)
        originalZipFile.deleteOnExit()

        val submissionId = submissionFixtures.uploadProject("projectCompilationErrors", defaultAssignmentId, STUDENT_1)

        this.mvc.perform(
            get("/buildReport/$submissionId")
                .with(user(STUDENT_1))
        )
            .andExpect(status().isOk())

        val result = this.mvc.perform(get("/downloadOriginalProject/$submissionId").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .with(user(TEACHER_1)))
            .andExpect(header().string("Content-Disposition", "attachment; filename=student1_student2.zip"))
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result.response.contentAsByteArray
        assertArrayEquals(Files.readAllBytes(originalZipFile.toPath()), downloadedFileContent)

    }

    @Test
    fun `download original all`() {

        submissionFixtures.uploadProject("projectCompilationErrors", defaultAssignmentId, STUDENT_1)
        submissionFixtures.uploadProject("projectJUnitErrors", defaultAssignmentId, STUDENT_2,
            listOf(STUDENT_2.username to "Student 2")
        )

        val result = this.mvc.perform(
            get("/downloadOriginalAll/testJavaProj").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(TEACHER_1))
        )
            .andExpect(header().string("Content-Disposition", "attachment; filename=testJavaProj_last_submissions.zip"))
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result.response.contentAsByteArray
        val downloadedFile = File("result.zip")
        FileUtils.writeByteArrayToFile(downloadedFile, downloadedFileContent)
        val downloadedFileAsZipObject = ZipFile(downloadedFile)
        assertTrue(downloadedFileAsZipObject.fileHeaders.size > 15, "zip has more than 15 files")
        downloadedFile.delete()

        // TODO: check zip contents?
    }

    @Test
    fun `download mavenized all`() {

        submissionFixtures.uploadProject("projectCompilationErrors", defaultAssignmentId, STUDENT_1)
        submissionFixtures.uploadProject("projectJUnitErrors", defaultAssignmentId, STUDENT_2,
            listOf(STUDENT_2.username to "Student 2")
        )

        val result = this.mvc.perform(
            get("/downloadMavenizedAll/testJavaProj").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(TEACHER_1))
        )
            .andExpect(header().string("Content-Disposition", "attachment; filename=testJavaProj_last_mavenized_submissions.zip"))
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result.response.contentAsByteArray
        val downloadedFile = File("result.zip")
        FileUtils.writeByteArrayToFile(downloadedFile, downloadedFileContent)
        val downloadedFileAsZipObject = ZipFile(downloadedFile)
        assertEquals(45, downloadedFileAsZipObject.fileHeaders.size)
        downloadedFile.delete()

    }

    @Test
    fun `download original project from git submission`() {

        val assignment = assignmentRepository.findById("sampleJavaProject").get()
        assignment.submissionMethod = SubmissionMethod.GIT
        assignmentRepository.save(assignment)

        gitFixtures.connectToGitRepositoryAndBuildReport("sampleJavaProject",
            "git@github.com:drop-project-edu/sampleJavaSubmission.git", "student1"
        )

        val result = this.mvc.perform(get("/downloadOriginalProject/1").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .with(user(TEACHER_1)))
            .andExpect(header().string("Content-Disposition", "attachment; filename=student1_student2.zip"))
            .andExpect(status().isOk)
            .andReturn()

        assertTrue(result.response.contentLength > 5000)  // just to make sure we don't get an empty file
    }

    @Test
    fun `download maven project from git submission`() {

        val assignment = assignmentRepository.findById("sampleJavaProject").get()
        assignment.submissionMethod = SubmissionMethod.GIT
        assignmentRepository.save(assignment)

        gitFixtures.connectToGitRepositoryAndBuildReport("sampleJavaProject",
            "git@github.com:drop-project-edu/sampleJavaSubmission.git", "student1"
        )

        val result = this.mvc.perform(
            get("/downloadMavenProject/1").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(TEACHER_1))
        )
            .andExpect(header().string("Content-Disposition", "attachment; filename=student1_student2_mavenized.zip"))
            .andExpect(status().isOk)
            .andReturn()

        assertTrue(result.response.contentLength > 5000)   // just to make sure we don't get an empty file
    }

    @Test
    fun `download maven project for a previous git submission uses historical code and teacher files`() {

        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, "historicalTeacherFilesTest")
        try {
            // create an assignment with two commits: a copy of sampleJavaProject and then a change "MARKER-NEW"
            val (assignment, assignmentCommitOld, assignmentCommitNew) =
                assignmentFixtures.createHistoricalAssignment()
            // create a submission with two commits: "MARKER-STUDENT-CODE-1" and then "MARKER-STUDENT-CODE-2"
            val (gitSubmission, studentCommitA, studentCommitB) =
                gitFixtures.createHistoricalGitSubmission(assignment
                )

            val now = Date()
            // first submission associated with the first assignment commit and the first student commit
            val submission1 = submissionFixtures.saveHistoricalSubmission(gitSubmission, assignment, assignmentCommitOld, studentCommitA, now
            )
            // second submission associated with the second assignment commit and the second student commit
            val submission2 = submissionFixtures.saveHistoricalSubmission(gitSubmission, assignment, assignmentCommitNew, studentCommitB, Date(now.time + 60000)
            )

            assertTrue(submission2.submissionDate.after(submission1.submissionDate))

            // downloading the OLDER submission must trigger the on-demand rebuild path, reflecting
            // both the older student commit and the teacher files as they were at that time
            val zip1 = submissionFixtures.downloadMavenProjectZip(submission1.id)
            assertTrue(submissionFixtures.zipEntryContent(zip1, "Main.java").contains("MARKER-STUDENT-CODE-1"))
            assertFalse(submissionFixtures.zipEntryContent(zip1, "Main.java").contains("MARKER-STUDENT-CODE-2"))
            assertFalse(submissionFixtures.zipEntryContent(zip1, "TestTeacherProject.java").contains("MARKER-NEW"))

        } finally {
            FileUtils.deleteQuietly(assignmentFolder)
        }
    }

    @Test
    fun `download mavenized all from git submissions`() {

        val assignment = assignmentRepository.findById("sampleJavaProject").get()
        assignment.submissionMethod = SubmissionMethod.GIT
        assignmentRepository.save(assignment)

        // TODO should have more than one group submitting to properly test this
        gitFixtures.connectToGitRepositoryAndBuildReport("sampleJavaProject",
            "git@github.com:drop-project-edu/sampleJavaSubmission.git", "student1"
        )

        val result = this.mvc.perform(
            get("/downloadMavenizedAll/sampleJavaProject").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(TEACHER_1))
        )
            .andExpect(header().string("Content-Disposition", "attachment; filename=sampleJavaProject_last_mavenized_submissions.zip"))
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result.response.contentAsByteArray
        val downloadedFile = File("result.zip")
        FileUtils.writeByteArrayToFile(downloadedFile, downloadedFileContent)
        val downloadedFileAsZipObject = ZipFile(downloadedFile)
        assertEquals(22, downloadedFileAsZipObject.fileHeaders.size)
        downloadedFile.delete()

    }

    @Test
    fun `download submission asset`() {

        val submissionId = submissionFixtures.uploadProject("projectWithREADME", defaultAssignmentId, STUDENT_1)

        val result = this.mvc.perform(get("/buildReport/$submissionId/cross_red_icon.png")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result.response.contentAsByteArray
        assertEquals(199, downloadedFileContent.size)

        // inexistent file
        this.mvc.perform(get("/buildReport/$submissionId/other.png")
            .with(user(STUDENT_1)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `export csv`() {

        val now = Date()
        val nowStr = now.formatDefault()

        submissionFixtures.makeSeveralSubmissions(listOf(
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectOK",
                "projectInvalidStructure1"
            ), now)

        // mark all as final, otherwise the export will be empty
        val submissions = submissionRepository.findAll()
        for (submission in submissions) {
            submission.markedAsFinal = true
            submissionRepository.save(submission)
        }

        this.mvc.perform(
            get("/exportCSV/testJavaProj?ellapsed=false")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/csv"))
            .andExpect(
                content().string(
                    "submission id;student id;student name;project structure;compilation;code quality;teacher tests;hidden tests;submission date;# submissions;overdue\n" +
                            "1;student1;Student 1;NOK;;;;;${nowStr};1;false\n" +
                            "2;student2;Student 2;NOK;;;;;${nowStr};1;false\n" +
                            "3;student3;Student 3;OK;OK;OK;2;1;${nowStr};1;false\n" +
                            "4;student4;Student 4;NOK;;;;;${nowStr};1;false\n" +
                            "4;student5;Student 5;NOK;;;;;${nowStr};0;false\n"
                )
            )

    }

    @Test
    fun `export csv with student tests`() {

        val assignment = assignmentRepository.findById(defaultAssignmentId).get()
        assignment.acceptsStudentTests = true
        assignment.minStudentTests = 2
        assignmentRepository.save(assignment)

        val now = Date()
        val nowStr = now.formatDefault()

        submissionFixtures.makeSeveralSubmissions(listOf(
                "projectWith1StudentTest",
                "projectWith1StudentTest",
                "projectWith1StudentTest",
                "projectWith1StudentTest"
            ), now)

        // mark all as final, otherwise the export will be empty
        val submissions = submissionRepository.findAll()
        for (submission in submissions) {
            submission.markedAsFinal = true
            submissionRepository.save(submission)
        }

        this.mvc.perform(
            get("/exportCSV/testJavaProj?ellapsed=false")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/csv"))
            .andExpect(
                content().string(
                    """
                            |submission id;student id;student name;project structure;compilation;code quality;student tests;teacher tests;hidden tests;submission date;# submissions;overdue
                            |1;student1;Student 1;OK;OK;OK;1;2;1;${nowStr};1;false
                            |2;student2;Student 2;OK;OK;OK;1;2;1;${nowStr};1;false
                            |3;student3;Student 3;OK;OK;OK;1;2;1;${nowStr};1;false
                            |4;student4;Student 4;OK;OK;OK;1;2;1;${nowStr};1;false
                            |4;student5;Student 5;OK;OK;OK;1;2;1;${nowStr};0;false
                            |
                        """.trimMargin()
                )
            )

    }

    @Test
    fun `export csv with mandatory tests`() {

        val assignment = assignmentRepository.findById(defaultAssignmentId).get()

        // first, edit the assignment to add a mandatory test suffix
        assignment.mandatoryTestsSuffix = "_OBG"
        assignmentRepository.save(assignment)


        val now = Date()
        val nowStr = now.formatDefault()

        submissionFixtures.makeSeveralSubmissions(listOf(
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectOK",
                "projectInvalidStructure1"
            ), now)

        // mark all as final, otherwise the export will be empty
        val submissions = submissionRepository.findAll()
        for (submission in submissions) {
            submission.markedAsFinal = true
            submissionRepository.save(submission)
        }

        this.mvc.perform(
            get("/exportCSV/testJavaProj?ellapsed=false")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/csv"))
            .andExpect(
                content().string(
                    "submission id;student id;student name;project structure;compilation;code quality;teacher tests;" +
                            "hidden tests;submission date;# submissions;# mandatory;overdue\n" +
                            "1;student1;Student 1;NOK;;;;;${nowStr};1;0;false\n" +
                            "2;student2;Student 2;NOK;;;;;${nowStr};1;0;false\n" +
                            "3;student3;Student 3;OK;OK;OK;2;1;${nowStr};1;0;false\n" +
                            "4;student4;Student 4;NOK;;;;;${nowStr};1;0;false\n" +
                            "4;student5;Student 5;NOK;;;;;${nowStr};0;0;false\n"
                )
            )

    }

    @Test
    fun `export csv with git repository`() {

        val assignment = assignmentRepository.findById(defaultAssignmentId).get()
        assignment.submissionMethod = SubmissionMethod.GIT
        assignmentRepository.save(assignment)

        val now = Date()
        val nowStr = now.formatDefault()

        // Create entities for 3 students using a loop to reduce duplication
        val students = listOf(1, 2, 3)
        students.forEach { studentNum ->
            // Create ProjectGroup
            val group = ProjectGroup()
            projectGroupRepository.save(group)

            // Create Author with group reference (Author is the owning side of the relationship)
            val author = Author(name = "Student $studentNum", number = "student$studentNum", group = group)
            authorRepository.save(author)

            // Create GitSubmission with repository URL
            val gitSubmission = GitSubmission(
                assignmentId = defaultAssignmentId,
                submitterUserId = "student$studentNum",
                gitRepositoryUrl = "git@github.com:student$studentNum/project$studentNum.git",
                group = group
            )
            gitSubmissionRepository.save(gitSubmission)

            // Create Submission referencing GitSubmission
            val submission = Submission(
                gitSubmissionId = gitSubmission.id,
                submissionDate = now,
                submitterUserId = "student$studentNum",
                status = SubmissionStatus.VALIDATED.code,
                statusDate = now,
                assignmentId = defaultAssignmentId,
                assignmentGitHash = null,
                markedAsFinal = true
            )
            submission.group = group
            submissionRepository.save(submission)
        }

        this.mvc.perform(
            get("/exportCSV/testJavaProj?ellapsed=false")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/csv"))
            .andExpect(content().string(
                """
                    submission id;student id;student name;project structure;compilation;code quality;submission date;# submissions;overdue;repository_url
                    1;student1;Student 1;;;;${nowStr};1;false;https://github.com/student1/project1
                    2;student2;Student 2;;;;${nowStr};1;false;https://github.com/student2/project2
                    3;student3;Student 3;;;;${nowStr};1;false;https://github.com/student3/project3
                    
                    """.trimIndent()
            ))
            .andReturn()
    }
}
