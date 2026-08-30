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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.dropproject.config.DropProjectProperties
import org.springframework.core.io.ResourceLoader
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import org.dropproject.TestsHelper
import org.dropproject.dao.*
import org.dropproject.data.*
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.dropproject.services.AssignmentService
import org.dropproject.services.ZipService
import org.hamcrest.Matchers.*
import java.util.*

/**
 * Shared plumbing for the report-related test classes (split from the former giant
 * ReportControllerTests): common repositories, users and the default assignments.
 */
abstract class ReportTestBase {

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    @Autowired
    lateinit var gitSubmissionRepository: GitSubmissionRepository

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var submissionGitInfoRepository: SubmissionGitInfoRepository

    @Autowired
    lateinit var assignmentTestMethodRepository: AssignmentTestMethodRepository

    @Autowired
    lateinit var authorRepository: AuthorRepository

    @Autowired
    lateinit var projectGroupRepository: ProjectGroupRepository

    @Autowired
    lateinit var testsHelper: TestsHelper

    @Autowired
    lateinit var zipService: ZipService

    @Autowired
    lateinit var assignmentService: AssignmentService

    val defaultAssignmentId = "testJavaProj"

    val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

    val STUDENT_2 = User("student2", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

    val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

    @BeforeEach
    fun setup() {
        var folder = File(dropProjectProperties.mavenizedProjects.rootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
        folder.mkdirs()

        // create initial assignments
        val assignment01 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj"
        )
        assignmentRepository.save(assignment01)

        assignmentTestMethodRepository.save(
            AssignmentTestMethod(
                assignment = assignment01,
                testClass = "TestTeacherProject", testMethod = "testFuncaoParaTestar"
            )
        )
        assignmentTestMethodRepository.save(
            AssignmentTestMethod(
                assignment = assignment01,
                testClass = "TestTeacherProject", testMethod = "testFuncaoLentaParaTestar"
            )
        )
        assignmentTestMethodRepository.save(
            AssignmentTestMethod(
                assignment = assignment01,
                testClass = "TestTeacherHiddenProject", testMethod = "testFuncaoParaTestarQueNaoApareceAosAlunos"
            )
        )

        val assignment02 = Assignment(
            id = "sampleJavaProject", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "sampleJavaProject"
        )
        assignmentRepository.save(assignment02)
    }

    @AfterEach
    fun deleteMavenizedFolder() {
        var folder = File(dropProjectProperties.mavenizedProjects.rootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }

        val submissionsFolder = File(dropProjectProperties.storage.rootLocation)
        if (submissionsFolder.exists()) {
            submissionsFolder.deleteRecursively()
        }
    }
}
