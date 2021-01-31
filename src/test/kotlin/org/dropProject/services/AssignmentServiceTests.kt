package org.dropProject.services

import junit.framework.Assert.assertEquals
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