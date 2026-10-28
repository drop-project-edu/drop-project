/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2026 Pedro Alves
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
package org.dropproject.dao

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.dropproject.DropProjectIntegrationTest
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssignmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import jakarta.persistence.EntityManager

@DropProjectIntegrationTest
class AssignmentSubmissionWindowsTests {

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var entityManager: EntityManager

    private fun newAssignment() = Assignment(id = "windowsAssignment", name = "Windows", ownerUserId = "teacher1",
        submissionMethod = SubmissionMethod.UPLOAD, gitRepositoryUrl = "git://dummy",
        gitRepositoryFolder = "windowsAssignment")

    private fun storedWindows() =
        (entityManager.createNativeQuery("select count(*) from submission_window").singleResult as Number).toLong()

    @Test
    fun `the windows are stored, replaced and deleted with the assignment`() {
        val assignment = newAssignment()
        assignment.submissionWindowsText = "2026-11-03 14:00-16:00, 2026-11-03 09:00-11:00"
        assignmentRepository.save(assignment)

        val stored = assignmentRepository.findById("windowsAssignment").get()
        assertEquals("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00", stored.submissionWindowsText)
        assertEquals(2L, storedWindows())

        // the windows that are no longer in the list are deleted
        stored.submissionWindowsText = "2026-11-04 09:00-11:00"
        assignmentRepository.save(stored)
        assertEquals("2026-11-04 09:00-11:00",
            assignmentRepository.findById("windowsAssignment").get().submissionWindowsText)
        assertEquals(1L, storedWindows())

        assignmentRepository.deleteById("windowsAssignment")
        assertEquals(0L, storedWindows())
    }

    @Test
    fun `saving the same assignment again doesn't duplicate its windows`() {
        // e.g. the creation of an assignment saves it once before connecting it to git, and once after
        val assignment = newAssignment()
        assignment.submissionWindowsText = "2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00"
        assignmentRepository.save(assignment)
        assignmentRepository.save(assignment)

        assertEquals("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00",
            assignmentRepository.findById("windowsAssignment").get().submissionWindowsText)
        assertEquals(2L, storedWindows())
    }

    @Test
    fun `the windows are exported and imported as text`() {
        val assignment = newAssignment()
        assignment.submissionWindowsText = "2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00"

        // the same mapper that exports and imports assignments
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val json = mapper.writeValueAsString(assignment)
        assertEquals("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00", mapper.readTree(json).at("/submissionWindows").asText())

        val imported = mapper.readValue(json, Assignment::class.java)
        assertEquals("2026-11-03 09:00-11:00, 2026-11-03 14:00-16:00", imported.submissionWindowsText)

        // an assignment without windows is exported without them, and imported without them
        val withoutWindows = mapper.writeValueAsString(newAssignment())
        assertFalse(mapper.readTree(withoutWindows).at("/submissionWindows").isTextual)
        assertNull(mapper.readValue(withoutWindows, Assignment::class.java).submissionWindowsText)
    }
}
