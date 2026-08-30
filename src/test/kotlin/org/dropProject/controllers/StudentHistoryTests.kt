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
import org.dropproject.TestUsers.TEACHER_1
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.dropproject.dao.*
import org.dropproject.data.*
import org.dropproject.repository.*
import org.hamcrest.Matchers.*
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class StudentHistoryTests : ReportTestBase() {

    @Test
    fun `student history`() {

        /**
         *
         * TODO
         *
         * It's very slow.
         * Should include information about the group, when the sumission is individual
         * Must also test /studentHistoryForm and /studentList
         *
         */

        submissionFixtures.uploadProject("projectInvalidStructure1", "testJavaProj", STUDENT_1,
            listOf(Pair("student1", "Student 1")))
        submissionFixtures.uploadProject("projectOK", "testJavaProj", STUDENT_1,
            listOf(Pair("student1", "Student 1")))
        submissionFixtures.uploadProject("projectOK", "sampleJavaProject", STUDENT_1,
            listOf(Pair("student1", "Student 1"), Pair("student2", "Student 2")))

        // make sure the last submission has a submissionDate superior to the previous ones,
        // since the sorted history assertions below depend on it
        val lastSubmission = submissionRepository.findAll().maxByOrNull { it.id }!!
        lastSubmission.submissionDate = Date(lastSubmission.submissionDate.time + 60_000)
        submissionRepository.save(lastSubmission)

        mvc.perform(get("/studentHistoryForm").with(user(TEACHER_1))).andExpect(status().isOk)

        val reportResult =
            this.mvc.perform(
                get("/studentHistory?id=${STUDENT_1.username}")
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isOk)
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val studentHistory = reportResult.modelAndView!!.modelMap["studentHistory"] as StudentHistory

        assertEquals(STUDENT_1.username, studentHistory.author.userId)
        assertEquals(2, studentHistory.history.size)

        assertEquals("testJavaProj", studentHistory.history[0].assignment.id)
        assertEquals(2, studentHistory.history[0].submissions.size)
        assertEquals(1, studentHistory.history[0].submissions[0].id)
        assertEquals(2, studentHistory.history[0].submissions[1].id)

        assertEquals("sampleJavaProject", studentHistory.history[1].assignment.id)
        assertEquals(1, studentHistory.history[1].submissions.size)
        assertEquals(3, studentHistory.history[1].submissions[0].id)
        assertEquals("student1", studentHistory.history[1].submissions[0].submitterUserId)
        assertEquals("Student 1", studentHistory.history[1].submissions[0].submitterShortName())

        // test sorted history
        val sortedHistory = studentHistory.getHistorySortedByDateDesc()
        assertEquals("sampleJavaProject", sortedHistory[0].assignment.id)
        assertEquals("testJavaProj", sortedHistory[1].assignment.id)

    }

    @Test
    fun `student history shows the submitter name declared in each submission's own group`() {

        // student1 submits solo, declaring himself as "Student A" -> group #1
        submissionFixtures.uploadProject("projectInvalidStructure1", "testJavaProj", STUDENT_1,
            listOf(Pair("student1", "Student A")))

        // the same student1, paired with a different partner, declares himself as "Student B" -> since the
        // author composition differs, this is a different group (#2), with its own Author row for student1
        submissionFixtures.uploadProject("projectOK", "sampleJavaProject", STUDENT_1,
            listOf(Pair("student1", "Student B"), Pair("student2", "Student 2")))

        val reportResult = this.mvc.perform(
            get("/studentHistory?id=${STUDENT_1.username}").with(user(TEACHER_1))
        )
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val studentHistory = reportResult.modelAndView!!.modelMap["studentHistory"] as StudentHistory

        // each submission must show the name student1 declared in that submission's own group, not whichever
        // group's Author row happens to be resolved first/last
        assertEquals("testJavaProj", studentHistory.history[0].assignment.id)
        assertEquals("Student A", studentHistory.history[0].submissions[0].submitterName)

        assertEquals("sampleJavaProject", studentHistory.history[1].assignment.id)
        assertEquals("Student B", studentHistory.history[1].submissions[0].submitterName)
    }
}
