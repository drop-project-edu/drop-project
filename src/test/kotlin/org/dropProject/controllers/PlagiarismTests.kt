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
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.dropproject.dao.*
import org.dropproject.dao.Language
import org.dropproject.data.*
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.dropproject.services.PlagiarismComparison
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class PlagiarismTests : ReportTestBase() {

    @Test
    fun `check plagiarism - java`() {

        testsHelper.uploadProject(this.mvc, "projectCompilationErrors", defaultAssignmentId, STUDENT_1)
        testsHelper.uploadProject(
            this.mvc, "projectJUnitErrors", defaultAssignmentId, STUDENT_2,
            listOf(STUDENT_2.username to "Student 2")
        )

        this.mvc.perform(post("/plagiarism/${defaultAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/plagiarism/${defaultAssignmentId}"))

        // the result of the check was stored, so it can be consulted without running the check again
        val mvcResult = this.mvc.perform(get("/plagiarism/${defaultAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val comparisons = mvcResult.modelAndView!!.modelMap["comparisons"] as List<PlagiarismComparison>
        assertEquals(1, comparisons.size)
        assertEquals(0, comparisons[0].matchId)
        assertThat(comparisons[0].firstSubmission.id.toInt(), either(`is`(1)).or(`is`(2)))
        assertThat(comparisons[0].secondSubmission.id.toInt(), either(`is`(1)).or(`is`(2)))
        assertEquals(1, comparisons[0].firstNumTries)
        assertEquals(1, comparisons[0].secondNumTries)
        assertEquals(80, comparisons[0].similarityPercentage)

        this.mvc.perform(get("/plagiarism/${defaultAssignmentId}/report").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "attachment; filename=dp-jplag-${defaultAssignmentId}-report.zip"))
    }

    @Test
    fun `check plagiarism - kotlin`() {

        val assignmentKotlin = Assignment(id = "testKotlinProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropproject.samples.samplekotlinassignment", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, language = Language.KOTLIN,
            gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testKotlinProj2")
        assignmentRepository.save(assignmentKotlin)

        testsHelper.uploadProject(this.mvc, "projectKotlinOK", "testKotlinProj", STUDENT_1, language  = Language.KOTLIN)
        testsHelper.uploadProject(this.mvc, "projectKotlinOK2", "testKotlinProj", STUDENT_2, language  = Language.KOTLIN)

        this.mvc.perform(post("/plagiarism/testKotlinProj").with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/plagiarism/testKotlinProj"))

        val mvcResult = this.mvc.perform(get("/plagiarism/testKotlinProj").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val comparisons = mvcResult.modelAndView!!.modelMap["comparisons"] as List<PlagiarismComparison>
        assertEquals(1, comparisons.size)
        assertEquals(0, comparisons[0].matchId)
        assertThat(comparisons[0].firstSubmission.id.toInt(), either(`is`(1)).or(`is`(2)))
        assertThat(comparisons[0].secondSubmission.id.toInt(), either(`is`(1)).or(`is`(2)))
        assertEquals(1, comparisons[0].firstNumTries)
        assertEquals(1, comparisons[0].secondNumTries)
        assertEquals(91, comparisons[0].similarityPercentage)

        this.mvc.perform(get("/plagiarism/testKotlinProj/report").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "attachment; filename=dp-jplag-testKotlinProj-report.zip"))


    }

    @Test
    fun `plagiarism report is stored`() {

        testsHelper.uploadProject(this.mvc, "projectCompilationErrors", defaultAssignmentId, STUDENT_1)
        testsHelper.uploadProject(
            this.mvc, "projectJUnitErrors", defaultAssignmentId, STUDENT_2,
            listOf(STUDENT_2.username to "Student 2")
        )

        // before any check, the page shows that this assignment was never checked
        val beforeCheck = this.mvc.perform(get("/plagiarism/${defaultAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn()
        assertNull(beforeCheck.modelAndView!!.modelMap["lastCheck"])

        this.mvc.perform(post("/plagiarism/${defaultAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isFound)

        val firstVisit = this.mvc.perform(get("/plagiarism/${defaultAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn()
        val storedCheck = firstVisit.modelAndView!!.modelMap["lastCheck"] as PlagiarismCheck
        assertEquals(2, storedCheck.numSubmissions)
        assertEquals("teacher1", storedCheck.checkedBy)
        assertEquals(true, firstVisit.modelAndView!!.modelMap["reportFileAvailable"])

        @Suppress("UNCHECKED_CAST")
        val comparisons = firstVisit.modelAndView!!.modelMap["comparisons"] as List<PlagiarismComparison>
        assertEquals(1, comparisons.size)
        assertEquals(80, comparisons[0].similarityPercentage)

        // visiting the page again shows the very same check, instead of running a new one
        val secondVisit = this.mvc.perform(get("/plagiarism/${defaultAssignmentId}").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn()
        val sameCheck = secondVisit.modelAndView!!.modelMap["lastCheck"] as PlagiarismCheck
        assertEquals(storedCheck.id, sameCheck.id)
        assertEquals(storedCheck.checkDate, sameCheck.checkDate)
    }
}
