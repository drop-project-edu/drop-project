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
import org.dropproject.dao.*
import org.dropproject.repository.*
import org.hamcrest.CoreMatchers.*
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class UploadCooloffTests : UploadTestBase() {

    @Test
    fun `get upload page with cooloff`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.cooloffPeriod = 10
        assignmentRepository.save(assignment)

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-upload-form"))


    }

    @Test
    fun `upload project with compilation errors then cooloff`() { // cooloff is reduced for structure or compilation errors

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.cooloffPeriod = 10
        assignmentRepository.save(assignment)

        submissionFixtures.uploadProject("projectCompilationErrors", "testJavaProj", STUDENT_1)
        val now = LocalTime.now()

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-upload-form"))
                // Accept both +2 and +1 minutes because a minute boundary
                // may be crossed between the upload and this assertion
                .andExpect(model().attribute("coolOffEnd",
                        anyOf(
                            equalTo(now.plusMinutes(2).format(DateTimeFormatter.ofPattern("HH:mm"))),
                            equalTo(now.plusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm")))
                        )))
    }

    @Test
    fun `upload project then cooloff`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.cooloffPeriod = 10
        assignmentRepository.save(assignment)

        submissionFixtures.uploadProject("projectCheckstyleErrors", "testJavaProj", STUDENT_1)
        val now = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-upload-form"))
                // Accept both +10 and +9 minutes because a minute boundary
                // may be crossed between the upload and this assertion
                .andExpect(model().attribute("coolOffEnd",
                        anyOf(
                            equalTo(now.plusMinutes(10).format(formatter)),
                            equalTo(now.plusMinutes(9).format(formatter))
                        )))
    }

    @Test
    fun `try to upload with cooloff then disable and upload again`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.cooloffPeriod = 10
        assignmentRepository.save(assignment)

        submissionFixtures.uploadProject("projectCheckstyleErrors", "testJavaProj", STUDENT_1)
        val now = LocalTime.now()

        this.mvc.perform(get("/upload/testJavaProj")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-upload-form"))
            // Accept both +10 and +9 minutes because a minute boundary
            // may be crossed between the upload and this assertion
            .andExpect(model().attribute("coolOffEnd",
                anyOf(
                    equalTo(now.plusMinutes(10).format(DateTimeFormatter.ofPattern("HH:mm"))),
                    equalTo(now.plusMinutes(9).format(DateTimeFormatter.ofPattern("HH:mm")))
                )))

        // Teacher disables cooloff for 30 minutes
        this.mvc.perform(post("/assignment/cooloff/${assignment.id}/disable")
            .param("duration", "30")
            .with(user(TEACHER_1)))
            .andExpect(status().isOk)

        this.mvc.perform(get("/upload/testJavaProj")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-upload-form"))
            .andExpect(model().attributeDoesNotExist("coolOffEnd"))
    }
}
