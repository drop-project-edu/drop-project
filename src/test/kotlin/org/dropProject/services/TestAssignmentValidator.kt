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
package org.dropProject.services

import org.dropProject.dao.Assignment
import org.dropProject.forms.SubmissionMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.junit4.SpringRunner


@RunWith(SpringRunner::class)
class TestAssignmentValidator {

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    lateinit var assignmentValidator: AssignmentValidator

    val sampleAssignmentsRootFolder = "src/test/sampleAssignments"

    val dummyAssignment = Assignment(id = "dummy", name = "", gitRepositoryUrl = "",
            gitRepositoryFolder = "", ownerUserId = "p4997", submissionMethod = SubmissionMethod.UPLOAD)

    @Before
    fun initAssignmentValidator() {
        assignmentValidator = AssignmentValidator()
    }

    @Test
    fun `Test testJavaProj assignment`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProj").file

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        assertTrue("report list should not be empty", !assignmentValidator.report.isEmpty())
    }


    @Test
    fun `Test testJavaProjWithUserIdOK assignment`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProjWithUserIdOK").file

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        assertTrue("report list should not be empty", !assignmentValidator.report.isEmpty())
    }

    @Test
    fun `Test testJavaProjWithUserIdNOK assignment`() {

        val assignmentFolder = resourceLoader.getResource("file:${sampleAssignmentsRootFolder}/testJavaProjWithUserIdNOK").file

        assignmentValidator.validate(assignmentFolder, dummyAssignment)
        val report = assignmentValidator.report
        assertTrue("report should not be empty", !report.isEmpty())
        assertTrue(report.any { it.type == AssignmentValidator.InfoType.WARNING })
        assertTrue(report.any { it.message == "POM file is not prepared to use the 'dropProject.currentUserId' system property" })
    }


}
