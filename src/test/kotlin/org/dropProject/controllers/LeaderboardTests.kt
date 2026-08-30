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
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.nio.file.Files
import org.dropproject.dao.*
import org.dropproject.data.*
import org.dropproject.repository.*
import org.hamcrest.Matchers.*
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class LeaderboardTests : ReportTestBase() {

    @Test
    fun `leaderboard not accessible`() {
        this.mvc.perform(
            get("/leaderboard/testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isForbidden())
    }

    @Test
    fun `leaderboard ok`() {

        val assignment = assignmentRepository.findById(defaultAssignmentId).get()
        assignment.showLeaderBoard = true
        assignment.leaderboardType = LeaderboardType.ELLAPSED
        assignmentRepository.save(assignment)

        // we start with two authors
        val projectRoot = resourceLoader.getResource("file:src/test/sampleProjects/compact/java/projectOK").file
        val path = File(projectRoot, "AUTHORS.txt").toPath()
        val lines = Files.readAllLines(path)
        assertEquals("student1;Student 1", lines[0])
        assertEquals("student2;Student 2", lines[1])

        val student3 = User("student3", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

        try {
            // upload five times, each time with a different author
            testsHelper.uploadProject(this.mvc, "projectOK", defaultAssignmentId, STUDENT_1)
            testsHelper.uploadProject(
                this.mvc, "projectOK", defaultAssignmentId, STUDENT_2,
                authors = listOf(STUDENT_2.username to "Student 2")
            )
            testsHelper.uploadProject(
                this.mvc, "projectJUnitErrors", defaultAssignmentId, student3,
                authors = listOf(student3.username to "Student 3")
            )
            testsHelper.uploadProject(
                this.mvc, "projectOK", defaultAssignmentId, STUDENT_2,
                authors = listOf(STUDENT_2.username to "Student 2")
            )
            testsHelper.uploadProject(
                this.mvc, "projectOK", defaultAssignmentId, STUDENT_1,
                authors = listOf(STUDENT_1.username to "Student 3")
            )

        } finally {
            // restore original AUTHORS.txt
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write(lines[1])
            writer.close()
        }

        val reportResult = this.mvc.perform(
            get("/leaderboard/testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val report = reportResult.modelAndView!!.modelMap["submissions"] as List<Submission>

        assertEquals(4, report.size, "report should have 4 lines")
        assertEquals("student3", report[3].group.authorsIdStr())  // this should be the last one because it has junit errors

        // the others should pass all tests and have ascending order of ellapsed time
        val others = report.dropLast(1)
        assertTrue(others.all { it.teacherTests?.progress == 2 }, "should pass all tests")

        val ellapsedList = others.map { it.ellapsed }
        val ellapsedSortedList = ellapsedList.sortedBy { it }
        assertArrayEquals(ellapsedSortedList.toTypedArray(), ellapsedList.toTypedArray())

    }

    @Test
    fun `leaderboard group URL has no extra slash`() {
        val assignment = assignmentRepository.findById(defaultAssignmentId).get()
        assignment.showLeaderBoard = true
        assignment.leaderboardType = LeaderboardType.ELLAPSED
        assignmentRepository.save(assignment)

        val projectRoot = resourceLoader.getResource("file:src/test/sampleProjects/compact/java/projectOK").file
        val path = File(projectRoot, "AUTHORS.txt").toPath()
        val lines = Files.readAllLines(path)

        try {
            testsHelper.uploadProject(this.mvc, "projectOK", defaultAssignmentId, STUDENT_1)
        } finally {
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write(lines[1])
            writer.close()
        }

        val result = this.mvc.perform(
            get("/leaderboard/testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk)
            .andReturn()

        val content = result.response.contentAsString
        assert(content.contains("/submissions?assignmentId=")) { "URL should contain /submissions?assignmentId= -  no extra slash found before query string" }
    }
}
