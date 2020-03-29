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

import net.lingala.zip4j.core.ZipFile
import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ResourceLoader
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.dropProject.dao.Submission
import org.dropProject.data.SubmissionInfo
import java.io.File
import java.nio.file.Files
import org.dropProject.TestsHelper
import org.dropProject.dao.Assignment
import org.dropProject.dao.LeaderboardType
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.AssignmentRepository
import org.dropProject.repository.GitSubmissionRepository
import org.dropProject.repository.SubmissionRepository
import org.dropProject.services.ZipService


@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class ReportControllerTests {

    @Value("\${mavenizedProjects.rootLocation}")
    val mavenizedProjectsRootLocation: String = ""

    @Value("\${storage.rootLocation}")
    val submissionsRootLocation: String = ""

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    @Autowired
    lateinit var gitSubmissionRepository: GitSubmissionRepository

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    private lateinit var testsHelper: TestsHelper

    @Autowired
    private lateinit var zipService: ZipService

    val defaultAssignmentId = "testJavaProj"

    val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_2 = User("student2", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

    @Before
    fun setup() {
        var folder = File(mavenizedProjectsRootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
        folder.mkdirs()

        // create initial assignments
        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
                gitRepositoryFolder = "testJavaProj")
        assignmentRepository.save(assignment01)

        val assignment02 = Assignment(id = "sampleJavaProject", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = "sampleJavaProject")
        assignmentRepository.save(assignment02)
    }

    @After
    fun deleteMavenizedFolder() {
        var folder = File(mavenizedProjectsRootLocation)
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
    fun reportForMultipleSubmissions() {

        testsHelper.makeSeveralSubmissions("projectInvalidStructure1", mvc)

        val reportResult = this.mvc.perform(get("/report/testJavaProj")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val report = reportResult.modelAndView.modelMap["submissions"] as List<SubmissionInfo>

        assertEquals("report should have 4 lines", 4, report.size)
        assertEquals("student1,student2", report[0].projectGroup.authorsStr())
        assertEquals(1, report[0].allSubmissions.size)
        assertEquals("student2", report[1].projectGroup.authorsStr())
        assertEquals(2, report[1].allSubmissions.size)
        assertEquals("student3", report[2].projectGroup.authorsStr())
        assertEquals(1, report[2].allSubmissions.size)
        assertEquals("student1", report[3].projectGroup.authorsStr())
        assertEquals(1, report[3].allSubmissions.size)


    }



    @Test
    @DirtiesContext
    fun testMySubmissions() {

        testsHelper.uploadProject(this.mvc,"projectInvalidStructure1", defaultAssignmentId, STUDENT_1)

        val mySubmissionsResult = this.mvc.perform(get("/mySubmissions")
                .param("assignmentId", defaultAssignmentId)
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val submissions = mySubmissionsResult.modelAndView.modelMap["submissions"] as List<Submission>

        assertEquals("there should exist only one submission", 1, submissions.size)
    }

    @Test
    @DirtiesContext
    fun downloadMavenProject() {

        val submissionId = testsHelper.uploadProject(this.mvc,"projectCompilationErrors", defaultAssignmentId, STUDENT_1)

        this.mvc.perform(get("/buildReport/$submissionId"))
                .andExpect(status().isOk())

        this.mvc.perform(get("/downloadMavenProject/$submissionId").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(TEACHER_1)))
                .andExpect(header().string("Content-Disposition", "attachment; filename=student1_student2_mavenized.zip"))
                .andExpect(status().isOk)

    }

    @Test
    @DirtiesContext
    fun downloadOriginalProject() {

        val originalZipFile = zipService.createZipFromFolder("original", resourceLoader.getResource("file:src/test/sampleProjects/projectCompilationErrors").file)
        originalZipFile.file.deleteOnExit()

        val submissionId = testsHelper.uploadProject(this.mvc,"projectCompilationErrors", defaultAssignmentId, STUDENT_1)

        this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())

        val result = this.mvc.perform(get("/downloadOriginalProject/$submissionId").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=student1_student2.zip"))
                .andExpect(status().isOk)
                .andReturn()

        val downloadedFileContent = result.response.contentAsByteArray
        assertArrayEquals(Files.readAllBytes(originalZipFile.file.toPath()), downloadedFileContent)

    }

    @Test
    @DirtiesContext
    fun downloadOriginalAll() {

        testsHelper.uploadProject(this.mvc,"projectCompilationErrors", defaultAssignmentId, STUDENT_1)
        testsHelper.uploadProject(this.mvc,"projectJUnitErrors", defaultAssignmentId, STUDENT_2,
                listOf(STUDENT_2.username to "Student 2"))

        val result = this.mvc.perform(get("/downloadOriginalAll/testJavaProj").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(TEACHER_1)))
                .andExpect(header().string("Content-Disposition", "attachment; filename=testJavaProj_last_submissions.zip"))
                .andExpect(status().isOk)
                .andReturn()

        val downloadedFileContent = result.response.contentAsByteArray
        val downloadedFile = File("result.zip")
        FileUtils.writeByteArrayToFile(downloadedFile, downloadedFileContent)
        val downloadedFileAsZipObject = ZipFile(downloadedFile)
        assertTrue("zip has more than 15 files", downloadedFileAsZipObject.fileHeaders.size > 15)
        downloadedFile.delete()

        // TODO: check zip contents?
    }

    @Test
    @DirtiesContext
    fun downloadMavenizedAll() {

        testsHelper.uploadProject(this.mvc,"projectCompilationErrors", defaultAssignmentId, STUDENT_1)
        testsHelper.uploadProject(this.mvc,"projectJUnitErrors", defaultAssignmentId, STUDENT_2,
                listOf(STUDENT_2.username to "Student 2"))

        val result = this.mvc.perform(get("/downloadMavenizedAll/testJavaProj").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(TEACHER_1)))
                .andExpect(header().string("Content-Disposition", "attachment; filename=testJavaProj_last_mavenized_submissions.zip"))
                .andExpect(status().isOk)
                .andReturn()

        val downloadedFileContent = result.response.contentAsByteArray
        val downloadedFile = File("result.zip")
        FileUtils.writeByteArrayToFile(downloadedFile, downloadedFileContent)
        val downloadedFileAsZipObject = ZipFile(downloadedFile)
        assertEquals(41, downloadedFileAsZipObject.fileHeaders.size)
        downloadedFile.delete()

    }

    @Test
    @DirtiesContext
    fun downloadOriginalProjectFromGitSubmission() {

        val assignment = assignmentRepository.getOne("sampleJavaProject")
        assignment.submissionMethod = SubmissionMethod.GIT
        assignmentRepository.save(assignment)

        testsHelper.connectToGitRepositoryAndBuildReport(mvc, gitSubmissionRepository, "sampleJavaProject",
                "git@github.com:palves-ulht/sampleJavaSubmission.git", "student1")

        val result = this.mvc.perform(get("/downloadOriginalProject/1").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=student1_student2.zip"))
                .andExpect(status().isOk)
                .andReturn()

        assertTrue(result.response.contentLength > 5000)  // just to make sure we don't get an empty file
    }

    @Test
    @DirtiesContext
    fun downloadMavenProjectFromGitSubmission() {

        val assignment = assignmentRepository.getOne("sampleJavaProject")
        assignment.submissionMethod = SubmissionMethod.GIT
        assignmentRepository.save(assignment)

        testsHelper.connectToGitRepositoryAndBuildReport(mvc, gitSubmissionRepository, "sampleJavaProject",
                "git@github.com:palves-ulht/sampleJavaSubmission.git", "student1")

        val result = this.mvc.perform(get("/downloadMavenProject/1").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(TEACHER_1)))
                .andExpect(header().string("Content-Disposition", "attachment; filename=student1_student2_mavenized.zip"))
                .andExpect(status().isOk)
                .andReturn()

        assertTrue(result.response.contentLength > 5000)   // just to make sure we don't get an empty file
    }

    @Test
    @DirtiesContext
    fun downloadMavenizedAllFromGitSubmissions() {

        val assignment = assignmentRepository.getOne("sampleJavaProject")
        assignment.submissionMethod = SubmissionMethod.GIT
        assignmentRepository.save(assignment)

        // TODO should have more than one group submitting to properly test this
        testsHelper.connectToGitRepositoryAndBuildReport(mvc, gitSubmissionRepository, "sampleJavaProject",
                "git@github.com:palves-ulht/sampleJavaSubmission.git", "student1")

        val result = this.mvc.perform(get("/downloadMavenizedAll/sampleJavaProject").contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(TEACHER_1)))
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
    @DirtiesContext
    fun leaderboardNotAccessible() {
        this.mvc.perform(get("/leaderboard/testJavaProj")
                .with(user(TEACHER_1)))
                .andExpect(status().isForbidden())
    }

    @Test
    @DirtiesContext
    fun leaderboardOK() {

        val assignment = assignmentRepository.getOne(defaultAssignmentId)
        assignment.showLeaderBoard = true
        assignment.leaderboardType = LeaderboardType.ELLAPSED
        assignmentRepository.save(assignment)

        // we start with two authors
        val projectRoot = resourceLoader.getResource("file:src/test/sampleProjects/projectOK").file
        val path = File(projectRoot, "AUTHORS.txt").toPath()
        val lines = Files.readAllLines(path)
        assertEquals("student1;Student 1", lines[0])
        assertEquals("student2;Student 2", lines[1])

        val student3 = User("student3", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

        try {
            // upload five times, each time with a different author
            testsHelper.uploadProject(this.mvc,"projectOK", defaultAssignmentId, STUDENT_1)
            testsHelper.uploadProject(this.mvc,"projectOK", defaultAssignmentId, STUDENT_2,
                    authors = listOf(STUDENT_2.username to "Student 2"))
            testsHelper.uploadProject(this.mvc,"projectJUnitErrors", defaultAssignmentId, student3,
                    authors = listOf(student3.username to "Student 3"))
            testsHelper.uploadProject(this.mvc,"projectOK", defaultAssignmentId, STUDENT_2,
                    authors = listOf(STUDENT_2.username to "Student 2"))
            testsHelper.uploadProject(this.mvc,"projectOK", defaultAssignmentId, STUDENT_1,
                    authors = listOf(STUDENT_1.username to "Student 3"))

        } finally {
            // restore original AUTHORS.txt
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write(lines[1])
            writer.close()
        }

        val reportResult = this.mvc.perform(get("/leaderboard/testJavaProj")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val report = reportResult.modelAndView.modelMap["submissions"] as List<Submission>

        assertEquals("report should have 4 lines", 4, report.size)
        assertEquals("student3", report[3].group.authorsStr())  // this should be the last one because it has junit errors

        // the others should pass all tests and have ascending order of ellapsed time
        val others = report.dropLast(1)
        assertTrue("should pass all tests", others.all { it.teacherTests?.progress == 2 })

        val ellapsedList = others.map { it.ellapsed }
        val ellapsedSortedList = ellapsedList.sortedBy { it }
        assertArrayEquals(ellapsedSortedList.toTypedArray(), ellapsedList.toTypedArray())

    }

    @Test
    @DirtiesContext
    fun exportCSV() {

        testsHelper.makeSeveralSubmissions("projectInvalidStructure1", mvc)

        // mark all as final, otherwise the export will be empty
        val submissions = submissionRepository.findAll()
        for (submission in submissions) {
            if (submission.id != 4L) {  // this submission is a repeated submission from the same student
                submission.markedAsFinal = true
                submissionRepository.save(submission)
            }
        }

        this.mvc.perform(get("/exportCSV/testJavaProj")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/csv"))
                .andExpect(content().string(
                        "1;student1;Student 1;NOK;;;\n" +
                        "1;student2;Student 2;NOK;;;\n" +
                        "2;student2;Student 2;NOK;;;\n" +
                        "3;student3;Student 3;NOK;;;\n" +
                        "4;student1;Student 3;NOK;;;\n"))

    }

    @Test
    @DirtiesContext
    fun exportCSVWithStudentTests() {

        val assignment = assignmentRepository.getOne(defaultAssignmentId)
        assignment.acceptsStudentTests = true
        assignment.minStudentTests = 2
        assignmentRepository.save(assignment)

        testsHelper.makeSeveralSubmissions("projectWith1StudentTest", mvc)

        // mark all as final, otherwise the export will be empty
        val submissions = submissionRepository.findAll()
        for (submission in submissions) {
            if (submission.id != 4L) {  // this submission is a repeated submission from the same student
                submission.markedAsFinal = true
                submissionRepository.save(submission)
            }
        }

        this.mvc.perform(get("/exportCSV/testJavaProj?ellapsed=false")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/csv"))
                .andExpect(content().string(
                        """
                            |1;student1;Student 1;OK;OK;OK;1;2;1;
                            |1;student2;Student 2;OK;OK;OK;1;2;1;
                            |2;student2;Student 2;OK;OK;OK;1;2;1;
                            |3;student3;Student 3;OK;OK;OK;1;2;1;
                            |4;student1;Student 3;OK;OK;OK;1;2;1;
                            |
                        """.trimMargin()))

    }

}



