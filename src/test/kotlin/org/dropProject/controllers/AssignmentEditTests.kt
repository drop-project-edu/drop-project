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
import org.dropproject.services.AssignmentValidator
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.*


@DropProjectIntegrationTest
@Tag("integration")
class AssignmentEditTests : AssignmentTestBase() {

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create new assignment and edit`() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment8", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo
            )

            // get edit form
            this.mvc.perform(get("/assignment/edit/dummyAssignment8"))
                .andExpect(status().isOk)
                .andExpect(model().hasNoErrors())
                .andExpect(
                    model().attribute(
                        "assignmentForm",
                        AssignmentForm(
                            assignmentId = "dummyAssignment8",
                            assignmentName = "Dummy Assignment",
                            assignmentPackage = "org.dummy",
                            submissionMethod = SubmissionMethod.UPLOAD,
                            language = Language.JAVA,
                            gitRepositoryUrl = sampleJavaAssignmentRepo,
                            hiddenTestsVisibility = TestVisibility.SHOW_PROGRESS,
                            editMode = true,
                            assignmentTags = "",
                            visibility = AssignmentVisibility.ONLY_BY_LINK
                        )
                    )
                )

            // post a change
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", "dummyAssignment8")
                    .param("assignmentName", "New Name")
                    .param("editMode", "true")
                    .param("submissionMethod", "UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                    .param("leaderboardType", "ELLAPSED")
                    .param("visibility", "PUBLIC")
            )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/info/dummyAssignment8"))

            // get edit form again
            this.mvc.perform(get("/assignment/edit/dummyAssignment8"))
                .andExpect(status().isOk)
                .andExpect(model().hasNoErrors())
                .andExpect(
                    model().attribute(
                        "assignmentForm",
                        AssignmentForm(
                            assignmentId = "dummyAssignment8",
                            assignmentName = "New Name",
                            submissionMethod = SubmissionMethod.UPLOAD,
                            language = Language.JAVA,
                            gitRepositoryUrl = sampleJavaAssignmentRepo,
                            editMode = true,
                            leaderboardType = LeaderboardType.ELLAPSED,
                            assignmentTags = "",
                            visibility = AssignmentVisibility.PUBLIC
                        )
                    )
                )


        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment8").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment8").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `edit assignment changing validation-relevant field marks it inactive`() {

        val assignmentId = "revalidateOnEditTest"
        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, assignmentId)
        try {
            val (assignment, _, _) = testsHelper.createHistoricalAssignment(
                assignmentRepository, dropProjectProperties, assignmentId)
            assertTrue(assignment.active, "assignment should start active")

            // simulate the report that was produced when the assignment was connected to the git repository
            assignmentReportRepository.save(AssignmentReport(assignmentId = assignmentId,
                type = AssignmentValidator.InfoType.INFO, message = "report before the edit", description = null))

            // turn on the coverage calculation, which the assignment's pom.xml is not prepared for
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", assignmentId)
                    .param("assignmentName", assignment.name)
                    .param("assignmentPackage", assignment.packageName)
                    .param("submissionMethod", "GIT")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", assignment.gitRepositoryUrl)
                    .param("editMode", "true")
                    .param("acceptsStudentTests", "true")
                    .param("minStudentTests", "1")
                    .param("calculateStudentTestsCoverage", "true")
            )
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/info/${assignmentId}"))
                .andExpect(flash().attribute("error", containsString("marked inactive")))

            // the new configuration doesn't match the contents of the repository, so the assignment
            // must have been marked inactive
            val updatedAssignment = assignmentRepository.findById(assignmentId).get()
            assertFalse(updatedAssignment.active, "assignment should have been marked inactive")
            assertTrue(updatedAssignment.calculateStudentTestsCoverage, "assignment should have been updated")

            // the previous report was replaced by the result of the new validation
            val reports = assignmentReportRepository.findByAssignmentId(assignmentId)
            assertTrue(reports.none { it.message == "report before the edit" }, "the previous report should have been cleared")
            assertTrue(reports.any { it.type == AssignmentValidator.InfoType.ERROR }, "the new report should contain errors")

        } finally {
            assignmentFolder.deleteRecursively()
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `edit assignment without changing validation-relevant fields doesn't validate again`() {

