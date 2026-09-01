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
import org.dropproject.forms.AssignmentForm
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
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
import org.springframework.validation.BindingResult
import java.io.File
import java.util.*


@DropProjectIntegrationTest
@Tag("integration")
class AssignmentCreationTests : AssignmentTestBase() {

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `get new assignment form`() {
        this.mvc.perform(get("/assignment/new"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create invalid assignment`() {

        mvc.perform(post("/assignment/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "assignmentId"))


        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("acceptsStudentTests", "true")  // <<<<<
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "acceptsStudentTests"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("minStudentTests", "1")  // <<<<<
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "acceptsStudentTests"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("calculateStudentTestsCoverage", "true") // <<<<<
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "acceptsStudentTests"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("acceptsStudentTests", "true")
                .param("calculateStudentTestsCoverage", "true")
                .param("minStudentTests", "1")
                .param("acl", "teacher1,teacher2")  // <<<<< acl should not include the session owner (teacher1)
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "acl"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("minGroupSize", "-1")
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "minGroupSize"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("maxGroupSize", "2")  // <<< minGroupSize is missing
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "minGroupSize"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("exceptions", "user1,user2")  // <<< minGroupSize is missing
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "exceptions"))


        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                .param("acceptsStudentTests", "true")    // <<<<
                .param("calculateStudentTestsCoverage", "true")  // <<<<
                .param("minStudentTests", "1")   // <<<<
        )
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/assignment/setup-git/assignmentId"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                .param("visibility", "PRIVATE")    // <<<< assignees is missing
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "assignees"))

    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create assignment with invalid git repository`() {

        val mvcResult = this.mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "dummyAssignment3")
                .param("assignmentName", "Dummy Assignment")
                .param("assignmentPackage", "org.dummy")
                .param("submissionMethod", "UPLOAD")
                .param("language", "JAVA")
                .param("gitRepositoryUrl", "git@githuu.com:someuser/cs1Assigment1.git")
        )
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "gitRepositoryUrl"))
            .andReturn()

        val result =
            mvcResult.modelAndView!!.model.get(BindingResult.MODEL_KEY_PREFIX + "assignmentForm") as BindingResult
        assertEquals(
            "Error cloning git repository. Are you sure the url is right?",
            result.getFieldError("gitRepositoryUrl")?.defaultMessage
        )

