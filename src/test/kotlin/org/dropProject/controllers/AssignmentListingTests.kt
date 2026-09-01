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
import org.dropproject.dao.*
import org.dropproject.data.SubmissionInfo
import org.dropproject.Constants.CACHE_ARCHIVED_ASSIGNMENTS_KEY
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
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
class AssignmentListingTests : AssignmentTestBase() {

    @Test
    fun `list assignments`() {

        val user = User("p1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {// list assigments should return empty
            this.mvc.perform(
                get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create assignment
            assignmentFixtures.createAndSetupAssignment("dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = "p1", activateRightAfterCloning = false
            )

            // list assignments should return one assignment
            val mvcResult = this.mvc.perform(
                get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView!!.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            assertEquals("dummyAssignment4", assignment.id)
            assertEquals("Dummy Assignment", assignment.name)
            assertEquals("org.dummy", assignment.packageName)
            assertEquals(SubmissionMethod.UPLOAD, assignment.submissionMethod)
            assertEquals(sampleJavaAssignmentRepo, assignment.gitRepositoryUrl)
            assertEquals("p1", assignment.ownerUserId)
            assertEquals(false, assignment.active)
            assertEquals(0, assignment.numSubmissions)
            assertEquals(0, assignment.numUniqueSubmitters)
            assertNull(assignment.lastSubmissionDate)
            assertEquals(AssignmentVisibility.ONLY_BY_LINK, assignment.visibility)

        } finally {
            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `show only active assignments`() {

        try {
            // create an assignment with white-list. it will start as inactive
            assignmentFixtures.createAndSetupAssignment("dummyAssignment6", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                assignees = "21800000"
            )

            // login as 21800000 and get an empty list of assignments
            val student = User("21800000", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
            this.mvc.perform(
                get("/")
                    .with(user(student))
            )
                .andExpect(status().isOk)
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(0)))

            // mark the assignment as active
            this.mvc.perform(get("/assignment/toggle-status/dummyAssignment6"))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/my"))
                .andExpect(flash().attribute("message", "Assignment was marked active"))

            // login again as 21800000 and get a redirect to the assignment
            this.mvc.perform(
                get("/")
                    .with(user(student))
            )
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/upload/dummyAssignment6"))

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment6").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment6").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `check assignment has no errors`() {

        // create initial assignment
        val assignment01 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = false, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj", hiddenTestsVisibility = TestVisibility.HIDE_EVERYTHING
        )
        assignmentRepository.save(assignment01)

        // toggle status
        this.mvc.perform(get("/assignment/toggle-status/testJavaProj"))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message", "Assignment was marked active"))

        // confirm it is now active
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assertTrue(assignment.active, "assignment is not active")
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `get assignment info`() {

        // create initial assignment
        val assignment = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = false, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj"
        )
        assignmentRepository.save(assignment)
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))

        this.mvc.perform(get("/assignment/info/testJavaProj"))
            .andExpect(status().isOk)
            .andExpect(view().name("assignment-detail"))
            .andExpect(model().hasNoErrors())
            .andExpect(model().attribute("assignment", assignment))
            .andExpect(content().string(containsString(assignment.id)))

    }

    @Test
    fun `list archived assignments`() {

        val user = User("p1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {// list archived assignments should return empty
            this.mvc.perform(
                get("/assignment/archived")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create assignment
            assignmentFixtures.createAndSetupAssignment("dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = "p1", activateRightAfterCloning = false
            )

            // list archived assignments should still return empty
            this.mvc.perform(
                get("/assignment/archived")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // archive assignment
            this.mvc.perform(
                post("/assignment/archive/dummyAssignment4")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/my"))
                .andExpect(
                    flash().attribute(
                        "message",
                        "Assignment was archived. You can now find it in the Archived assignments page"
                    )
                )

            // list archived assignments should now return 1 assignment
            val mvcResult = this.mvc.perform(
                get("/assignment/archived")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView!!.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            assertEquals("dummyAssignment4", assignment.id)


        } finally {
            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").deleteRecursively()
            }
        }
    }

    @Test
    fun `archived assignments are cached`() {

        val user = User("cacheTester", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {
            assignmentRepository.save(
                Assignment(
                    id = "archivedProjToCache", name = "Archived Project", packageName = "org.dummy",
                    ownerUserId = "cacheTester", submissionMethod = SubmissionMethod.UPLOAD, active = false,
                    archived = true, gitRepositoryUrl = "git://dummyRepo", gitRepositoryFolder = "archivedProjToCache"
                )
            )

            cacheManager.getCache(CACHE_ARCHIVED_ASSIGNMENTS_KEY)?.clear()

            this.mvc.perform(
                get("/assignment/archived")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))

            // the assignments are not serializable, so the cache must keep them in the heap. Otherwise, it
            // silently fails to store them (and logs a NotSerializableException on every request)
            assertNotNull(cacheManager.getCache(CACHE_ARCHIVED_ASSIGNMENTS_KEY)?.get("cacheTester"), "the archived assignments were not cached")

        } finally {
            assignmentRepository.deleteById("archivedProjToCache")
            cacheManager.getCache(CACHE_ARCHIVED_ASSIGNMENTS_KEY)?.clear()
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create new assignment and info with test methods`() {

        try {
            assignmentFixtures.createAndSetupAssignment("dummyAssignmentTests", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo
            )

            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignmentTests"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val testMethods = mvcResult.modelAndView!!.modelMap["tests"] as List<AssignmentTestMethod>
            assertEquals(4, testMethods.size)
            assertThat(
                testMethods.map { it.testMethod },
                contains(
                    "test_001_FindMax",
                    "test_002_FindMaxAllNegative",
                    "test_003_FindMaxNegativeAndPositive",
                    "test_004_FindMaxWithNull"
                )
            )

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignmentTests").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignmentTests").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create new assignment and info with test methods with junit5`() {

        try {
            assignmentFixtures.createAndSetupAssignment("dummyAssignmentTests", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentWithJUnit5Repo
            )

            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignmentTests"))
                .andExpect(status().isOk)
                .andReturn()


            @Suppress("UNCHECKED_CAST")
            val report = mvcResult.modelAndView!!.modelMap["report"] as List<AssignmentReport>
            assertEquals(6, report.size)
            assertEquals("Assignment has a pom.xml", report[0].message)
            assertEquals("Doesn't use the 'dropProject.currentUserId' system property", report[1].message)
            assertEquals("POM file is prepared to prevent stacktrace trimming on junit errors", report[2].message)
            assertEquals("Found 1 test classes", report[3].message)
            assertEquals("You have defined a global timeout for the test methods.", report[4].message)
            assertEquals("You are using a recent version of checkstyle.", report[5].message)

            @Suppress("UNCHECKED_CAST")
            val testMethods = mvcResult.modelAndView!!.modelMap["tests"] as List<AssignmentTestMethod>
            assertEquals(4, testMethods.size)
            assertThat(
                testMethods.map { it.testMethod },
                contains(
                    "test_001_FindMax",
                    "test_002_FindMaxAllNegative",
                    "test_003_FindMaxNegativeAndPositive",
                    "test_004_FindMaxWithNull"
                )
            )

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignmentTests").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignmentTests").deleteRecursively()
            }
        }
    }

    @Test
    fun `list assignments after some submissions`() {

        try {

            // create assignment
            assignmentFixtures.createAndSetupAssignment("dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = TEACHER_1.username, activateRightAfterCloning = true
            )

            submissionFixtures.uploadProject("projectCompilationErrors", "dummyAssignment4", STUDENT_1)
            val lastSubmissionId =
                submissionFixtures.uploadProject("projectCheckstyleErrors", "dummyAssignment4", STUDENT_1)

            // list assignments should return one assignment
            val mvcResult = this.mvc.perform(
                get("/assignment/my")
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView!!.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            assertEquals("dummyAssignment4", assignment.id)
            assertEquals("Dummy Assignment", assignment.name)
            assertEquals("org.dummy", assignment.packageName)
            assertEquals(SubmissionMethod.UPLOAD, assignment.submissionMethod)
            assertEquals(sampleJavaAssignmentRepo, assignment.gitRepositoryUrl)
            assertEquals(TEACHER_1.username, assignment.ownerUserId)
            assertEquals(true, assignment.active)
            assertEquals(2, assignment.numSubmissions)
            assertEquals(1, assignment.numUniqueSubmitters)
            assertEquals(
                submissionRepository.getReferenceById(lastSubmissionId.toLong()).submissionDate,
                assignment.lastSubmissionDate
            )
            assertEquals(AssignmentVisibility.ONLY_BY_LINK, assignment.visibility)

        } finally {
            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").deleteRecursively()
            }
        }
    }

    @Test
    fun `mark all as final`() {

        val assignmentId = submissionFixtures.defaultAssignmentId

        // create assignment
        val assignment01 = Assignment(
            id = assignmentId, name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = TEACHER_1.username,
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj"
        )
        assignmentRepository.save(assignment01)

        // make several submissions for that assignment
        submissionFixtures.makeSeveralSubmissions(listOf(
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectInvalidStructure1"
            ))

        // mark all as final
        this.mvc.perform(
            post("/assignment/markAllAsFinal/${assignmentId}")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/report/${assignmentId}"))

        // check results
        val reportResult = this.mvc.perform(
            get("/report/testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val report = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>
        report.forEach {
            assertTrue(it.lastSubmission.markedAsFinal)
        }

    }
}
