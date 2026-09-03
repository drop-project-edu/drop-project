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
package org.dropproject.services

import org.dropproject.dao.Assignment
import org.dropproject.dao.Language
import org.dropproject.dao.TestVisibility
import org.dropproject.forms.SubmissionMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension


@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
class AssignmentValidatorTests {

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    lateinit var assignmentValidator: AssignmentValidator

    val sampleAssignmentsRootFolder = "src/test/sampleAssignments"

    val dummyAssignment = Assignment(id = "dummy", name = "", gitRepositoryUrl = "",
            gitRepositoryFolder = "", ownerUserId = "p4997", submissionMethod = SubmissionMethod.UPLOAD,
            packageName = "org.dropProject.samples",
            hiddenTestsVisibility = TestVisibility.HIDE_EVERYTHING)

    val dummyKotlinAssignment = Assignment(id = "dummyKotlin", name = "", gitRepositoryUrl = "",
            gitRepositoryFolder = "", ownerUserId = "p4997", submissionMethod = SubmissionMethod.UPLOAD,
            language = Language.KOTLIN,
            packageName = "org.dropproject.samples.samplekotlinassignment",
            hiddenTestsVisibility = TestVisibility.HIDE_EVERYTHING)

    @BeforeEach
    fun initAssignmentValidator() {
        assignmentValidator = AssignmentValidator()
    }

