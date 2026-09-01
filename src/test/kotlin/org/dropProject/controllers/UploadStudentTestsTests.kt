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
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.dropproject.dao.*
import org.dropproject.data.BuildReport
import org.dropproject.data.TestType
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.hamcrest.CoreMatchers.*
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class UploadStudentTestsTests : UploadTestBase() {

    @Test
    fun `upload project with student tests`() {

        val assignment = Assignment(id = "testJavaProjWithCoverage",    // <<< this is very important for this test
                name = "Test Project (for automatic tests with coverage)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true,
                acceptsStudentTests = true,    // <<< this is very important for this test
                minStudentTests = 1,
                calculateStudentTestsCoverage = true,  // <<< this is very important for this test
                gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = "testJavaProjWithCoverage")
        assignmentRepository.save(assignment)

        val submissionId = submissionFixtures.uploadProject("projectWith1StudentTest", assignment.id, STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(6, summary.size, "Summary should be 6 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.STUDENT_UNIT_TESTS, summary[3].indicator, "student tests should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "student tests should be OK (value)")
        assertEquals(1, summary[3].reportProgress, "student tests should pass 1 test")
        assertEquals(1, summary[3].reportGoal, "student tests should have total 1 tests")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[4].indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary[4].reportValue, "teacher tests should be OK (value)")
        assertEquals(2, summary[4].reportProgress, "teacher tests should pass 2 tests")
        assertEquals(2, summary[4].reportGoal, "teacher tests should have total 2 tests")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())
        assert(buildResult.checkstyleErrors.isEmpty())
        assert(buildResult.PMDerrors().isEmpty())

        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == false)
        assertTrue(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))

        assert(buildResult.hasJUnitErrors(TestType.STUDENT) == false)
        assertTrue(buildResult.junitSummaryStudent!!.startsWith("Tests run: 1, Failures: 0, Errors: 0"))

        assert(buildResult.jacocoResults.isNotEmpty())
        assertEquals(25, buildResult.jacocoResults[0].lineCoveragePercent)
    }

    @Test
    fun `upload project with student tests using junit5`() {

        val assignment = Assignment(id = "testJavaProjJUnit5",
            name = "Test Project",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true,
            acceptsStudentTests = true,    // <<< this is very important for this test
            minStudentTests = 1,
            gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testJavaProjJUnit5")
        assignmentRepository.save(assignment)

        val submissionId = submissionFixtures.uploadProject("projectWith2StudentTestsUsingBeforeClass", assignment.id, STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(6, summary.size, "Summary should be 6 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.STUDENT_UNIT_TESTS, summary[3].indicator, "student tests should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "student tests should be OK (value)")
        assertEquals(2, summary[3].reportProgress, "student tests should pass 2 tests")
        assertEquals(2, summary[3].reportGoal, "student tests should have total 2 tests")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[4].indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary[4].reportValue, "teacher tests should be OK (value)")
        assertEquals(2, summary[4].reportProgress, "teacher tests should pass 2 tests")
        assertEquals(2, summary[4].reportGoal, "teacher tests should have total 2 tests")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())
        assert(buildResult.checkstyleErrors.isEmpty())
        assert(buildResult.PMDerrors().isEmpty())

        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == false)
        assertTrue(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))

        assert(buildResult.hasJUnitErrors(TestType.STUDENT) == false)
        assertTrue(buildResult.junitSummaryStudent!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))
    }

    @Test
    fun `upload project with student tests for assignment that doesn't require student tests`() {

        val assignment = Assignment(id = "testJavaProjWithCoverage",    // <<< this is very important for this test
                name = "Test Project (for automatic tests with coverage)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true,
                acceptsStudentTests = false,    // <<< this is very important for this test
                gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = "testJavaProjWithCoverage")
        assignmentRepository.save(assignment)

        val submissionId = submissionFixtures.uploadProject("projectWith1StudentTest", assignment.id, STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
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
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "teacher tests should be OK (value)")
        assertEquals(2, summary[3].reportProgress, "teacher tests should pass 2 tests")
        assertEquals(2, summary[3].reportGoal, "teacher tests should have total 2 tests")

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport

        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == false)
        assertTrue(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))
    }

    @Test
    fun `upload project without student tests for assignment that requires student tests`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.acceptsStudentTests = true  // <<< this is very important for this test
        assignment.minStudentTests = 1
        assignmentRepository.save(assignment)

        val submissionId = submissionFixtures.uploadProject("projectOK", "testJavaProj", STUDENT_1)  // <<< this project doesn't have student tests

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(6, summary.size, "Summary should be 6 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.STUDENT_UNIT_TESTS, summary[3].indicator, "student tests should be NOK (key)")
        assertEquals("Not Enough Tests", summary[3].reportValue, "student tests should be NOK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[4].indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary[4].reportValue, "teacher tests should be OK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())
        assert(buildResult.checkstyleErrors.isEmpty())
        assert(buildResult.PMDerrors().isEmpty())

        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == false)
        assertTrue(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))

        assertNull(buildResult.hasJUnitErrors(TestType.STUDENT))
        assertNull(buildResult.junitSummaryStudent)
    }

    @Test
    fun `upload project without enough student tests`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.acceptsStudentTests = true  // <<< this is very important for this test
        assignment.minStudentTests = 2  // <<< this project requires at least 2 student tests
        assignmentRepository.save(assignment)

        val submissionId = submissionFixtures.uploadProject("projectWith1StudentTest", "testJavaProj", STUDENT_1)  // <<< this project only has 1 student test

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(6, summary.size, "Summary should be 6 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.STUDENT_UNIT_TESTS, summary[3].indicator, "student tests should be NOK (key)")
        assertEquals("Not Enough Tests", summary[3].reportValue, "student tests should be NOK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[4].indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary[4].reportValue, "teacher tests should be OK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())
        assert(buildResult.checkstyleErrors.isEmpty())
        assert(buildResult.PMDerrors().isEmpty())

        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == false)
        assertTrue(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))

        assertTrue(buildResult.hasJUnitErrors(TestType.STUDENT) == false)
        assertNotNull(buildResult.junitSummaryStudent)
    }

    @Test
    fun `upload project with test input files`() {

        val submissionId = submissionFixtures.uploadProject("projectWithTestInputFiles", "testJavaProj", STUDENT_1)

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
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "junit should be OK (value)")
    }
}