        try {
            assignmentRepository.findById("dummyAssignment3").get()
            fail<Unit>("dummyAssignment shouldn't exist in the database")
        } catch (e: Exception) {
        }

    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create assignment clears leftover folder from previously deleted assignment`() {

        // simulate a leftover folder from a previously deleted assignment with the same id (issue #111): e.g.
        // left behind because the OS hadn't yet released file handles JGit held open when it was deleted
        val leftoverFolder = File(dropProjectProperties.assignments.rootLocation, "assignmentId")
        try {
            leftoverFolder.mkdirs()
            File(leftoverFolder, "leftover.txt").writeText("leftover from a previously deleted assignment")

            // creating a new assignment with the same id must behave exactly as if the folder didn't exist at
            // all - i.e. it should just redirect to setup-git (this repo isn't connected yet), not fail with
            // some "destination already exists" error
            this.mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", "assignmentId")
                    .param("assignmentName", "assignmentName")
                    .param("assignmentPackage", "assignmentPackage")
                    .param("language", "JAVA")
                    .param("submissionMethod", "UPLOAD")
                    .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
            )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/setup-git/assignmentId"))

            assertTrue(assignmentRepository.existsById("assignmentId"))
            assertFalse(File(leftoverFolder, "leftover.txt").exists(), "the leftover folder should have been cleared before cloning")

        } finally {
            leftoverFolder.deleteRecursively()
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create assignment rejects git repository folder already used by another assignment`() {

        // an assignment (e.g. previously imported) whose gitRepositoryFolder doesn't match its own id
        val existingAssignment = Assignment(
            id = "existingAssignmentId", name = "Existing Assignment", packageName = "org.dummy",
            ownerUserId = "teacher1", submissionMethod = SubmissionMethod.UPLOAD, active = true,
            gitRepositoryUrl = "git://dummy", gitRepositoryFolder = "sharedFolder"
        )
        assignmentRepository.save(existingAssignment)

        val mvcResult = this.mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "sharedFolder")
                .param("assignmentName", "New Assignment")
                .param("assignmentPackage", "org.dummy")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "assignmentId"))
            .andReturn()

        val result =
            mvcResult.modelAndView!!.model.get(BindingResult.MODEL_KEY_PREFIX + "assignmentForm") as BindingResult
        assertEquals(
            "Error: There is already an assignment using this git repository folder",
            result.getFieldError("assignmentId")?.defaultMessage
        )

        // the existing assignment must be untouched, and no new one should have been created
        assertTrue(assignmentRepository.existsById("existingAssignmentId"))
        assertFalse(assignmentRepository.existsById("sharedFolder"))
    }

    @Test
    @WithMockUser(username = "teacher1", roles = ["TEACHER"])
    fun `create assignment with other teachers`() {

        try {

            assignmentFixtures.createAndSetupAssignment("dummyAssignment7", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                acl = "p1000, p1001"
            )

            // get assignment detail
            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignment7"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val aclList = mvcResult.modelAndView!!.modelMap["acl"] as List<AssignmentACL>
            assertEquals(2, aclList.size)
            assertEquals("p1000", aclList[0].userId)
            assertEquals("p1001", aclList[1].userId)

            // accessing "/assignments/my" with p1000 should give one assignment
            val user = User("p1000", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))
            this.mvc.perform(
                get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk)
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment7").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment7").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create assignment with acl containing spaces`() {
        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                .param("acl", "teacher2 teacher3")  // space instead of comma
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "acl"))
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create assignment with acl containing semicolons`() {
        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                .param("acl", "teacher2;teacher3")  // semicolon instead of comma
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "acl"))
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `assignment id with backslash is rejected`() {
        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "test\\assignment")
                .param("assignmentName", "Test Assignment")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
        )
            .andExpect(status().isOk)
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "assignmentId"))
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create assignment with project group restrictions`() {

        try {

            assignmentFixtures.createAndSetupAssignment("dummyAssignment7", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                minGroupSize = "2",
                maxGroupSize = "2",
                exceptions = "student3,\n   student4"
            )

            // get assignment detail
            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignment7"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignment = mvcResult.modelAndView!!.modelMap["assignment"] as Assignment
            assertNotNull(assignment.projectGroupRestrictions)
            assertEquals(2, assignment.projectGroupRestrictions?.minGroupSize)
            assertEquals(2, assignment.projectGroupRestrictions?.maxGroupSize)
            assertEquals("student3,student4", assignment.projectGroupRestrictions?.exceptions)

            // get edit form
            this.mvc.perform(get("/assignment/edit/dummyAssignment7"))
                .andExpect(status().isOk)
                .andExpect(model().hasNoErrors())
                .andExpect(
                    model().attribute(
                        "assignmentForm",
                        AssignmentForm(
                            assignmentId = "dummyAssignment7",
                            assignmentName = "Dummy Assignment",
                            assignmentPackage = "org.dummy",
                            submissionMethod = SubmissionMethod.UPLOAD,
                            language = Language.JAVA,
                            gitRepositoryUrl = sampleJavaAssignmentRepo,
                            hiddenTestsVisibility = TestVisibility.SHOW_PROGRESS,
                            editMode = true,
                            assignmentTags = "",
                            minGroupSize = 2,
                            maxGroupSize = 2,
                            exceptions = "student3,\nstudent4",
                        )
                    )
                )

            // post a change
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", "dummyAssignment7")
                    .param("assignmentName", "New Name")
                    .param("editMode", "true")
                    .param("submissionMethod", "UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                    .param("minGroupSize", "1")
                    .param("maxGroupSize", "2")
                    .param("exceptions", "student3,student4,student5")
            )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/info/dummyAssignment7"))

            // get assignment detail
            val mvcResult2 = this.mvc.perform(get("/assignment/info/dummyAssignment7"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignment2 = mvcResult2.modelAndView!!.modelMap["assignment"] as Assignment
            assertNotNull(assignment2.projectGroupRestrictions)
            assertEquals(1, assignment2.projectGroupRestrictions?.minGroupSize)
            assertEquals(2, assignment2.projectGroupRestrictions?.maxGroupSize)
            assertEquals("student3,student4,student5", assignment2.projectGroupRestrictions?.exceptions)

            // post another change
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", "dummyAssignment7")
                    .param("assignmentName", "New Name")
                    .param("editMode", "true")
                    .param("submissionMethod", "UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                // minGroupSize and the others no longer exist
            )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/info/dummyAssignment7"))

            // get assignment detail
            val mvcResult3 = this.mvc.perform(get("/assignment/info/dummyAssignment7"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignment3 = mvcResult3.modelAndView!!.modelMap["assignment"] as Assignment
            assertNull(assignment3.projectGroupRestrictions)

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment7").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment7").deleteRecursively()
            }
        }
    }
}
