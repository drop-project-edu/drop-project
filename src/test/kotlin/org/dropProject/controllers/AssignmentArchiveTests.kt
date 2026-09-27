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

import org.dropproject.DropProjectIntegrationTest
import org.dropproject.TestUsers.TEACHER_1
import org.dropproject.TestUsers.TEACHER_2
import org.dropproject.dao.AssignmentACL
import org.dropproject.repository.AssignmentACLRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@DropProjectIntegrationTest
class AssignmentArchiveTests : AssignmentTestBase() {

    @Autowired
    lateinit var assignmentACLRepository: AssignmentACLRepository

    private fun isArchived(assignmentId: String) = assignmentRepository.findById(assignmentId).get().archived

    @Test
    fun `archive a single assignment`() {
        assignmentFixtures.createDefaultAssignment(id = "assignmentToArchive")

        this.mvc.perform(post("/assignment/archive").param("ids", "assignmentToArchive").with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message",
                "Assignment was archived. You can now find it in the Archived assignments page"))

        assertTrue(isArchived("assignmentToArchive"))
    }

    @Test
    fun `archive several assignments at once`() {
        assignmentFixtures.createDefaultAssignment(id = "assignmentToArchive1")
        assignmentFixtures.createDefaultAssignment(id = "assignmentToArchive2")
        assignmentFixtures.createDefaultAssignment(id = "assignmentToKeep")

        // the same assignment may come twice, from the hidden inputs of the pages of the table that are not shown
        this.mvc.perform(post("/assignment/archive")
                .param("ids", "assignmentToArchive1", "assignmentToArchive2", "assignmentToArchive1")
                .with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message",
                "Archived 2 assignments. You can now find them in the Archived assignments page"))

        assertTrue(isArchived("assignmentToArchive1"))
        assertTrue(isArchived("assignmentToArchive2"))
        assertFalse(isArchived("assignmentToKeep"))
    }

    @Test
    fun `a teacher in the acl of an assignment can archive it`() {
        assignmentFixtures.createDefaultAssignment(id = "assignmentToArchive")
        assignmentACLRepository.save(AssignmentACL(assignmentId = "assignmentToArchive", userId = "teacher2"))

        this.mvc.perform(post("/assignment/archive").param("ids", "assignmentToArchive").with(user(TEACHER_2)))
            .andExpect(status().isFound)

        assertTrue(isArchived("assignmentToArchive"))
    }

    @Test
    fun `an admin can archive an assignment of another teacher`() {
        assignmentFixtures.createDefaultAssignment(id = "assignmentToArchive")
        val admin = User("admin", "", mutableListOf(
            SimpleGrantedAuthority("ROLE_TEACHER"), SimpleGrantedAuthority("ROLE_DROP_PROJECT_ADMIN")))

        this.mvc.perform(post("/assignment/archive").param("ids", "assignmentToArchive").with(user(admin)))
            .andExpect(status().isFound)

        assertTrue(isArchived("assignmentToArchive"))
    }

    @Test
    fun `nothing is archived if one of the assignments belongs to another teacher`() {
        assignmentFixtures.createDefaultAssignment(id = "assignmentOfTeacher1")
        assignmentACLRepository.save(AssignmentACL(assignmentId = "assignmentOfTeacher1", userId = "teacher2"))
        assignmentFixtures.createDefaultAssignment(id = "anotherAssignmentOfTeacher1")

        // teacher2 may archive the first one, through the acl, but not the second
        this.mvc.perform(post("/assignment/archive")
                .param("ids", "assignmentOfTeacher1", "anotherAssignmentOfTeacher1")
                .with(user(TEACHER_2)))
            .andExpect(status().isForbidden)

        assertFalse(isArchived("assignmentOfTeacher1"))
        assertFalse(isArchived("anotherAssignmentOfTeacher1"))
    }

    @Test
    fun `nothing is archived if one of the assignments no longer exists`() {
        assignmentFixtures.createDefaultAssignment(id = "assignmentToArchive")

        this.mvc.perform(post("/assignment/archive")
                .param("ids", "assignmentToArchive", "deletedMeanwhile")
                .with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(flash().attribute("error",
                "Error: The assignment deletedMeanwhile no longer exists. Please refresh the page and try again"))

        assertFalse(isArchived("assignmentToArchive"))
    }

    @Test
    fun `archive without selecting any assignment`() {
        this.mvc.perform(post("/assignment/archive").with(user(TEACHER_1)))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("error", "Error: You didn't select any assignment to archive"))
    }

    @Test
    fun `the archive bulk action is only offered in the list of assignments that are not archived`() {
        assignmentFixtures.createDefaultAssignment(id = "assignmentToArchive")

        this.mvc.perform(get("/assignment/my").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("data-target=\"#bulkArchiveModal\"")))

        this.mvc.perform(post("/assignment/archive").param("ids", "assignmentToArchive").with(user(TEACHER_1)))

        this.mvc.perform(get("/assignment/archived").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("bulkArchiveModal"))))
    }
}
