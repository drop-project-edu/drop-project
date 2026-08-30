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

import org.junit.jupiter.api.extension.ExtendWith
import org.dropproject.ResetStateExtension
import org.dropproject.controllers.InvalidProjectStructureException
import org.dropproject.dao.Assignment
import org.dropproject.dao.Language
import org.dropproject.dao.SubmissionStructure
import org.dropproject.dao.TestVisibility
import org.dropproject.forms.SubmissionMethod
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.io.File


@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
@ExtendWith(ResetStateExtension::class)
class TestSubmissionService {

    @Autowired
    private lateinit var submissionService: SubmissionService

    @Test
    fun `check authors_txt without author id`() {

        try {
            submissionService.getProjectAuthors(File("src/test/sampleAUTHORS_TXT/without_id.txt"))
            fail<Unit>("Should have thrown an exception")
        } catch (e: InvalidProjectStructureException) {
            assertEquals("The student number must be filled in for all group members", e.message)
        }
    }

    @Test
    fun `check authors_txt with blank spaces`() {

        val authors = submissionService.getProjectAuthors(File("src/test/sampleAUTHORS_TXT/with_spaces.txt"))
        assertEquals("a21700000", authors[0].number)
        assertEquals("John Doe", authors[0].name)
    }

    @Test
    fun `check authors_txt with extra empty lines`() {

        val authors = submissionService.getProjectAuthors(File("src/test/sampleAUTHORS_TXT/with_extra_empty_line.txt"))
        assertEquals("a21700000", authors[0].number)
        assertEquals("John Doe", authors[0].name)
    }

    @Test
    fun `check that SubmissionEvaluationInputs reflect the properties that determine how a submission is evaluated`() {

        val assignment = Assignment(id = "dummy", name = "Dummy", gitRepositoryUrl = "",
            gitRepositoryFolder = "", ownerUserId = "p4997", submissionMethod = SubmissionMethod.UPLOAD,
            packageName = "org.dropProject.samples", language = Language.JAVA,
            submissionStructure = SubmissionStructure.MAVEN, acceptsStudentTests = false, minStudentTests = null,
            calculateStudentTestsCoverage = false, maxMemoryMb = null,
            hiddenTestsVisibility = TestVisibility.HIDE_EVERYTHING)

        val evaluationInputs = SubmissionEvaluationInputs.from(assignment)

        assertEquals(evaluationInputs, SubmissionEvaluationInputs.from(assignment))

        // properties that require the submissions to be rebuilt
        assignment.packageName = "org.dropProject.other"
        assertNotEquals(evaluationInputs, SubmissionEvaluationInputs.from(assignment))
        assignment.packageName = "org.dropProject.samples"

        assignment.language = Language.KOTLIN
        assertNotEquals(evaluationInputs, SubmissionEvaluationInputs.from(assignment))
        assignment.language = Language.JAVA

        assignment.submissionStructure = SubmissionStructure.COMPACT
        assertNotEquals(evaluationInputs, SubmissionEvaluationInputs.from(assignment))
        assignment.submissionStructure = SubmissionStructure.MAVEN

        assignment.acceptsStudentTests = true
        assertNotEquals(evaluationInputs, SubmissionEvaluationInputs.from(assignment))
        assignment.acceptsStudentTests = false

        assignment.minStudentTests = 2
        assertNotEquals(evaluationInputs, SubmissionEvaluationInputs.from(assignment))
        assignment.minStudentTests = null

        assignment.calculateStudentTestsCoverage = true
        assertNotEquals(evaluationInputs, SubmissionEvaluationInputs.from(assignment))
        assignment.calculateStudentTestsCoverage = false

        assignment.maxMemoryMb = 1024
        assertNotEquals(evaluationInputs, SubmissionEvaluationInputs.from(assignment))
        assignment.maxMemoryMb = null

        // properties that are re-evaluated everytime the build report is rendered, so they don't require a rebuild
        assignment.mandatoryTestsSuffix = "_MANDATORY"
        assignment.hiddenTestsVisibility = TestVisibility.SHOW_PROGRESS
        assertEquals(evaluationInputs, SubmissionEvaluationInputs.from(assignment))
    }
}
