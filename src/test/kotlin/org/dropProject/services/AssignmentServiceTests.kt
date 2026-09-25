/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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

import org.apache.commons.io.FileUtils
import org.dropproject.DropProjectIntegrationTest
import org.dropproject.config.DropProjectProperties
import org.dropproject.repository.AssignmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.dropproject.dao.Assignment
import org.dropproject.dao.Language
import org.dropproject.dao.TestVisibility
import org.dropproject.forms.AssignmentForm
import org.dropproject.forms.SubmissionMethod
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.io.File

@DropProjectIntegrationTest
class AssignmentServiceTests() {

    @Autowired
    private lateinit var assignmentService: AssignmentService

    @Autowired
    private lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    private lateinit var dropProjectProperties: DropProjectProperties

    @Test
    fun `update assignment`() {
        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, language = Language.JAVA, active = true,
                gitRepositoryUrl = "git://dummyRepo", gitRepositoryFolder = "testJavaProj")

        // Create an AssignmentForm with information different from the one used to create the Assignment
        val assignmentForm = AssignmentForm()
        assignmentForm.assignmentName = "Test Project (renamed)"
        assignmentForm.assignmentPackage = "org.dropProject.sampleAssignments.testProject"
        assignmentForm.language = Language.KOTLIN
        assignmentForm.submissionMethod = SubmissionMethod.GIT

        assignmentService.updateAssignment(assignment01, assignmentForm)

        assertEquals("Test Project (renamed)", assignment01.name)
        assertEquals("org.dropProject.sampleAssignments.testProject", assignment01.packageName)
        assertEquals(Language.KOTLIN, assignment01.language)
        assertEquals(SubmissionMethod.GIT, assignment01.submissionMethod)

    }

    @Test
    fun `validating an assignment stores whether its pom is a spring boot one`() {
        val rootLocation = dropProjectProperties.assignments.rootLocation
        val folder = File(rootLocation, "testJavaProjSpringBootDetection")
        FileUtils.copyDirectory(File(rootLocation, "testJavaProj"), folder)

        try {
            val assignment = Assignment(id = "testJavaProjSpringBootDetection", name = "Spring Boot detection",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = folder.name, hiddenTestsVisibility = TestVisibility.SHOW_PROGRESS)
            assignmentRepository.save(assignment)

            assignmentService.validateAndStoreReport(assignment, null)
            val validated = assignmentRepository.findById(assignment.id).get()
            assertEquals(false, validated.springBoot)
            assertTrue(validated.assignmentTestMethods.isNotEmpty(), "the validation should have stored the test methods")

            // the teacher pushes a pom.xml that depends on spring boot, and the assignment is validated again, as a
            // refresh does: loaded from the database, with the test methods that the validation replaces
            val pomFile = File(folder, "pom.xml")
            pomFile.writeText(pomFile.readText().replaceFirst("<dependencies>", """
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                        <version>3.3.0</version>
                    </dependency>
            """.trimIndent()))

            assignmentService.validateAndStoreReport(validated, null)
            assertEquals(true, assignmentRepository.findById(assignment.id).get().springBoot)
        } finally {
            folder.deleteRecursively()
        }
    }
}
