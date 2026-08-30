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
import org.dropproject.dao.*
import org.dropproject.data.SubmissionInfo
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.hamcrest.CoreMatchers.*
import org.hamcrest.Matchers.hasProperty
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class UploadSubmissionLifecycleTests : UploadTestBase() {

    @Test
    fun `upload project goes into right folder`() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1)

        val submissionDB = submissionRepository.findById(submissionId.toLong()).get()
        val submissionFolder = File("${dropProjectProperties.storage.rootLocation}/upload", submissionDB.submissionFolder)

        assertTrue(submissionFolder.exists(), "submission folder doesn't exist")

    }

    @Test
    fun `multiple submissions increments counter`() {

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("numSubmissions", 0L))

        testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)

        this.mvc.perform(get("/upload/testJavaProj").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("numSubmissions", 1L))

    }

    @Test
    fun `mark as final`() {

        testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
        testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors", "testJavaProj", STUDENT_1)

        // mark second submission as final
        this.mvc.perform(post("/markAsFinal/2")
                .with(user(TEACHER_1)))
                .andExpect(redirectedUrl("/buildReport/2"))
                .andExpect(status().isFound)

        // check if it was not marked as final
        this.mvc.perform(get("/buildReport/1").with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(model().attribute<Submission>("submission", hasProperty("markedAsFinal", equalTo(false))))

        // check if it was marked as final
        this.mvc.perform(get("/buildReport/2").with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(model().attribute<Submission>("submission", hasProperty("markedAsFinal", equalTo(true))))

        // now mark first submission as final (it should unmark the second submission
        this.mvc.perform(post("/markAsFinal/1")
            .with(user(TEACHER_1)))
            .andExpect(redirectedUrl("/buildReport/1"))
            .andExpect(status().isFound)

        // check if it was marked as final
        this.mvc.perform(get("/buildReport/1").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute<Submission>("submission", hasProperty("markedAsFinal", equalTo(true))))

        // check if it was not marked as final
        this.mvc.perform(get("/buildReport/2").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(model().attribute<Submission>("submission", hasProperty("markedAsFinal", equalTo(false))))

    }

    @Test
    fun `cleanup submissions`() {

        testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
        testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors", "testJavaProj", STUDENT_1)

        // mark second submission as final
        this.mvc.perform(post("/markAsFinal/2")
                .with(user(TEACHER_1)))
                .andExpect(redirectedUrl("/buildReport/2"))
                .andExpect(status().isFound())

        var mavenizedProjectsFolder = File(dropProjectProperties.mavenizedProjects.rootLocation,
                                            Submission.relativeUploadFolder("testJavaProj", Date()))
        assertEquals(2, mavenizedProjectsFolder.list().size)

        val admin = User("admin", "", mutableListOf(SimpleGrantedAuthority("ROLE_DROP_PROJECT_ADMIN")))

        // cleanup all non-final - should delete the mavenized folder of submission 1
        this.mvc.perform(post("/admin/cleanup/testJavaProj")
                .with(user(admin)))
                .andExpect(redirectedUrl("/report/testJavaProj"))
                .andExpect(status().isFound())

        assertEquals(1, mavenizedProjectsFolder.list().size)

        val submissionThatSurvivedCleanup = submissionRepository.findById(2).get()

        assertEquals("${submissionThatSurvivedCleanup.submissionId}-mavenized", mavenizedProjectsFolder.list()[0])
    }

    @Test
    fun `cleanup doesn't remove files of groups without a final submission`() {

        testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
        testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors", "testJavaProj", STUDENT_1)

        val mavenizedProjectsFolder = File(dropProjectProperties.mavenizedProjects.rootLocation,
                                            Submission.relativeUploadFolder("testJavaProj", Date()))
        assertEquals(2, mavenizedProjectsFolder.list().size)

        val admin = User("admin", "", mutableListOf(SimpleGrantedAuthority("ROLE_DROP_PROJECT_ADMIN")))

        // no submission was marked as final, so the group must keep both submissions
        this.mvc.perform(post("/admin/cleanup/testJavaProj")
                .with(user(admin)))
                .andExpect(redirectedUrl("/report/testJavaProj"))
                .andExpect(flash().attribute("message", "There were no non-final submission files to remove"))

        assertEquals(2, mavenizedProjectsFolder.list().size)

        // and the button must be enabled, since this is an upload assignment
        this.mvc.perform(get("/report/testJavaProj").with(user(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("Remove the files of the non-final submissions")))
    }

    @Test
    fun `cleanup is not available for git assignments`() {

        assignmentRepository.save(Assignment(id = "testGitProj", name = "Test Git Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.GIT, active = true, gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = "testGitProj", gitCurrentHash = "somehash"))

        val admin = User("admin", "", mutableListOf(SimpleGrantedAuthority("ROLE_DROP_PROJECT_ADMIN")))

        this.mvc.perform(post("/admin/cleanup/testGitProj")
                .with(user(admin)))
                .andExpect(redirectedUrl("/report/testGitProj"))
                .andExpect(flash().attributeExists("error"))

        this.mvc.perform(get("/report/testGitProj").with(user(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("Not available for git assignments")))
    }

    @Test
    fun `upload project with errors, then update assignment, then rebuild full`() {

        val testFile = File("${dropProjectProperties.assignments.rootLocation}/testJavaProj/src/test/java/org/dropProject/sampleAssignments/testProj/TestTeacherProject.java")
        val backupTestFile = testFile.copyTo(
                File("${dropProjectProperties.assignments.rootLocation}/testJavaProj/src/test/java/org/dropProject/sampleAssignments/testProj/TestTeacherProject.java.backup"),
                overwrite = true)

        val uploader = User("a21702482", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

        try {

            // change assignment so that it has an error
            run {
                var testFileContent = testFile.readText()
                testFileContent = testFileContent.replace("assertEquals(3, Main.funcaoParaTestar());",
                        "assertEquals(4, Main.funcaoParaTestar());")
                testFile.writeText(testFileContent)
            }

            // submit assignment and check errors
            run {
                val submissionId = testsHelper.uploadProject(this.mvc, "projectOtherEncoding", "testJavaProj", uploader)

                val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(uploader)))
                        .andExpect(status().isOk())
                        .andReturn()

                @Suppress("UNCHECKED_CAST")
                val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
                assertEquals(5, summary.size, "Summary should be 5 lines")
                assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
                assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
                assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
                assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
                assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
                assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
                assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be NOK (key)")
                assertEquals("NOK", summary[3].reportValue, "junit should be NOK (value)")
            }

            // change assignment to fix the error
            run {
                var testFileContent = testFile.readText()
                testFileContent = testFileContent.replace("assertEquals(4, Main.funcaoParaTestar());",
                        "assertEquals(3, Main.funcaoParaTestar());")
                testFile.writeText(testFileContent)
            }

            // rebuild full
            run {
                this.mvc.perform(post("/rebuildFull/1")
                        .with(user(TEACHER_1)))
                        .andExpect(status().isFound())
                        .andExpect(header().string("Location", "/buildReport/2"))
            }

            // check that are no longer errors
            run {
                val reportResult = this.mvc.perform(get("/buildReport/2").with(user(uploader)))
                        .andExpect(status().isOk())
                        .andReturn()

                @Suppress("UNCHECKED_CAST")
                val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
                assertEquals(5, summary.size, "Summary should be 5 lines")
                assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
                assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
                assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
                assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
                assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
                assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
                assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be OK (key)")
                assertEquals("OK", summary[3].reportValue, "junit should be OK (value)")

                val submission = reportResult.modelAndView!!.modelMap["submission"] as Submission
                assertEquals(SubmissionStatus.VALIDATED_REBUILT, submission.getStatus())
            }


        } finally {
            backupTestFile.copyTo(testFile, overwrite = true)
            backupTestFile.delete()
        }
    }

    @Test
    fun `rebuild submission`() {
        val submissionId = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)

        this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk)

        val submission = submissionRepository.findById(submissionId.toLong()).get()
        assertEquals(SubmissionStatus.VALIDATED, submission.getStatus())

        // sanity check: a normal (non-rebuild) submission never gets a RebuildStatus tracking row
        assertNull(rebuildStatusRepository.findBySubmissionId(submissionId.toLong()))

        this.mvc.perform(post("/rebuild/$submissionId")
                .with(user(TEACHER_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/buildReport/$submissionId"))

        val updatedSubmission = submissionRepository.findById(submissionId.toLong()).get()
        assertEquals(SubmissionStatus.VALIDATED_REBUILT, updatedSubmission.getStatus())

        // the RebuildStatus tracking row created when the rebuild started must be cleaned up once it finishes
        // (in the "test" profile, checkProject runs synchronously, so by the time the request above returns,
        // the rebuild has already fully completed and its tracking row should be gone)
        assertNull(rebuildStatusRepository.findBySubmissionId(submissionId.toLong()))
    }

    @Test
    fun `abort rebuild`() {
        val submissionId = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)

        // simulate a rebuild that got stuck: force the submission into REBUILDING and give it a tracking row,
        // as UploadController.rebuild() would have done when it started
        val submission = submissionRepository.findById(submissionId.toLong()).get()
        submission.setStatus(SubmissionStatus.REBUILDING, dontUpdateStatusDate = true)
        submissionRepository.save(submission)
        rebuildStatusRepository.save(RebuildStatus(submission = submission))

        this.mvc.perform(post("/abortRebuild/$submissionId")
                .with(user(TEACHER_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/buildReport/$submissionId"))

        val abortedSubmission = submissionRepository.findById(submissionId.toLong()).get()
        assertEquals(SubmissionStatus.ABORTED_BY_TIMEOUT, abortedSubmission.getStatus())
        assertNull(rebuildStatusRepository.findBySubmissionId(submissionId.toLong()))

        // aborting a submission that has already reached a terminal, non-aborted status must be a no-op (guards
        // against a stale "Abort" click on the build report page clobbering a result that has since completed)
        val otherSubmissionId = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_2)
        assertEquals(SubmissionStatus.VALIDATED, submissionRepository.findById(otherSubmissionId.toLong()).get().getStatus())

        this.mvc.perform(post("/abortRebuild/$otherSubmissionId")
                .with(user(TEACHER_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/buildReport/$otherSubmissionId"))

        val unchangedSubmission = submissionRepository.findById(otherSubmissionId.toLong()).get()
        assertEquals(SubmissionStatus.VALIDATED, unchangedSubmission.getStatus())
    }

    @Test
    fun `upload and delete one submission`() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)

        this.mvc.perform(post("/delete/$submissionId")
                .with(user(TEACHER_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/report/testJavaProj"))

        val reportResult = this.mvc.perform(get("/report/testJavaProj")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val report = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>
        assertTrue(report.isEmpty(), "report should be empty")

        this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isForbidden())
    }

    @Test
    fun `upload multiple and delete just one submission`() {

        val submissionId1 = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
        val submissionId2 = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)

        this.mvc.perform(post("/delete/$submissionId1")
                .with(user(TEACHER_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/report/testJavaProj"))

        val reportResult = this.mvc.perform(get("/report/testJavaProj")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val report = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>
        assertEquals(1, report.size)
        assertEquals(1,report[0].allSubmissions.size)
        assertEquals(submissionId2.toLong(),report[0].allSubmissions[0].id)
    }
}