    @Test
    fun `Test testJavaProj assignment`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProj").file

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        assertTrue(!report.isEmpty(), "report list should not be empty")
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.INFO  &&
                it.message == "You have hidden tests. The results will be completely hidden from the students." })
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.INFO  &&
                it.message == "Found 2 test classes" })
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.WARNING  &&
                it.message == "You haven't defined a timeout for 2 test methods." })
    }


    @Test
    fun `Test testJavaProjWithUserIdOK assignment`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProjWithUserIdOK").file

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        assertTrue(!report.isEmpty(), "report should not be empty")
        assertTrue(report.none { it.type != AssignmentValidator.InfoType.INFO })
    }

    @Test
    fun `Test testJavaProjWithUserIdNOK assignment`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProjWithUserIdNOK").file

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        assertTrue(!report.isEmpty(), "report should not be empty")
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.WARNING })
        assertTrue(report.any { it.message == "POM file is not prepared to use the 'dropProject.currentUserId' system property" })
    }

    @Test
    fun `Test testJavaProj assignment without setting hidden tests visibility`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProj").file

        dummyAssignment.hiddenTestsVisibility = null  // <<<< this is important

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        assertTrue(report.any {
            it.type == AssignmentValidator.InfoType.ERROR &&
                    it.message == "You have hidden tests but you didn't set their visibility to students."
        })
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.INFO &&
                it.message == "You are using a recent version of checkstyle."})
    }

    @Test
    fun `Test testJavaProjWithCoverage assignment with wrong package`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProjWithCoverage").file

        dummyAssignment.acceptsStudentTests = true  // <<<< this is important
        dummyAssignment.minStudentTests = 2  // <<<< this is important
        dummyAssignment.calculateStudentTestsCoverage = true  // <<<< this is important

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        assertTrue(report.any {
            it.type == AssignmentValidator.InfoType.ERROR &&
                    it.message == "jacoco-maven-plugin (used for coverage) has a configuration problem"
        })
    }

    @Test
    fun `Test testJavaProjWithCoverage assignment with right package`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProjWithCoverage").file

        dummyAssignment.acceptsStudentTests = true  // <<<< this is important
        dummyAssignment.minStudentTests = 2  // <<<< this is important
        dummyAssignment.calculateStudentTestsCoverage = true  // <<<< this is important
        dummyAssignment.packageName = "org.dropProject.sampleAssignments.testProj" // <<<< this is important

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report2 = assignmentValidator.report
        assertTrue(report2.none() {
            it.type == AssignmentValidator.InfoType.ERROR
        })
    }

    @Test
    fun `Test testJavaComplexProj with several test classes and test methods`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaComplexProj").file

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val tests = assignmentValidator.testMethods
        assertEquals(35, tests.size)
    }

    @Test
    fun `Test testJavaProjWithWrongCheckstyleVersion`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProjWithWrongCheckstyleVersion").file
        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.ERROR &&
        it.message == "You are using an outdated version of checkstyle."})
    }

    @Test
    fun `Test testJavaProjJUnit5`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProjJUnit5").file
        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.INFO &&
                it.message == "You have defined a global timeout for the test methods."})
    }

    @Test
    fun `Test testJavaProjJUnit5 with a class level timeout that qdox cannot resolve`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProjJUnit5UnresolvedTimeout").file
        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.INFO &&
                it.message == "You have defined a global timeout for the test methods."})
        assertTrue(report.none { it.type == AssignmentValidator.InfoType.WARNING &&
                it.message.startsWith("You haven't defined a timeout")})
    }

    @Test
    fun `Test testJavaProjJUnit5 with a timeout on each test method`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProjJUnit5PerMethodTimeout").file
        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        // the @Disabled test method doesn't count, even though it is also annotated with @Test
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.INFO &&
                it.message == "You have defined 2 test methods with timeout."})
        assertTrue(report.none { it.type == AssignmentValidator.InfoType.WARNING &&
                it.message.startsWith("You haven't defined a timeout")})
    }

    @Test
    fun `Test testKotlinProj with a class level timeout`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testKotlinProjClassTimeout").file
        assignmentValidator.validate(assignmentFolder, dummyKotlinAssignment)
        val report = assignmentValidator.report
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.INFO &&
                it.message == "You have defined a global timeout for the test methods."})
        assertTrue(report.none { it.type == AssignmentValidator.InfoType.WARNING &&
                it.message.startsWith("You haven't defined a timeout")})
    }

    @Test
    fun `Test testKotlinProj with a timeout on each test method`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testKotlinProjPerMethodTimeout").file
        assignmentValidator.validate(assignmentFolder, dummyKotlinAssignment)
        val report = assignmentValidator.report
        // the @Disabled test method doesn't count, even though it is also annotated with @Test
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.INFO &&
                it.message == "You have defined 2 test methods with timeout."})
        assertTrue(report.none { it.type == AssignmentValidator.InfoType.WARNING &&
                it.message.startsWith("You haven't defined a timeout")})
    }

    @Test
    fun `Test testJavaProj without package`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProj").file

        dummyAssignment.packageName = null  // <<<< this is important

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        assertTrue(report.any {
            it.type == AssignmentValidator.InfoType.WARNING &&
                    it.message == "Assignment without package."
        })
    }

    @Test
    fun `Test that AssignmentValidationInputs reflect the properties that are cross-checked with the repository`() {

        val assignment = Assignment(id = "dummy2", name = "Dummy", gitRepositoryUrl = "",
            gitRepositoryFolder = "", ownerUserId = "p4997", submissionMethod = SubmissionMethod.UPLOAD,
            packageName = "org.dropProject.samples", language = Language.JAVA,
            calculateStudentTestsCoverage = false, maxMemoryMb = null, mandatoryTestsSuffix = null,
            hiddenTestsVisibility = TestVisibility.HIDE_EVERYTHING)

        val validationInputs = AssignmentValidationInputs.from(assignment)

        assertEquals(validationInputs, AssignmentValidationInputs.from(assignment))

        // properties that must trigger a new validation
        assignment.packageName = "org.dropProject.other"
        assertNotEquals(validationInputs, AssignmentValidationInputs.from(assignment))
        assignment.packageName = "org.dropProject.samples"

        assignment.language = Language.KOTLIN
        assertNotEquals(validationInputs, AssignmentValidationInputs.from(assignment))
        assignment.language = Language.JAVA

        assignment.calculateStudentTestsCoverage = true
        assertNotEquals(validationInputs, AssignmentValidationInputs.from(assignment))
        assignment.calculateStudentTestsCoverage = false

        assignment.maxMemoryMb = 1024
        assertNotEquals(validationInputs, AssignmentValidationInputs.from(assignment))
        assignment.maxMemoryMb = null

        assignment.mandatoryTestsSuffix = "_MANDATORY"
        assertNotEquals(validationInputs, AssignmentValidationInputs.from(assignment))
        assignment.mandatoryTestsSuffix = null

        // properties that are not checked against the contents of the repository
        assignment.name = "Another name"
        assignment.cooloffPeriod = 10
        assertEquals(validationInputs, AssignmentValidationInputs.from(assignment))
    }
}
