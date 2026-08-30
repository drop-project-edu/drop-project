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

import org.dropproject.TestsHelper.Companion.sampleJavaAssignmentPrivateKey
import org.dropproject.TestsHelper.Companion.sampleJavaAssignmentPublicKey
import org.dropproject.extensions.formatJustDate
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import org.apache.commons.io.FileUtils
import org.dropproject.TestsHelper
import org.dropproject.dao.*
import org.dropproject.data.SubmissionInfo
import org.dropproject.config.PendingExport
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.*


@DropProjectIntegrationTest
@Tag("integration")
class AssignmentImportExportTests : AssignmentTestBase() {

    @Test
    fun `export assignment`() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment1", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                dueDate = "2022-10-31T01:30:00.000-00:00"
            )


            val result = this.mvc.perform(
                get("/assignment/export/dummyAssignment1?includeSubmissions=false")
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isFound)
                .andReturn()

            val redirectLocation = result.response.getHeader("Location")
            kotlin.test.assertNotNull(redirectLocation)

            val result2 = this.mvc.perform(
                get(redirectLocation)
                    .with(user(TEACHER_1))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
            )
                .andExpect(
                    header().string(
                        "Content-Disposition",
                        "attachment; filename=dummyAssignment1_${Date().formatJustDate()}.dp"
                    )
                )
                .andExpect(status().isOk)
                .andReturn()

            val downloadedFileContent = result2.response.contentAsByteArray
            val downloadedZipFile = File("result.zip")
            val downloadedJSONFileName = File("result/assignment.json")
            FileUtils.writeByteArrayToFile(downloadedZipFile, downloadedFileContent)
            val downloadedFileAsZipObject = ZipFile(downloadedZipFile)
            downloadedFileAsZipObject.extractFile("assignment.json", "result")

            val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            val node = mapper.readTree(downloadedJSONFileName)
            assertEquals("dummyAssignment1", node.at("/id").asText())
            assertEquals("Dummy Assignment", node.at("/name").asText())
            assertEquals("org.dummy", node.at("/packageName").asText())
            assertEquals("2022-10-31 01:30:00", node.at("/dueDate").asText())
            assertEquals("UPLOAD", node.at("/submissionMethod").asText())
            assertEquals("JAVA", node.at("/language").asText())
            assertEquals("SHOW_PROGRESS", node.at("/hiddenTestsVisibility").asText())
            assertFalse(node.at("/acceptsStudentTests").asBoolean())
            assertEquals(sampleJavaAssignmentRepo, node.at("/gitRepositoryUrl").asText())
            assertEquals(TestsHelper.sampleJavaAssignmentPublicKey, node.at("/gitRepositoryPubKey").asText())
            assertEquals(TestsHelper.sampleJavaAssignmentPrivateKey, node.at("/gitRepositoryPrivKey").asText())
            assertEquals("dummyAssignment1", node.at("/gitRepositoryFolder").asText())

            downloadedZipFile.delete()
            downloadedJSONFileName.delete()

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").deleteRecursively()
            }
        }
    }

    @Test
    fun `download expired export`() {

        // exports are deleted after a while, so downloading one that no longer exists must be explained to the user
        this.mvc.perform(
            get("/assignment/export-result/someExpiredTaskId")
                .with(user(TEACHER_1))
                .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
        )
            .andExpect(status().isGone)
            .andExpect(content().string(containsString("This export is no longer available")))
    }

    @Test
    fun `export several assignments`() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment1", "Dummy Assignment",
                "org.dummy", "UPLOAD", sampleJavaAssignmentRepo
            )
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment2", "Dummy Kotlin Assignment",
                "org.dummy", "UPLOAD", sampleKotlinAssignmentRepo, language = "KOTLIN"
            )

            // the assignments are selected in the assignments list and exported all at once
            val result = this.mvc.perform(
                post("/assignment/export")
                    .with(user(TEACHER_1))
                    .param("ids", "dummyAssignment1", "dummyAssignment2")
            )
                .andExpect(status().isFound)
                .andReturn()

            val resultsLocation = result.response.getHeader("Location")
            kotlin.test.assertNotNull(resultsLocation)

            // the export produced one file per assignment
            val resultsPage = this.mvc.perform(get(resultsLocation).with(user(TEACHER_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("export-results"))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val exports = resultsPage.modelAndView!!.model["exports"] as List<PendingExport>
            assertEquals(2, exports.size)
            assertEquals("dummyAssignment1_${Date().formatJustDate()}", exports[0].filename)
            assertEquals("dummyAssignment2_${Date().formatJustDate()}", exports[1].filename)

            // and each one of them can be downloaded
            listOf("dummyAssignment1", "dummyAssignment2").forEachIndexed { index, assignmentId ->
                this.mvc.perform(
                    get("${resultsLocation}/${index}")
                        .with(user(TEACHER_1))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                )
                    .andExpect(status().isOk)
                    .andExpect(
                        header().string(
                            "Content-Disposition",
                            "attachment; filename=${assignmentId}_${Date().formatJustDate()}.dp"
                        )
                    )
            }

        } finally {
            File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").deleteRecursively()
            File(dropProjectProperties.assignments.rootLocation, "dummyAssignment2").deleteRecursively()
        }
    }

    @Test
    fun `export assignments of another teacher`() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment1", "Dummy Assignment",
                "org.dummy", "UPLOAD", sampleJavaAssignmentRepo
            )

            // the ids are sent by the browser, so a teacher must not be able to export the assignments of others
            val teacher2 = User("teacher2", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))
            this.mvc.perform(
                post("/assignment/export")
                    .with(user(teacher2))
                    .param("ids", "dummyAssignment1")
            )
                .andExpect(status().isForbidden)

        } finally {
            File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").deleteRecursively()
        }
    }

    @Test
    fun `import assignment only`() {

        try {
            val fileContent = File("src/test/sampleExports/export-only-assignment.dp").readBytes()
            val multipartFile =
                MockMultipartFile("file", "export-only-assignment.dp", "application/zip", fileContent)

            mvc.perform(
                MockMvcRequestBuilders.multipart("/assignment/import")
                    .file(multipartFile)
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isFound())
                .andExpect(
                    flash().attribute(
                        "message",
                        "Imported successfully dummyAssignment1. Submissions were not imported"
                    )
                )
                .andExpect(header().string("Location", "/assignment/info/dummyAssignment1"))


            // let's check if it was well imported
            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignment1").with(user(TEACHER_1)))
                .andExpect(status().isOk)
                .andReturn()

            val assignment = mvcResult.modelAndView!!.model["assignment"] as Assignment
            assertEquals("dummyAssignment1", assignment.id)
            assertEquals("teacher1", assignment.ownerUserId)
            mvcResult.modelAndView!!.model["tests"]
            mvcResult.modelAndView!!.model["report"]

        } finally {
            // remove the assignments files created during the test
            File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").deleteRecursively()
        }
    }

    @Test
    fun `export assignment and submissions`() {

        val assignment01 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj"
        )
        assignmentRepository.save(assignment01)

        testsHelper.makeSeveralSubmissions(
            listOf(
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectOK",
                "projectInvalidStructure1"
            ), mvc
        )

        val result = this.mvc.perform(
            get("/assignment/export/testJavaProj?includeSubmissions=true")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andReturn()

        val redirectLocation = result.response.getHeader("Location")
        kotlin.test.assertNotNull(redirectLocation)

        val result2 = this.mvc.perform(
            get(redirectLocation)
                .with(user(TEACHER_1))
                .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
        )
            .andExpect(
                header().string(
                    "Content-Disposition",
                    "attachment; filename=testJavaProj_${Date().formatJustDate()}.dp"
                )
            )
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result2.response.contentAsByteArray
        val downloadedZipFile = File("result.zip")
        val downloadedJSONFileName = File("result/submissions.json")
        FileUtils.writeByteArrayToFile(downloadedZipFile, downloadedFileContent)
        val downloadedFileAsZipObject = ZipFile(downloadedZipFile)
        downloadedFileAsZipObject.extractFile("submissions.json", "result")

        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val node = mapper.readTree(downloadedJSONFileName)
        assertEquals("testJavaProj", node.at("/0/assignmentId").asText())
        assertEquals("student1", node.at("/0/submitterUserId").asText())
        assertEquals("V", node.at("/0/status").asText())
        assertTrue(node.at("/0/buildReport").isNull)
        assertEquals("student1", node.at("/0/authors/0/userId").asText())
        assertEquals("PS", node.at("/0/submissionReport/0/key").asText())
        assertEquals("NOK", node.at("/0/submissionReport/0/value").asText())

        assertEquals("student2", node.at("/1/submitterUserId").asText())
        assertEquals("student2", node.at("/1/authors/0/userId").asText())

        assertFalse(node.at("/2/buildReport").isNull)
        assertEquals("PS", node.at("/2/submissionReport/0/key").asText())
        assertEquals("OK", node.at("/2/submissionReport/0/value").asText())
        assertEquals("C", node.at("/2/submissionReport/1/key").asText())
        assertEquals("OK", node.at("/2/submissionReport/1/value").asText())
        assertEquals("TT", node.at("/2/submissionReport/3/key").asText())
        assertEquals("OK", node.at("/2/submissionReport/3/value").asText())
        assertEquals(2, node.at("/2/submissionReport/3/progress").asInt())
        assertEquals(2, node.at("/2/submissionReport/3/goal").asInt())

        val junitReportFileNames =
            node.at("/2/junitReports").elements().asSequence().toList().map { it.get("filename").textValue() }

        assertThat(
            junitReportFileNames,
            hasItems(
                "TEST-org.dropProject.sampleAssignments.testProj.TestTeacherProject.xml",
                "TEST-org.dropProject.sampleAssignments.testProj.TestTeacherHiddenProject.xml"
            )
        )

        assertEquals("student4", node.at("/3/authors/0/userId").asText())
        assertEquals("student5", node.at("/3/authors/1/userId").asText())

        val fileHeaders = downloadedFileAsZipObject.fileHeaders as List<FileHeader>
        assertEquals(9, fileHeaders.size)
        // TODO: Think of a way to make these tests independent of the headers order
//        assertThat(fileHeaders[3].fileName, matchesPattern("original/testJavaProj/[0-9][0-9]-[0-9][0-9]/"))
//        for (i in 4..7) {
//            assertTrue(fileHeaders[i].fileName.endsWith(".zip"))
//        }

        downloadedZipFile.delete()
        downloadedJSONFileName.delete()

    }

    @Test
    fun `import assignment and submissions`() {

        try {
            val fileContent = File("src/test/sampleExports/export-assignment-and-submissions.dp").readBytes()
            val multipartFile =
                MockMultipartFile("file", "export-assignment-and-submissions.dp", "application/zip", fileContent)

            mvc.perform(
                MockMvcRequestBuilders.multipart("/assignment/import")
                    .file(multipartFile)
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isFound)
                .andExpect(
                    flash().attribute(
                        "message",
                        "Imported successfully dummyAssignment1 and all its submissions"
                    )
                )
                .andExpect(header().string("Location", "/report/dummyAssignment1"))

            val reportResult = this.mvc.perform(
                get("/report/dummyAssignment1")
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isOk())
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val report = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>
            assertEquals(4, report.size)

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").deleteRecursively()
            }
        }


    }

    @Test
    fun `export assignment and git submissions`() {

        val assignment01 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.GIT, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj"
        )
        assignmentRepository.save(assignment01)

        testsHelper.connectToGitRepositoryAndBuildReport(
            mvc, gitSubmissionRepository, "testJavaProj",
            "git@github.com:drop-project-edu/sampleJavaSubmission.git", "student1"
        )

        val result = this.mvc.perform(
            get("/assignment/export/testJavaProj?includeSubmissions=true")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andReturn()

        val redirectLocation = result.response.getHeader("Location")
        kotlin.test.assertNotNull(redirectLocation)

        val result2 = this.mvc.perform(
            get(redirectLocation)
                .with(user(TEACHER_1))
                .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
        )
            .andExpect(
                header().string(
                    "Content-Disposition",
                    "attachment; filename=testJavaProj_${Date().formatJustDate()}.dp"
                )
            )
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result2.response.contentAsByteArray
        val downloadedZipFile = File("result.zip")
        val downloadedJSONFileName = File("result/git-submissions.json")
        FileUtils.writeByteArrayToFile(downloadedZipFile, downloadedFileContent)
        val downloadedFileAsZipObject = ZipFile(downloadedZipFile)
        downloadedFileAsZipObject.extractFile("git-submissions.json", "result")

        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val node = mapper.readTree(downloadedJSONFileName)
        assertEquals("testJavaProj", node.at("/0/assignmentId").asText())
        assertEquals("student1", node.at("/0/submitterUserId").asText())
        assertEquals("2019-02-26 17:26:53", node.at("/0/lastCommitDate").asText())
        assertEquals(
            "git@github.com:drop-project-edu/sampleJavaSubmission.git",
            node.at("/0/gitRepositoryUrl").asText()
        )
        assertEquals("student1", node.at("/0/authors/0/userId").asText())
        assertEquals("student2", node.at("/0/authors/1/userId").asText())

        val fileHeaders = downloadedFileAsZipObject.fileHeaders as List<FileHeader>
        assertEquals(41, fileHeaders.size)
        // use a regex because the timestamp (mutable) is part of the name
//        assertThat(fileHeaders[3].fileName, matchesPattern("original/testJavaProj/[0-9][0-9]-[0-9][0-9]/"))
//        assertThat(fileHeaders[4].fileName, matchesPattern("original/testJavaProj/[0-9][0-9]-[0-9][0-9]/[0-9]+-sampleJavaSubmission/"))

        downloadedZipFile.delete()
        downloadedJSONFileName.delete()
    }

    @Test
    fun `import assignment and git submissions`() {

        try {
            val fileContent = File("src/test/sampleExports/export-assignment-and-git-submissions.dp").readBytes()
            val multipartFile =
                MockMultipartFile("file", "export-assignment-and-git-submissions.dp", "application/zip", fileContent)

            mvc.perform(
                MockMvcRequestBuilders.multipart("/assignment/import")
                    .file(multipartFile)
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isFound)
                .andExpect(
                    flash().attribute(
                        "message",
                        "Imported successfully dummyAssignment1 and all its submissions"
                    )
                )
                .andExpect(header().string("Location", "/report/dummyAssignment1"))

            val reportResult = this.mvc.perform(
                get("/report/dummyAssignment1")
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isOk())
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val report = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>
            assertEquals(4, report.size)

            // TODO check git submission

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").deleteRecursively()
            }
        }


    }
}
