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
import org.dropproject.data.TestType
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class UploadMavenStructureTests : UploadTestBase() {

    // ===================================
    // Maven Submission Tests
    // ===================================

    @Test
    fun `upload Maven project with correct structure and pom`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectOK-maven", "testJavaProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertTrue(structureErrors.isEmpty(), "Structure errors should be empty")

        // Verify main resources are copied to the mavenized folder
        val submissionDB = submissionRepository.findById(submissionId.toLong()).get()
        val mavenizedFolder = File(dropProjectProperties.mavenizedProjects.rootLocation,
            Submission.relativeUploadFolder("testJavaProj", submissionDB.submissionDate))
        val mavenizedProjectFolder = File(mavenizedFolder, "${submissionDB.submissionId}-mavenized")
        assertTrue(File(mavenizedProjectFolder, "src/main/resources/application.properties").exists(), "src/main/resources/application.properties should be copied to mavenized folder")
    }

    @Test
    fun `upload Maven project with invalid structure`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure1-maven", "testJavaProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

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
        assertTrue(structureErrors.isNotEmpty(), "Should have structure errors")
        assertThat(structureErrors,
            hasItems("The project does not contain a 'src/main/java/org/dropProject/sampleAssignments/testProj' folder"))
    }

    @Test
    fun `upload Maven project without pom xml`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        // Upload a compact project (no pom.xml) to a Maven assignment
        val submissionId = testsHelper.uploadProject(this.mvc, "projectOK", "testJavaProj", STUDENT_1,
            submissionStructure = SubmissionStructure.COMPACT, language = Language.JAVA)

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
        assertTrue(structureErrors.any { it.contains("pom.xml", ignoreCase = true) }, "Should have error about missing pom.xml")
    }

    @Test
    fun `upload Maven project with JUnit errors`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectJUnitErrors-maven", "testJavaProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be NOK (key)")
        assertEquals("NOK", summary[3].reportValue, "junit should be NOK (value)")

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertTrue(buildResult.hasJUnitErrors(TestType.TEACHER) == true, "Should have JUnit errors")
    }

    @Test
    fun `upload Maven project with checkstyle errors`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors-maven", "testJavaProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertTrue(summary.size >= 4, "Summary should have at least 4 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be NOK (key)")
        assertEquals("NOK", summary[2].reportValue, "checkstyle should be NOK (value)")

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertTrue(buildResult.checkstyleErrors.isNotEmpty(), "Should have checkstyle errors")
    }

    @Test
    fun `upload Kotlin Maven project`() {
        val assignment = Assignment(
            id = "testKotlinProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropproject.samples.samplekotlinassignment",
            language = Language.KOTLIN,
            ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD,
            active = true,
            submissionStructure = SubmissionStructure.MAVEN,  // <<< Maven structure
            gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testKotlinProj"
        )
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectKotlinOK-maven", "testKotlinProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertTrue(summary.isNotEmpty(), "Summary should have at least 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertTrue(structureErrors.isEmpty(), "Structure errors should be empty")
    }

    @Test
    fun `compact project should be rejected by Maven assignment`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        // Upload a compact project to a Maven assignment
        val submissionId = testsHelper.uploadProject(this.mvc, "projectOK", "testJavaProj", STUDENT_1,
            submissionStructure = SubmissionStructure.COMPACT, language = Language.JAVA)

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
        assertTrue(structureErrors.isNotEmpty(), "Should have errors about missing Maven structure")
    }
}
