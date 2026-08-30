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

import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.dropproject.dao.*
import org.dropproject.data.BuildReport
import org.dropproject.data.SubmissionInfo
import org.dropproject.data.TestType
import org.dropproject.repository.*
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.verify
import org.mockito.Mockito.never
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class UploadValidationTests : UploadTestBase() {

    @Test
    @Disabled("rever isto - storageService is not a mock, so the verify() call would fail")
    fun `should not accept a non-zip file`() {
        val multipartFile = MockMultipartFile("file", "test.txt", "text/plain", "Spring Framework".toByteArray())
        this.mvc.perform(multipart("/upload").file(multipartFile).with(user(STUDENT_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/upload"))
                .andExpect(flash().attribute("error", "O ficheiro tem que ser um .zip"))

        verify(this.storageService, never()).store(multipartFile, "")
    }

    @Test
    @Disabled("Infelizmente o MockMvc não consegue testar isto")
    fun `should not accept a big file`() {

        val bigFileData = ByteArray(100_000_000) { 1 }

        val multipartFile = MockMultipartFile("file", "test.txt", "text/plain", bigFileData)
        this.mvc.perform(multipart("/upload").file(multipartFile).with(user(STUDENT_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/upload"))
                .andExpect(flash().attribute("error", "Ficheiro excede o tamanho máximo permitido"))

        verify(this.storageService, never()).store(multipartFile, "")
    }

    @Test
    fun `upload project with invalid structure 1`() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(1, summary.size, "Summary should be 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be NOK (key)")
        assertEquals("NOK", summary[0].reportValue, "projectStructure should be NOK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertThat(structureErrors,
                hasItems("The project does not contain a 'src/org/dropProject/sampleAssignments/testProj' folder",
                        "The project does not contain the Main.java file in the 'src/org/dropProject/sampleAssignments/testProj' folder"))
    }

    @Test
    fun `upload project with invalid structure 2`() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure2", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(1, summary.size, "Summary should be 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be NOK (key)")
        assertEquals("NOK", summary[0].reportValue, "projectStructure should be NOK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertThat(structureErrors, hasItems("The project contains a README.md folder but it should be a file"))
    }

    @Test
    fun `upload project with hacking attempt`() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectHackingAttempt", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be NOK (key)")
        assertEquals("NOK", summary[3].reportValue, "junit should be NOK (value)")

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertEquals(2, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numErrors)
        assertTrue(buildResult.junitErrorsTeacher?.contains("SecurityException") == true)
    }

    @Test
    fun `upload project with unexpected character`() {
        val uploader = User("p4453", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))
        val submissionId = testsHelper.uploadProject(this.mvc, "projectUnexpectedCharacter", "testJavaProj", uploader)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(uploader)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>

        assertEquals(2, summary.size, "Summary should be 2 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be NOK (key)")
        assertEquals("NOK", summary[1].reportValue, "compilation should be NOK (value)")

    }

    @Test
    fun `upload project to inexistent assignment`() {
        this.mvc.perform(get("/upload/inexistentAssignment")
                .with(user(STUDENT_1)))
                .andExpect(status().isNotFound())
    }

    @Test
    fun `upload a project with test classes that dont follow the TestXXX convention should show an error`() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWithStudentTestNotValid", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(1, summary.size, "Summary should be 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be NOK (key)")
        assertEquals("NOK", summary[0].reportValue, "projectStructure should be NOK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertThat(structureErrors, hasItems("Test classes must start with the word Test (example: TestCar)"))
    }

    fun `upload project with nested folder in zip should return error`() {

        // Create a temp folder simulating the wrong zip structure:
        // outerFolder/
        //   └── my-project/
        //       ├── src/
        //       └── AUTHORS.txt
        val tempDirectory = File(System.getProperty("java.io.tmpdir"))
        val zipCreationTime = System.currentTimeMillis()

        val projectFolder = File(tempDirectory, "my-project-$zipCreationTime")
        projectFolder.mkdir()
        File(projectFolder, "src").mkdir()
        File(projectFolder, "AUTHORS.txt").apply {
            createNewFile()
            writeText("student1;Student 1")
        }

        val outerFolder = File(tempDirectory, "outer-$zipCreationTime")
        outerFolder.mkdir()
        projectFolder.copyRecursively(File(outerFolder, projectFolder.name))

        // Zip the outer folder (wrong structure)
        val zipFile = zipService.createZipFromFolder("bad-submission-$zipCreationTime", outerFolder)
        zipFile.deleteOnExit()

        val multipartFile = MockMultipartFile("file", zipFile.name, "application/zip", zipFile.readBytes())

        this.mvc.perform(
            multipart("/upload")
                .file(multipartFile)
                .param("assignmentId", "testJavaProj")
                .param("async", "false")
                .with(user(STUDENT_1))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(content().string(
                """{"error":"Please make sure that AUTHORS.txt is placed directly in the root of the ZIP, and that your ZIP does not contain an extra top-level folder (e.g., project-name/AUTHORS.txt)."}"""
            ))

        // clean-up
        projectFolder.deleteRecursively()
        outerFolder.deleteRecursively()
        zipFile.delete()
    }

    @Test
    fun `upload project with invalid structure - indicators should be visible in report`() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1)

        // status should remain VALIDATED
        val submissionFromDB = submissionRepository.findById(submissionId.toLong()).get()
        assertEquals(SubmissionStatus.VALIDATED, submissionFromDB.getStatus())

        // check that the report page shows the PROJECT_STRUCTURE indicator
        val reportResult = this.mvc.perform(get("/report/testJavaProj").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val submissions = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>
        assertEquals(1, submissions.size)

        val lastSubmission = submissions[0].lastSubmission
        assertNotNull(lastSubmission.reportElements, "reportElements should not be null")
        assertTrue(lastSubmission.reportElements!!.isNotEmpty(), "reportElements should not be empty")
        assertEquals(Indicator.PROJECT_STRUCTURE, lastSubmission.reportElements!![0].indicator)
        assertEquals("NOK", lastSubmission.reportElements!![0].reportValue)
    }

    // assignment's src/main should not overwrite student submission
    @Test
    fun `assignment files don't overwrite submission files`() {

        try {
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "sampleJavaAssignment", "Sample Java Assignment",
                    "org.dropProject.samples.sampleJavaAssignment",
                    "UPLOAD", sampleJavaAssignmentRepo,
                    activateRightAfterCloning = true)

            val submissionId = testsHelper.uploadProject(this.mvc, "projectSampleJavaAssignmentNOK", "sampleJavaAssignment", STUDENT_1)

            val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                    .andExpect(status().isOk())
                    .andReturn()

            @Suppress("UNCHECKED_CAST")
            val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
            assertEquals(4, summary.size, "Summary should be 4 lines")
            assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
            assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
            assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
            assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
            assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
            assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
            assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be NOK (key)")
            assertEquals("NOK", summary[3].reportValue, "junit should be NOK (value)")

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "sampleJavaAssignment").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "sampleJavaAssignment").deleteRecursively()
            }
        }

    }
}
