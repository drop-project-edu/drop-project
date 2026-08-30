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
import org.apache.commons.io.FileUtils
import org.dropproject.dao.*
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.hamcrest.Matchers.*
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.*


@DropProjectIntegrationTest
@Tag("integration")
class AssignmentDeleteTests : AssignmentTestBase() {

    @Test
    fun `delete assignment`() {

        // make a copy of the "testJavaProj" assignment files and create an assignment based on the copy
        // so that we can safely delete it, without affecting the original files
        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, "testJavaProjForDelete")
        FileUtils.copyDirectory(File(dropProjectProperties.assignments.rootLocation, "testJavaProj"), assignmentFolder)

        // create initial assignment
        val assignment = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjForDelete"
        )
        assignmentRepository.save(assignment)
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student2"))


        // make a submission
        val submissionId =
            submissionFixtures.uploadProject("projectCompilationErrors", "testJavaProj", STUDENT_1).toLong()

        // try to delete the assignment but DP will issue an error since it has submissions
        this.mvc.perform(
            post("/assignment/delete")
                .param("ids", "testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("error", "Assignment testJavaProj can't be deleted because it has submissions"))

        // remove the submission
        submissionRepository.deleteById(submissionId)

        // try to delete the assignment again, this time with success
        this.mvc.perform(
            post("/assignment/delete")
                .param("ids", "testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message", "Assignment was successfully deleted"))

        // check if the assignment folder was also deleted
        assertFalse(assignmentFolder.exists(), "$assignmentFolder should have been deleted")
    }

    @Test
    fun `delete assignment with other assignments`() {

        // make a copy of the "testJavaProj" assignment files and create an assignment based on the copy
        // so that we can safely delete it, without affecting the original files
        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, "testJavaProjForDelete")
        FileUtils.copyDirectory(File(dropProjectProperties.assignments.rootLocation, "testJavaProj"), assignmentFolder)

        // create two initial assignments
        val assignment1 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjForDelete"
        )
        assignmentService.addTagToAssignment(assignment1, "teste")
        assignmentRepository.save(assignment1)
        assigneeRepository.save(Assignee(assignmentId = assignment1.id, authorUserId = "student1"))

        // create two initial assignments
        val assignment2 = Assignment(
            id = "testJavaProj2", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjForDelete"
        )
        assignmentService.addTagToAssignment(assignment2, "teste")
        assignmentRepository.save(assignment2)
        assigneeRepository.save(Assignee(assignmentId = assignment2.id, authorUserId = "student1"))


        // delete the assignment 1
        this.mvc.perform(
            post("/assignment/delete")
                .param("ids", "testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message", "Assignment was successfully deleted"))

        // check my assignments
        this.mvc.perform(
            get("/assignment/my")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk)
            .andExpect(model().hasNoErrors())
            .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))

    }

    @Test
    fun `delete assignment with force`() {

        // make a copy of the "testJavaProj" assignment files and create an assignment based on the copy
        // so that we can safely delete it, without affecting the original files
        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, "testJavaProjForDelete")
        FileUtils.copyDirectory(File(dropProjectProperties.assignments.rootLocation, "testJavaProj"), assignmentFolder)

        // create initial assignment
        val assignment = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjForDelete"
        )
        assignmentRepository.save(assignment)
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student2"))


        // make a submission
        val submissionId =
            submissionFixtures.uploadProject("projectCompilationErrors", "testJavaProj", STUDENT_1).toLong()
        val submission = submissionRepository.findById(submissionId).get()

        // try to delete the assignment with force = true using someone who hasn't the admin role
        this.mvc.perform(
            post("/assignment/delete")
                .param("ids", "testJavaProj")
                .param("force", "true")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isForbidden)

        // try to delete the assignment with force = true using someone who has the admin role
        this.mvc.perform(
            post("/assignment/delete")
                .param("ids", "testJavaProj")
                .param("force", "true")
                .with(user(User("admin", "", mutableListOf(SimpleGrantedAuthority("ROLE_DROP_PROJECT_ADMIN")))))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message", "Assignment was successfully deleted"))

        // check if the assignment folder was deleted
        assertFalse(assignmentFolder.exists(), "$assignmentFolder should have been deleted")

        // check if the submission folder was deleted
        val submissionFolder = File(dropProjectProperties.storage.uploadLocation, submission.submissionFolder)
        assertFalse(submissionFolder.exists(), "$submissionFolder should have been deleted")

        // check if the submission was deleted from the database
        assertTrue(submissionRepository.findById(submissionId).isEmpty, "$submissionId should have been deleted from the DB")
    }

    @Test
    fun `delete assignment and check repositories`() {

        // make a copy of the "testJavaProj" assignment files and create an assignment based on the copy
        // so that we can safely delete it, without affecting the original files
        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, "testJavaProjForDelete")
        FileUtils.copyDirectory(File(dropProjectProperties.assignments.rootLocation, "testJavaProj"), assignmentFolder)

        // create initial assignment
        val assignment = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjForDelete"
        )
        assignmentRepository.save(assignment)
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))

        //check if assignment is in Assignee Repository
        assertTrue(assigneeRepository.findByAuthorUserId(STUDENT_1.username).isNotEmpty(), "${assignment.id} should be in assignee repository")

        //check if assignment is in assignment Repository
        assertTrue(assignmentRepository.existsById(assignment.id), "${assignment.id} should be in assignment repository")

        // succeed on deleting the assignment
        this.mvc.perform(
            post("/assignment/delete")
                .param("ids", "testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message", "Assignment was successfully deleted"))

        //check if assignment was deleted in Assignee Repository
        assertFalse(assigneeRepository.existsByAssignmentId(assignment.id), "${assignment.id} should have been deleted from assignee repository")
        assertTrue(assigneeRepository.findByAuthorUserId(STUDENT_1.username).isEmpty(), "${assignment.id} should have been deleted from assignee repository")

        //check if assignment was deleted in assignment Repository
        assertFalse(assignmentRepository.existsById(assignment.id), "${assignment.id} should have been deleted from assignment repository")
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `delete assignment with tags`() {

        this.mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "dummyAssignmentToDelete")
                .param("assignmentName", "Dummy Assignment")
                .param("assignmentPackage", "org.dummy")
                .param("submissionMethod", "UPLOAD")
                .param("language", "JAVA")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                .param("assignmentTags", "sample,test")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/setup-git/dummyAssignmentToDelete"))

        assertEquals(2, assignmentTagRepository.findAll().size)

        this.mvc.perform(post("/assignment/delete").param("ids", "dummyAssignmentToDelete"))
            .andExpect(status().isFound)
            .andExpect(flash().attribute("message", "Assignment was successfully deleted"))
            .andExpect(header().string("Location", "/assignment/my"))

        assertFalse(assignmentRepository.existsById("dummyAssignmentToDelete"))

        // no other assignment was using these tags, so they should have been removed from the global list
        assertEquals(0, assignmentTagRepository.findAll().size)
    }

    @Test
    fun `delete several assignments with their submissions`() {

        // the assignment folder is a copy of the sample one, since the deletion removes it from the disk
        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, "testJavaProjToDelete")
        FileUtils.copyDirectory(File(dropProjectProperties.assignments.rootLocation, "testJavaProj"), assignmentFolder)

        val assignment01 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjToDelete"
        )
        assignmentRepository.save(assignment01)

        val assignment02 = Assignment(
            id = "anotherProjToDelete", name = "Another Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo2",
            gitRepositoryFolder = "anotherProjToDelete"
        )
        assignmentRepository.save(assignment02)

        submissionFixtures.makeSeveralSubmissions(listOf("projectOK", "projectInvalidStructure1"))
        assertEquals(2, submissionRepository.countByAssignmentIdAndStatusNot("testJavaProj",
            SubmissionStatus.DELETED.code))

        // deleting the submissions together with the assignment is only allowed to admins
        this.mvc.perform(
            post("/assignment/delete")
                .with(user(TEACHER_1))
                .param("ids", "testJavaProj")
                .param("force", "true")
        )
            .andExpect(status().isForbidden)

        assertTrue(assignmentRepository.existsById("testJavaProj"))

        val admin = User("admin", "", mutableListOf(
            SimpleGrantedAuthority("ROLE_TEACHER"), SimpleGrantedAuthority("ROLE_DROP_PROJECT_ADMIN")))

        this.mvc.perform(
            post("/assignment/delete")
                .with(user(admin))
                .param("ids", "testJavaProj", "anotherProjToDelete")
                .param("force", "true")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(
                flash().attribute(
                    "message",
                    "Deleted 2 assignments, together with all their submissions"
                )
            )

        assertFalse(assignmentRepository.existsById("testJavaProj"))
        assertFalse(assignmentRepository.existsById("anotherProjToDelete"))
        assertEquals(0, submissionRepository.countByAssignmentIdAndStatusNot("testJavaProj",
            SubmissionStatus.DELETED.code))
        assertFalse(assignmentFolder.exists(), "the folder of the assignment was not deleted")
    }
}