        val assignmentId = "revalidateOnEditTest2"
        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, assignmentId)
        try {
            val (assignment, _, _) = testsHelper.createHistoricalAssignment(
                assignmentRepository, dropProjectProperties, assignmentId)

            assignmentReportRepository.save(AssignmentReport(assignmentId = assignmentId,
                type = AssignmentValidator.InfoType.INFO, message = "report before the edit", description = null))

            // change only the name, which is not cross-checked against the contents of the repository
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", assignmentId)
                    .param("assignmentName", "New Name")
                    .param("assignmentPackage", assignment.packageName)
                    .param("submissionMethod", "GIT")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", assignment.gitRepositoryUrl)
                    .param("editMode", "true")
            )
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/info/${assignmentId}"))
                .andExpect(flash().attribute("message", "Assignment was successfully updated"))

            val updatedAssignment = assignmentRepository.findById(assignmentId).get()
            assertEquals("New Name", updatedAssignment.name)
            assertTrue(updatedAssignment.active, "assignment should still be active")

            // the assignment wasn't validated again, so the previous report is still there
            val reports = assignmentReportRepository.findByAssignmentId(assignmentId)
            assertEquals(1, reports.size)
            assertEquals("report before the edit", reports[0].message)

        } finally {
            assignmentFolder.deleteRecursively()
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `edit assignment changing evaluation-relevant field warns about existing submissions`() {

        val assignmentId = "rebuildWarningTest"
        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, assignmentId)
        try {
            val (assignment, _, _) = testsHelper.createHistoricalAssignment(
                assignmentRepository, dropProjectProperties, assignmentId)

            // a submission that was evaluated with the current configuration
            val group = ProjectGroup()
            projectGroupRepository.save(group)
            val submission = Submission(submissionDate = Date(), submitterUserId = "student1",
                status = SubmissionStatus.VALIDATED.code, statusDate = Date(),
                assignmentId = assignmentId, assignmentGitHash = null)
            submission.group = group
            submissionRepository.save(submission)

            // start requiring student tests, which changes the way the submissions are evaluated
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", assignmentId)
                    .param("assignmentName", assignment.name)
                    .param("assignmentPackage", assignment.packageName)
                    .param("submissionMethod", "GIT")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", assignment.gitRepositoryUrl)
                    .param("editMode", "true")
                    .param("acceptsStudentTests", "true")
                    .param("minStudentTests", "2")
            )
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/info/${assignmentId}"))
                .andExpect(flash().attribute("warning", containsString("1 submission(s) were evaluated")))

            val updatedAssignment = assignmentRepository.findById(assignmentId).get()
            assertEquals(2, updatedAssignment.minStudentTests)

        } finally {
            assignmentFolder.deleteRecursively()
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `edit assignment changing evaluation-relevant field without submissions doesn't warn`() {

        val assignmentId = "rebuildWarningTest2"
        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, assignmentId)
        try {
            val (assignment, _, _) = testsHelper.createHistoricalAssignment(
                assignmentRepository, dropProjectProperties, assignmentId)

            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", assignmentId)
                    .param("assignmentName", assignment.name)
                    .param("assignmentPackage", assignment.packageName)
                    .param("submissionMethod", "GIT")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", assignment.gitRepositoryUrl)
                    .param("editMode", "true")
                    .param("acceptsStudentTests", "true")
                    .param("minStudentTests", "2")
            )
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/info/${assignmentId}"))
                .andExpect(flash().attribute("message", "Assignment was successfully updated"))
                .andExpect(flash().attributeCount(1))  // there is nothing to rebuild, so no warning

        } finally {
            assignmentFolder.deleteRecursively()
        }
    }
}
