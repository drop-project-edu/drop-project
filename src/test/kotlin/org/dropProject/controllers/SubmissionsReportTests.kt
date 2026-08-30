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
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.dropproject.dao.*
import org.dropproject.data.*
import org.dropproject.repository.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import java.util.*
import kotlin.collections.LinkedHashMap

@DropProjectIntegrationTest
@Tag("integration")
class SubmissionsReportTests : ReportTestBase() {

    @Test
    fun `report is not accessible to students`() {

        this.mvc.perform(get("/report/testJavaProj")
            .with(user(STUDENT_1)))
            .andExpect(status().isForbidden)
            .andExpect(forwardedUrl("/access-denied"))
    }

    @Test
    fun `report for multiple submissions`() {

        testsHelper.makeSeveralSubmissions(
            listOf(
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectInvalidStructure1"
            ), mvc
        )

        val reportResult = this.mvc.perform(
            get("/report/testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val report = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>

        assertEquals(4, report.size, "report should have 4 lines")
        assertEquals("student1", report[0].projectGroup.authorsIdStr())
        assertEquals(2, report[0].allSubmissions.size)
        assertEquals("student2", report[1].projectGroup.authorsIdStr())
        assertEquals(1, report[1].allSubmissions.size)
        assertEquals("student3", report[2].projectGroup.authorsIdStr())
        assertEquals(1, report[2].allSubmissions.size)
        assertEquals("student4,student5", report[3].projectGroup.authorsIdStr())
        assertEquals(1, report[3].allSubmissions.size)
    }

    @Test
    fun `my submissions`() {

        testsHelper.uploadProject(this.mvc, "projectInvalidStructure1", defaultAssignmentId, STUDENT_1)

        val mySubmissionsResult = this.mvc.perform(
            get("/mySubmissions")
                .with(user(STUDENT_1))
        )
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val studentHistory = mySubmissionsResult.modelAndView!!.modelMap["studentHistory"] as StudentHistory

        assertEquals(STUDENT_1.username, studentHistory.author.userId)
        assertEquals(1, studentHistory.history.size)

        assertEquals(defaultAssignmentId, studentHistory.history[0].assignment.id)
        assertEquals(1, studentHistory.history[0].submissions.size)
        assertEquals(1, studentHistory.history[0].submissions[0].id)
    }

    @Test
    fun `submissions report`() {

        testsHelper.uploadProject(
            this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1,
            listOf(Pair("student1", "Student 1"))
        )
        testsHelper.uploadProject(
            this.mvc, "projectOK", "testJavaProj", STUDENT_1,
            listOf(Pair("student1", "Student 1"))
        )

        val reportResult = this.mvc.perform(get("/submissions?assignmentId=testJavaProj&groupId=1")
            .with(user(TEACHER_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val submissions = reportResult.modelAndView!!.modelMap["submissions"] as List<Submission>
        assertEquals(2, submissions.size)
        for (submission in submissions) {
            assertEquals("Student 1", submission.submitterShortName())
        }
    }

    @Test
    fun `submitter name shown is scoped to the group being displayed, not any group the student is in`() {

        // student1 submits solo, declaring himself as "Student A" -> creates group #1
        testsHelper.uploadProject(
            this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1,
            listOf(Pair("student1", "Student A"))
        )

        // the same student1, now paired with a different partner, declares himself as "Student B" -> since the
        // author composition differs, this creates a brand new group (#2), with its own Author row for student1
        testsHelper.uploadProject(
            this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1,
            listOf(Pair("student1", "Student B"), Pair("student2", "Student 2"))
        )

        // viewing group #1's history must still show "Student A", regardless of the name student1 later
        // declared for himself in the unrelated group #2
        val reportResult = this.mvc.perform(get("/submissions?assignmentId=testJavaProj&groupId=1")
            .with(user(TEACHER_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val submissions = reportResult.modelAndView!!.modelMap["submissions"] as List<Submission>
        assertEquals(1, submissions.size)
        assertEquals("Student A", submissions[0].submitterName)
    }

    @Test
    fun `student list`() {

        // create some authors
        authorRepository.save(Author(name="Sarah", userId = "student1"))
        authorRepository.save(Author(name="Cris", userId = "student2"))

        this.mvc.perform(
            get("/studentList?q=stu")
                .with(user(TEACHER_1))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                [{"value":"student1","text":"Sarah"},{"value":"student2","text":"Cris"}]
            """.trimIndent()))

        this.mvc.perform(
            get("/studentList?q=cri")
                .with(user(TEACHER_1))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                [{"value":"student2","text":"Cris"}]
            """.trimIndent()))

        this.mvc.perform(
            get("/studentList?q=banana")
                .with(user(TEACHER_1))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                []
            """.trimIndent()))

    }

    @Test
    fun `get report by other element of the group`() {

        // student1 makes a submission in name of the group (student1, student2)
        val submissionId = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", defaultAssignmentId, STUDENT_1,
            listOf(Pair("student1", "Student 1"), Pair("student2", "Student 2")))

        // student1 gets the report
        this.mvc.perform(get("/buildReport/$submissionId")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)

        // student2 gets the report
        this.mvc.perform(get("/buildReport/$submissionId")
            .with(user(STUDENT_2)))
            .andExpect(status().isOk)

        // studentOther tries to get the report but it is forbidden
        this.mvc.perform(get("/buildReport/$submissionId")
            .with(user(User("studentOther", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT"))))))
            .andExpect(status().isForbidden)

    }

    @Test
    fun `test matrix`() {
        testsHelper.uploadProject(this.mvc, "projectJUnitErrors", defaultAssignmentId, STUDENT_1)

        val reportResult = this.mvc.perform(
            get("/testMatrix/${defaultAssignmentId}")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val tests = reportResult.modelAndView!!.modelMap["tests"] as LinkedHashMap<String, Int>
        assertEquals(3, tests.size)
        assertThat(
            tests.keys.map { "${it}->${tests[it]}" }, contains(
                "testFuncaoParaTestar:TestTeacherProject->0",
                "testFuncaoLentaParaTestar:TestTeacherProject->1",
                "testFuncaoParaTestarQueNaoApareceAosAlunos:TestTeacherHiddenProject->0"
            )
        )
    }
}
