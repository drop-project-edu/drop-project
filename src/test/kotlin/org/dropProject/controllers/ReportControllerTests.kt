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
import java.io.File
import java.nio.file.Files
import org.dropProject.TestsHelper
import org.dropProject.dao.*
import org.dropProject.data.*
import org.dropProject.extensions.formatDefault
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.AssignmentRepository
import org.dropProject.repository.AssignmentTestMethodRepository
import org.dropProject.repository.GitSubmissionRepository
import org.dropProject.repository.SubmissionRepository
import org.dropProject.services.AssignmentService
import org.dropProject.services.ZipService
import org.hamcrest.Matchers.contains
import java.util.*
import kotlin.collections.LinkedHashMap


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
    lateinit var assignmentTestMethodRepository: AssignmentTestMethodRepository

    @Autowired
    private lateinit var testsHelper: TestsHelper

    @Autowired
    private lateinit var zipService: ZipService

    @Autowired
    private lateinit var assignmentService: AssignmentService

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

        assignmentTestMethodRepository.save(AssignmentTestMethod(assignmentId = "testJavaProj",
                testClass = "TestTeacherProject", testMethod = "testFuncaoParaTestar"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignmentId = "testJavaProj",
                testClass = "TestTeacherProject", testMethod = "testFuncaoLentaParaTestar"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignmentId = "testJavaProj",
                testClass = "TestTeacherHiddenProject", testMethod = "testFuncaoParaTestarQueNaoApareceAosAlunos"))

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

        testsHelper.makeSeveralSubmissions(
                listOf("projectInvalidStructure1",
                        "projectInvalidStructure1",
                        "projectInvalidStructure1",
                        "projectInvalidStructure1",
                        "projectInvalidStructure1"), mvc)

        val reportResult = this.mvc.perform(get("/report/testJavaProj")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val report = reportResult.modelAndView.modelMap["submissions"] as List<SubmissionInfo>

        assertEquals("report should have 4 lines", 4, report.size)
        assertEquals("student1", report[0].projectGroup.authorsStr())
        assertEquals(2, report[0].allSubmissions.size)
        assertEquals("student2", report[1].projectGroup.authorsStr())
        assertEquals(1, report[1].allSubmissions.size)
        assertEquals("student3", report[2].projectGroup.authorsStr())
        assertEquals(1, report[2].allSubmissions.size)
        assertEquals("student4,student5", report[3].projectGroup.authorsStr())
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

        val now = Date()
        val nowStr = now.formatDefault()

        testsHelper.makeSeveralSubmissions(
                listOf("projectInvalidStructure1",
                        "projectInvalidStructure1",
                        "projectOK",
                        "projectInvalidStructure1"), mvc, now)

        // mark all as final, otherwise the export will be empty
        val submissions = submissionRepository.findAll()
        for (submission in submissions) {
            submission.markedAsFinal = true
            submissionRepository.save(submission)
        }

        this.mvc.perform(get("/exportCSV/testJavaProj?ellapsed=false")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/csv"))
                .andExpect(content().string(
                        "submission id;student id;student name;project structure;compilation;code quality;teacher tests;hidden tests;submission date\n" +
                        "1;student1;Student 1;NOK;;;;;${nowStr}\n" +
                        "2;student2;Student 2;NOK;;;;;${nowStr}\n" +
                        "3;student3;Student 3;OK;OK;OK;2;1;${nowStr}\n" +
                        "4;student5;Student 5;NOK;;;;;${nowStr}\n" +
                        "4;student4;Student 4;NOK;;;;;${nowStr}\n"))

    }

    @Test
    @DirtiesContext
    fun exportCSVWithStudentTests() {

        val assignment = assignmentRepository.getOne(defaultAssignmentId)
        assignment.acceptsStudentTests = true
        assignment.minStudentTests = 2
        assignmentRepository.save(assignment)

        val now = Date()
        val nowStr = now.formatDefault()

        testsHelper.makeSeveralSubmissions(
                listOf("projectWith1StudentTest",
                        "projectWith1StudentTest",
                        "projectWith1StudentTest",
                        "projectWith1StudentTest"), mvc, now)

        // mark all as final, otherwise the export will be empty
        val submissions = submissionRepository.findAll()
        for (submission in submissions) {
            submission.markedAsFinal = true
            submissionRepository.save(submission)
        }

        this.mvc.perform(get("/exportCSV/testJavaProj?ellapsed=false")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/csv"))
                .andExpect(content().string(
                        """
                            |submission id;student id;student name;project structure;compilation;code quality;student tests;teacher tests;hidden tests;submission date
                            |1;student1;Student 1;OK;OK;OK;1;2;1;${nowStr}
                            |2;student2;Student 2;OK;OK;OK;1;2;1;${nowStr}
                            |3;student3;Student 3;OK;OK;OK;1;2;1;${nowStr}
                            |4;student5;Student 5;OK;OK;OK;1;2;1;${nowStr}
                            |4;student4;Student 4;OK;OK;OK;1;2;1;${nowStr}
                            |
                        """.trimMargin()))

    }

    @Test
    @DirtiesContext
    fun testTestMatrix() {
        testsHelper.uploadProject(this.mvc, "projectJUnitErrors", defaultAssignmentId, STUDENT_1)

        val reportResult = this.mvc.perform(get("/testMatrix/${defaultAssignmentId}")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val tests = reportResult.modelAndView.modelMap["tests"] as LinkedHashMap<String,Int>
        assertEquals(3, tests.size)
        assertThat(tests.keys.map { "${it}->${tests[it]}" }, contains("testFuncaoParaTestar:TestTeacherProject->0",
                "testFuncaoLentaParaTestar:TestTeacherProject->1",
                "testFuncaoParaTestarQueNaoApareceAosAlunos:TestTeacherHiddenProject->0"))
    }

    /**
     * Tested function: ReportController.getSignaledGroupsOrSubmissions() via MVC
     * Test scenario:
     * - 3 students perform submissions.
     * - Two of them fail the same 2 tests.
     * - The other does not fail any test.
     * Expectations:
     * - The MVC controller function should place in the Model in the model a List of size 1.
     * - The only element of the list should be a GroupedProjectGroup with 2 groups (one for each student)
     * and 2 failed tests.
     */
    @Test
    @DirtiesContext
    fun testSignalledGroups() {
        val student3 = User("student3", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

        testsHelper.uploadProject(this.mvc, "projectJUnitErrors", defaultAssignmentId, STUDENT_1)
        testsHelper.uploadProject(this.mvc,"projectJUnitErrors", defaultAssignmentId, STUDENT_2,
                authors = listOf(STUDENT_2.username to "Student 2"))
        testsHelper.uploadProject(this.mvc,"projectOK", defaultAssignmentId, student3,
                authors = listOf(student3.username to "Student 3"))

        val reportResult = this.mvc.perform(get("/signalledSubmissions/${defaultAssignmentId}")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val signalledGroups = reportResult.modelAndView.modelMap["signalledGroups"] as List<GroupedProjectGroups>

        assert(signalledGroups != null)
        assert(signalledGroups.size == 1)
        assert(signalledGroups.get(0).groups.size == 2)

        assert(signalledGroups.get(0).failedTestNames.size == 2)
        var expectedFailedTests = mutableListOf("testFuncaoParaTestar", "testFuncaoParaTestarQueNaoApareceAosAlunos")
        assert(signalledGroups.get(0).failedTestNames.containsAll(expectedFailedTests))
    }

    /**
     * Tested function: ReportController.getSignaledGroupsOrSubmissions() via MVC
     * Test scenario:
     * - 2 students perform submissions.
     * - One of them fails tests.
     * - The other does not fail any test.
     * Expectations:
     * - The MVC controller function should place in the Model:
     * -- a List of size 0; and
     * -- a String with a message saying that there are no signalled groups.
     */
    @Test
    @DirtiesContext
    fun testSignalledGroupsViaMVC_NoGroupsAreSignalled() {
        val student3 = User("student3", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

        testsHelper.uploadProject(this.mvc, "projectJUnitErrors", defaultAssignmentId, STUDENT_1)
        testsHelper.uploadProject(this.mvc,"projectOK", defaultAssignmentId, student3,
                authors = listOf(student3.username to "Student 3"))

        val reportResult = this.mvc.perform(get("/signalledSubmissions/${defaultAssignmentId}")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val message = reportResult.modelAndView.modelMap["message"] as String
        val list = reportResult.modelAndView.modelMap["signalledGroups"] as List<GroupedProjectGroups>

        assert(message != null)
        assertEquals("No groups identified as similar", message)
        assert(list.isEmpty())
    }

    /**
     * This function creates "test data" that will be used in multiple tests of
     * the function AssignmentService.groupGroupsByFailures()
     */
    private fun testDataForGroupGroupsByFailures(): List<ProjectGroup> {
        val g1 = ProjectGroup(1)
        g1.authors.add(Author(1, "BC", "BC"))
        val g2 = ProjectGroup(2)
        g2.authors.add(Author(2, "RCC", "RCC"))
        val g3 = ProjectGroup(3)
        g3.authors.add(Author(3, "PA", "PA"))
        val g4 = ProjectGroup(4)
        g4.authors.add(Author(4, "IL", "IL"))
        val g5 = ProjectGroup(5)
        g5.authors.add(Author(5, "RP", "RP"))
        return mutableListOf<ProjectGroup>(g1, g2, g3, g4, g5)
    }

    /**
     * Tested function: AssignmentService.groupGroupsByFailures()
     * Test scenario:
     * - 3 student/project groups
     * - All have different test failures
     * - The groupGroupsByFailures() function will return a List with size 0
     */
    @Test
    fun testGroupGroupsByFailures_NoGroupsAreSignalled() {
        val projectGroups = testDataForGroupGroupsByFailures();
        var g1 = projectGroups[0];
        var g2 = projectGroups[1];
        var g3 = projectGroups[2];

        val failuresByGroup : HashMap<ProjectGroup, ArrayList<String>> = HashMap()

        // in this scenario, the 3 ProjectoGroups have distinct failures, so nothing will be Signalled
        failuresByGroup.put(g1, mutableListOf("Test001", "Test002") as ArrayList<String>)
        failuresByGroup.put(g2, mutableListOf("Test001") as ArrayList<String>)
        failuresByGroup.put(g3, mutableListOf("Test002") as ArrayList<String>)

        val expected = mutableListOf<GroupedProjectGroups>()
        val result = assignmentService.groupGroupsByFailures(failuresByGroup)

        assert(expected != null)
        assert(result.size == 0)
    }

    /**
     * Tested function: AssignmentService.groupGroupsByFailures()
     * Test scenario:
     * - 3 student/project groups
     * - All have the same failures, but one of the groups has the failures in different order
     * Expectations:
     * - The groupGroupsByFailures() function will return a List with size 1
     * - The only element of the returned list will contain 3 student groups and 2 failed tests
     */
    @Test
    fun testGroupGroupsByFailures_AllGroupsAreSignalled() {
        val projectGroups = testDataForGroupGroupsByFailures();
        var g1 = projectGroups[0];
        var g2 = projectGroups[1];
        var g3 = projectGroups[2];

        val failuresByGroup : HashMap<ProjectGroup, ArrayList<String>> = HashMap()

        failuresByGroup.put(g1, mutableListOf("Test001", "Test002") as ArrayList<String>)
        failuresByGroup.put(g2, mutableListOf("Test001", "Test002") as ArrayList<String>)
        // the order of the failures should not influence the "signalling"
        failuresByGroup.put(g3, mutableListOf("Test002", "Test001") as ArrayList<String>)

        var group1 = GroupedProjectGroups(mutableListOf<ProjectGroup>(g1, g2, g3), mutableListOf("Test001", "Test002"))
        val result = assignmentService.groupGroupsByFailures(failuresByGroup)

        assert(result != null)
        assert(result.size == 1)

        // the order of each group (g1, g2 and g3) in the result might change, so we check
        // the list size and the individual existence of each group
        assert(result.get(0).groups.size == 3)
        assert(result.get(0).groups.contains(g1));
        assert(result.get(0).groups.contains(g2));
        assert(result.get(0).groups.contains(g3));

        assert(result.get(0).failedTestNames.contains("Test001"))
        assert(result.get(0).failedTestNames.contains("Test002"))
    }

    /**
     * Tested function: AssignmentService.groupGroupsByFailures()
     * Test scenario:
     * - 5 student/project groups
     * - groups 1 and 3 have the same failures
     * - group 2 has failures but the list is unlike any other group
     * - groups 4 and 5 have hte same failures (in different order)
     * Expectations:
     * - The groupGroupsByFailures() function will return a List with size 2
     * - The list will have two GroupedProjectGroups objects:
     * -- One with groups 1 and 3
     * -- One with groups 4 and 5
     */
    @Test
    fun testGroupGroupsByFailures_MoreComplexScenario() {

        val projectGroups = testDataForGroupGroupsByFailures();
        var g1 = projectGroups[0];
        var g2 = projectGroups[1];
        var g3 = projectGroups[2];
        var g4 = projectGroups[3];
        var g5 = projectGroups[4];

        val failuresByGroup : HashMap<ProjectGroup, ArrayList<String>> = HashMap()

        failuresByGroup.put(g1, mutableListOf("Test001", "Test002") as ArrayList<String>)
        failuresByGroup.put(g2, mutableListOf("Test001") as ArrayList<String>)
        failuresByGroup.put(g3, mutableListOf("Test001", "Test002") as ArrayList<String>)

        // same failed tests, different order
        failuresByGroup.put(g4, mutableListOf("Test001", "Test003") as ArrayList<String>);
        failuresByGroup.put(g5, mutableListOf("Test003", "Test001") as ArrayList<String>);

        // the ProjectGroups with the same failures are:
        //    (1 and 3) ; (2) ; (4 and 5)
        // however, (2) will be ignored because there is no "suspicion"
        // as such, the expected groups are:
        //    (1 and 3) ; (4 and 5)
        val group1 = GroupedProjectGroups(mutableListOf(g1, g3), mutableListOf("Test001", "Test002"))
        val group3 = GroupedProjectGroups(mutableListOf(g4, g5), mutableListOf("Test001", "Test003"))

        //val expected = mutableListOf<GroupedProjectGroups>(group1, group2, group3)
        val expected = mutableListOf<GroupedProjectGroups>(group1, group3)

        // cal fn to check result
        val result = assignmentService.groupGroupsByFailures(failuresByGroup)

        assert(result != null)
        assert(result.size == 2)

        assert(result.containsAll(expected))
    }

    /**
     * This function generates test data for the code that identifies "suspicious" groups.
     *
     * @param nrSuspiciousCases is an Int, identifying one of two possible scenarios. If the value is 1, the returned
     * data will contain only one suspicious group. If the value is 2, the returned data will contain two suspicious
     * groups.
     */
    fun testDataForComputeStatistics(nrSuspiciousCases: Int): List<GroupSubmissionStatistics> {
        var groups = testDataForGroupGroupsByFailures();
        var submissionStatistics = mutableListOf<GroupSubmissionStatistics>()

        submissionStatistics.add(GroupSubmissionStatistics(groups[0].id, 15, 20));
        submissionStatistics.add(GroupSubmissionStatistics(groups[1].id, 10, 22)); // ignored
        submissionStatistics.add(GroupSubmissionStatistics(groups[2].id, 17, 18));
        submissionStatistics.add(GroupSubmissionStatistics(groups[3].id, 15, 5)); // suspicious
        submissionStatistics.add(GroupSubmissionStatistics(groups[4].id, 20, 20));

        if(nrSuspiciousCases == 2) {
            submissionStatistics.add(GroupSubmissionStatistics(6, 16, 6)) // suspicious
            submissionStatistics.add(GroupSubmissionStatistics(7, 17, 19))
            submissionStatistics.add(GroupSubmissionStatistics(8, 14, 14)) // ignored
        }

        return submissionStatistics
    }

    /**
     * Tested function: computeStatistics()
     *
     * This is a test for the calculation of the average and standard deviation statistics.
     */
    @Test
    fun testComputeStatistics() {
        var submissionStatistics = testDataForComputeStatistics(1)
        var nrOfGroups = 4.0
        var expectedAverageNumberOfSubmissions = (20 + 18 + 5 + 20) / nrOfGroups
        var expectedStdDev = 7.22
        var result = computeStatistics(submissionStatistics, 20)
        assertEquals(expectedAverageNumberOfSubmissions, result.average, 0.01)
        assertEquals(expectedStdDev, result.standardDeviation, 0.01);
    }

    /**
     * Tested function: AssignmentStatistics.identifyGroupsOutsideStatisticalNorms()
     *
     * In this scenario there are 4 relevant groups. One (1) of those groups has a result that is considered "too good
     * to be true" (i.e. it is suspicious). That group should be returned by the function.
     */
    @Test
    fun testIdentifyGroupsOutsideStatisticalNorms() {
        var submissionStatistics = testDataForComputeStatistics(1)
        var assignmentStatistics = computeStatistics(submissionStatistics, 20)
        var expected = listOf<GroupSubmissionStatistics>(GroupSubmissionStatistics(4, 15, 5))
        var result = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
        assert(1 == result.size)
        assertEquals(expected, result)
    }

    /**
     * Tested function: AssignmentStatistics.identifyGroupsOutsideStatisticalNorms()
     *
     * In this scenario there are 6 relevant groups. Two (2) of those groups have a result that is considered "too good
     * to be true" (i.e. it is suspicious). Those 2 groups should be returned by the function.
     */
    @Test
    fun testIdentifyGroupsOutsideStatisticalNorms_MoreThanOneSuspiciousGroup() {
        var submissionStatistics = testDataForComputeStatistics(2)
        var assignmentStatistics = computeStatistics(submissionStatistics, 20)
        var gss1 = GroupSubmissionStatistics(4, 15, 5)
        var gss2 = GroupSubmissionStatistics(6, 16, 6)
        var expected = listOf<GroupSubmissionStatistics>(gss1, gss2)
        var result = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
        assert(2 == result.size)
        assert(result.containsAll(expected))
    }

    /**
     * Tested function: AssignmentStatistics.identifyGroupsOutsideStatisticalNorms()
     *
     * In this scenario all the groups will be below de 75% threshold. This means that no groups should be identified
     * as being outside the statistical norms.
     */
    @Test
    fun testIdentifyGroupsOutsideStatisticalNorms_NoGroupsOverThreshold() {
        var submissionStatistics = mutableListOf<GroupSubmissionStatistics>()
        // all the groups are below the 75% threshold
        submissionStatistics.add(GroupSubmissionStatistics(1, 10, 20));
        submissionStatistics.add(GroupSubmissionStatistics(2, 10, 22));
        submissionStatistics.add(GroupSubmissionStatistics(3, 12, 20));
        var assignmentStatistics = computeStatistics(submissionStatistics, 20)
        var result = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
        assertEquals(0, assignmentStatistics.groupsConsideredForStatistics.size)
        assert(result.isEmpty())
    }

    /**
     * Tested function: AssignmentStatistics.identifyGroupsOutsideStatisticalNorms()
     *
     * In this scenario, two of the three groups are below the 75% threshold. This means that no groups should be
     * identified as being outside the statistical norms, because only one group will define the norm and will not be
     * outside of it.
     */
    @Test
    fun testIdentifyGroupsOutsideStatisticalNorms_OnlyTheOneGroupIsOverThreshold() {
        var submissionStatistics = mutableListOf<GroupSubmissionStatistics>()
        // 2 of the 3 groups are below the 75% threshold
        submissionStatistics.add(GroupSubmissionStatistics(1, 10, 20)); // ignored
        submissionStatistics.add(GroupSubmissionStatistics(2, 15, 22));
        submissionStatistics.add(GroupSubmissionStatistics(3, 12, 20)); // ignored
        var assignmentStatistics = computeStatistics(submissionStatistics, 20)
        var result = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
        assert(result.isEmpty())
        assertEquals(1, assignmentStatistics.groupsConsideredForStatistics.size)
    }

    /**
     * Tested function: AssignmentStatistics.identifyGroupsOutsideStatisticalNorms()
     *
     * Scenario where a Group has little submissions but also has little tests.
     *
     * Average: 21.33
     * StdDev: 1.154
     * Average - StdDev = 20.17
     *
     * Three Groups will be considered for the statistics.
     *
     * Any Group with at least 15 tests and less than 20.17 submissions will be signalled. There is only 1 Group in
     * this situation (nr tests: 17, nr subs: 20).
     */
    @Test
    fun testIdentifyGroupsOutsideStatisticalNorms_MoreComplexScenario() {
        var submissionStatistics = mutableListOf<GroupSubmissionStatistics>()
        submissionStatistics.add(GroupSubmissionStatistics(1, 10, 20)); // ignored because below 75% of tests
        submissionStatistics.add(GroupSubmissionStatistics(2, 15, 22));
        submissionStatistics.add(GroupSubmissionStatistics(3, 10, 10)); // ignored low tests & low subs
        submissionStatistics.add(GroupSubmissionStatistics(4, 15, 22));
        val gss5 = GroupSubmissionStatistics(5, 17, 20)
        submissionStatistics.add(gss5); // signalled
        var assignmentStatistics = computeStatistics(submissionStatistics, 20)
        var result = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
        assertEquals(3, assignmentStatistics.groupsConsideredForStatistics.size)
        assert(1 == result.size)
        assert(result.contains(gss5))
    }

}
