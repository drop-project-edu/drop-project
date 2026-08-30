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
import org.dropproject.dao.*
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.*
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.*


@DropProjectIntegrationTest
@Tag("integration")
class AssignmentTagsTests : AssignmentTestBase() {

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create new assignment with tags`() {

        // check available tags
        // it shouldn't exist none
        var globalTags = assignmentTagRepository.findAll()
        assertEquals(0, globalTags.size)

        // post form
        this.mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "dummyAssignmentTags")
                .param("assignmentName", "Dummy Assignment")
                .param("assignmentPackage", "org.dummy")
                .param("submissionMethod", "UPLOAD")
                .param("language", "JAVA")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                .param("assignmentTags", "sample,test,simple")
        )
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/assignment/setup-git/dummyAssignmentTags"))

        // get assignment detail
        val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignmentTags"))
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val assignment = mvcResult.modelAndView!!.modelMap["assignment"] as Assignment
        val tagNames = assignment.tagsStr
        org.hamcrest.MatcherAssert.assertThat(tagNames, containsInAnyOrder("sample", "test", "simple"))

        // check available tags
        // it should now return 'sample','test','simple'
        globalTags = assignmentTagRepository.findAll()
        assertEquals(3, globalTags.size)
        org.hamcrest.MatcherAssert.assertThat(tagNames, containsInAnyOrder("sample", "test", "simple"))
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `update assignment with tags`() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignmentTags", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                tags = "sample,test,simple"  // <<<<
            )

            // add one tag and remove 2 tags
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", "dummyAssignmentTags")
                    .param("assignmentName", "Dummy Assignment")
                    .param("assignmentPackage", "org.dummy")
                    .param("submissionMethod", "UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                    .param("editMode", "true")
                    .param("assignmentTags", "sample,complex") // <<<<
            )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/info/dummyAssignmentTags"))

            // get assignment detail
            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignmentTags"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignment = mvcResult.modelAndView!!.modelMap["assignment"] as Assignment
            val tagNames = assignment.tagsStr
            assertEquals(2, tagNames?.size)
            org.hamcrest.MatcherAssert.assertThat(tagNames, containsInAnyOrder("sample", "complex"))

            // check available tags
            // it should now return 'sample','complex'
            val globalTags = assignmentTagRepository.findAll().map { it.name }
            assertEquals(4, globalTags.size)
            assertThat(globalTags, Matchers.containsInAnyOrder("sample", "complex", "test", "simple"))

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignmentTags").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignmentTags").deleteRecursively()
            }
        }
    }

    @Test
    fun `list assignments filtered by tags`() {

        val user = User("p1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {// list assigments should return empty
            this.mvc.perform(
                get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create two assignments
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = "p1", activateRightAfterCloning = false, tags = "sample,test"
            )

            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment5", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = "p1", activateRightAfterCloning = false, tags = "other,test"
            )

            // list assignments filtered by tag "sample" should return one assignment
            this.mvc.perform(
                get("/assignment/my?tags=sample")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))

            // list assignments filtered by tag "notexistent" should return zero assignments
            this.mvc.perform(
                get("/assignment/my?tags=notexistent")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(0)))

            // list assignments filtered by tag "test" should return two assignments
            this.mvc.perform(
                get("/assignment/my?tags=test")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(2)))

            // list assignments filtered by tag "sample,other" should return zero assignments
            this.mvc.perform(
                get("/assignment/my?tags=sample,other")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(0)))

        } finally {
            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").deleteRecursively()
            }

            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment5").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment5").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `tag filter preserved after toggle`() {
        val assignment = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj"
        )
        assignmentRepository.save(assignment)

        // toggle with tags parameter - should redirect preserving the tag
        this.mvc.perform(
            post("/assignment/toggle-status/testJavaProj")
                .param("tags", "sample")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my?tags=sample"))
    }
}
