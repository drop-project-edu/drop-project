/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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

import junit.framework.TestCase.assertEquals
import org.dropProject.dao.Assignment
import org.dropProject.dao.Language
import org.dropProject.forms.AssignmentForm
import org.dropProject.forms.SubmissionMethod
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class AssignmentServiceTests() {

    @Autowired
    private lateinit var assignmentService: AssignmentService

    @Test
    fun testUpdateAssignment() {
        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, language = Language.JAVA, active = true,
                gitRepositoryUrl = "git://dummyRepo", gitRepositoryFolder = "testJavaProj")

        // Create an AssignmentForm with information different from the one used to create the Assignment
        val assignmentForm = AssignmentForm()
        assignmentForm.assignmentName = "Test Project (renamed)"
        assignmentForm.assignmentPackage = "org.dropProject.sampleAssignments.testProject"
        assignmentForm.language = Language.KOTLIN
        assignmentForm.submissionMethod = SubmissionMethod.GIT

        assignmentService.updateAssignment(assignment01, assignmentForm)

        assertEquals("Test Project (renamed)", assignment01.name)
        assertEquals("org.dropProject.sampleAssignments.testProject", assignment01.packageName)
        assertEquals(Language.KOTLIN, assignment01.language)
        assertEquals(SubmissionMethod.GIT, assignment01.submissionMethod)

    }

}
