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
package org.dropproject

import org.apache.commons.io.FileUtils
import org.dropproject.config.DropProjectProperties
import org.dropproject.dao.Assignment
import org.dropproject.dao.AssignmentTestMethod
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.AssignmentTestMethodRepository
import org.eclipse.jgit.api.Git
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.stereotype.Service
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.io.File

/**
 * Test fixtures for creating assignments, either directly in the database or through the
 * teacher-facing web flow.
 */
@Service
class AssignmentFixtures {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var assignmentTestMethodRepository: AssignmentTestMethodRepository

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    /**
     * Saves the default assignment ("testJavaProj", matching the sample assignment folder of the same
     * name) directly in the database. With [withTestMethods], also registers its teacher test methods.
     */
    fun createDefaultAssignment(id: String = "testJavaProj",
                                name: String = "Test Project (for automatic tests)",
                                packageName: String = "org.dropProject.sampleAssignments.testProj",
                                gitRepositoryUrl: String = "git://dummy",
                                gitCurrentHash: String? = null,
                                withTestMethods: Boolean = false): Assignment {
        val assignment = Assignment(id = id, name = name,
            packageName = packageName, ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = gitRepositoryUrl,
            gitRepositoryFolder = id, gitCurrentHash = gitCurrentHash)
        assignmentRepository.save(assignment)

        if (withTestMethods) {
            assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = "TestTeacherProject", testMethod = "testFuncaoParaTestar"))
            assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = "TestTeacherProject", testMethod = "testFuncaoLentaParaTestar"))
            assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = "TestTeacherHiddenProject", testMethod = "testFuncaoParaTestarQueNaoApareceAosAlunos"))
        }

        return assignment
    }

    fun createAndSetupAssignment(assignmentId: String, assignmentName: String,
                                 assignmentPackage: String, submissionMethod: String,
                                 repositoryUrl: String, privateKey: String = TestKeys.sampleJavaAssignmentPrivateKey,
                                 publicKey: String = TestKeys.sampleJavaAssignmentPublicKey,
                                 assignees: String? = null, acl: String? = null,
                                 teacherId: String = "teacher1", language: String = "JAVA",
                                 activateRightAfterCloning: Boolean = false,
                                 hiddenTestsVisibility: String = "SHOW_PROGRESS",
                                 tags: String? = null,
                                 dueDate: String? = null,
                                 minGroupSize: String? = null,
                                 maxGroupSize: String? = null,
                                 exceptions: String? = null,
                                 visibility: String = "ONLY_BY_LINK"
                                 ): Assignment {

        val user = User(teacherId, "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        // post form
        mvc.perform(MockMvcRequestBuilders.post("/assignment/new")
                .with(SecurityMockMvcRequestPostProcessors.user(user))
                .param("assignmentId", assignmentId)
                .param("assignmentName", assignmentName)
                .param("assignmentPackage", assignmentPackage)
                .param("language", language)
                .param("submissionMethod", submissionMethod)
                .param("gitRepositoryUrl", repositoryUrl)
                .param("assignees", assignees)
                .param("acl", acl)
                .param("hiddenTestsVisibility", hiddenTestsVisibility)
                .param("assignmentTags", tags)
                .param("dueDate", dueDate)
                .param("minGroupSize", minGroupSize)
                .param("maxGroupSize", maxGroupSize)
                .param("exceptions", exceptions)
                .param("visibility", visibility)
        )
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/assignment/setup-git/${assignmentId}"))

        // get assignment detail
        mvc.perform(MockMvcRequestBuilders.get("/assignment/setup-git/${assignmentId}")
                .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isOk)

        // inject private and public key to continue
        var assignment = assignmentRepository.findById(assignmentId).get()
        assignment.gitRepositoryPrivKey = privateKey
        assignment.gitRepositoryPubKey = publicKey
        if (activateRightAfterCloning) {
            assignment.active = true
        }
        assignmentRepository.save(assignment)

        // connect to git repository
        mvc.perform(MockMvcRequestBuilders.post("/assignment/setup-git/${assignmentId}")
                .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/assignment/info/${assignmentId}"))
                .andExpect(MockMvcResultMatchers.flash().attribute("message", "Assignment was successfully created and connected to git repository"))

        // refresh assignment
        assignment = assignmentRepository.findById(assignmentId).get()

        return assignment
    }

    // copies the sampleJavaProject fixture into a new folder under the assignments root location and turns
    // it into a real local git repo with two commits, the second one changing a teacher test file
    // (marked with "MARKER-NEW"). Returns the saved Assignment together with both commit hashes.
    fun createHistoricalAssignment(newAssignmentId: String = "historicalTeacherFilesTest"): Triple<Assignment, String, String> {
        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, newAssignmentId)
        FileUtils.copyDirectory(
            File(dropProjectProperties.assignments.rootLocation, "sampleJavaProject"),
            assignmentFolder
        )

        val git = Git.init().setDirectory(assignmentFolder).call()
        git.add().addFilepattern(".").call()
        val commitOld = git.commit().setMessage("initial teacher files").call()

        val teacherTestFile = File(
            assignmentFolder,
            "src/test/java/org/dropProject/samples/sampleJavaAssignment/TestTeacherProject.java"
        )
        teacherTestFile.writeText(
            teacherTestFile.readText()
                .replace("public class TestTeacherProject", "// MARKER-NEW\npublic class TestTeacherProject")
        )
        git.add().addFilepattern(".").call()
        val commitNew = git.commit().setMessage("teacher updated the tests").call()
        git.close()

        val assignment = Assignment(
            id = newAssignmentId, name = "Historical Teacher Files Test",
            packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.GIT, active = true,
            gitRepositoryUrl = "git@github.com:teacher1/$newAssignmentId.git",
            gitRepositoryFolder = newAssignmentId,
            gitCurrentHash = commitNew.name
        )
        assignmentRepository.save(assignment)

        return Triple(assignment, commitOld.name, commitNew.name)
    }
}
