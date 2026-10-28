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
import org.dropproject.FakeClock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import java.time.LocalDateTime

@DropProjectIntegrationTest
@Tag("integration")
class UploadCooloffTests : UploadTestBase() {

    @Autowired
    lateinit var fakeClock: FakeClock

    private fun setCooloff(minutes: Int) {
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.cooloffPeriod = minutes
        assignmentRepository.save(assignment)
    }

    private fun at(time: String) = fakeClock.setNow(LocalDateTime.parse("2026-11-03T$time"))

    // the time the student can submit again, as shown on the upload page, or null if they can submit now
    private fun coolOffEnd(): Any? =
        this.mvc.perform(get("/upload/testJavaProj").with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-upload-form"))
                .andReturn().modelAndView!!.model["coolOffEnd"]

    @Test
    fun `get upload page with cooloff`() {

        setCooloff(10)

        assertNull(coolOffEnd())
    }

    @Test
    fun `upload project then cooloff`() {

        setCooloff(10)

        at("10:00:30")
        submissionFixtures.uploadProject("projectCheckstyleErrors", "testJavaProj", STUDENT_1)

        assertEquals("10:10", coolOffEnd())

        // still in the cool-off period, so a new submission is refused
        at("10:09:00")
        assertEquals("10:10", coolOffEnd())
        // (the cool-off period is checked before the file is even looked at)
        this.mvc.perform(multipart("/upload")
                .file(MockMultipartFile("file", "project.zip", "application/zip", ByteArray(0)))
                .param("assignmentId", "testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isForbidden)

        // the cool-off period is over
        at("10:10:30")
        assertNull(coolOffEnd())
        submissionFixtures.uploadProject("projectCheckstyleErrors", "testJavaProj", STUDENT_1)
    }

    @Test
    fun `upload project with compilation errors then cooloff`() { // cooloff is reduced for structure or compilation errors

        setCooloff(10)

        at("10:00:30")
        submissionFixtures.uploadProject("projectCompilationErrors", "testJavaProj", STUDENT_1)

        assertEquals("10:02", coolOffEnd())

        at("10:02:30")
        assertNull(coolOffEnd())
        submissionFixtures.uploadProject("projectCompilationErrors", "testJavaProj", STUDENT_1)
    }

    @Test
    fun `try to upload with cooloff then disable and upload again`() {

        setCooloff(10)

        at("10:00:30")
        submissionFixtures.uploadProject("projectCheckstyleErrors", "testJavaProj", STUDENT_1)
        assertEquals("10:10", coolOffEnd())

        // Teacher disables cooloff for 30 minutes
        this.mvc.perform(post("/assignment/cooloff/testJavaProj/disable")
            .param("duration", "30")
            .with(user(TEACHER_1)))
            .andExpect(status().isOk)

        assertNull(coolOffEnd())
        assertEquals(30, cooloffOverrideService.getRemainingMinutes("testJavaProj"))

        // while it is disabled, the student can submit as often as they want
        at("10:25:00")
        assertEquals(5, cooloffOverrideService.getRemainingMinutes("testJavaProj"))
        submissionFixtures.uploadProject("projectCheckstyleErrors", "testJavaProj", STUDENT_1)
        assertNull(coolOffEnd())

        // once the override expires, the cool-off period applies again, counting from the last submission
        at("10:31:00")
        assertNull(cooloffOverrideService.getRemainingMinutes("testJavaProj"))
        assertEquals("10:35", coolOffEnd())
    }
}
