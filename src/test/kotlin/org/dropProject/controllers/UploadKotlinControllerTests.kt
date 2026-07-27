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

import org.dropproject.TestsHelper
import org.dropproject.dao.Assignment
import org.dropproject.dao.Indicator
import org.dropproject.dao.Language
import org.dropproject.dao.SubmissionReport
import org.dropproject.dao.SubmissionStructure
import org.dropproject.data.BuildReport
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssignmentRepository
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.dropproject.config.DropProjectProperties
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import java.io.File

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations=["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class UploadKotlinControllerTests {

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    @Autowired
    lateinit var mvc : MockMvc

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    private lateinit var testsHelper: TestsHelper

    val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

    @Before
    fun initMavenizedFolderAndCreateAssignment() {

        // init mavenized folder
        var folder = File(dropProjectProperties.mavenizedProjects.rootLocation)
        if(folder.exists()) {
            folder.deleteRecursively()
        }
        folder.mkdirs()

        // create assignment
        val assignment01 = Assignment(id = "testKotlinProj", name = "Test Project (for automatic tests)",
                packageName = null, ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true, language = Language.KOTLIN,
                gitRepositoryUrl = "git://dummyRepo",
                gitRepositoryFolder = "testKotlinProj")
        assignmentRepository.save(assignment01)

        val assignment02 = Assignment(id = "testKotlinProj2", name = "Test Project (for automatic tests)",
            packageName = "org.dropproject.samples.samplekotlinassignment", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, language = Language.KOTLIN,
            gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testKotlinProj2")
        assignmentRepository.save(assignment02)
    }

    @After
    fun cleanup() {
        val folder = File(dropProjectProperties.mavenizedProjects.rootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }

        val submissionsFolder = File(dropProjectProperties.storage.rootLocation)
        if (submissionsFolder.exists()) {
            submissionsFolder.deleteRecursively()
        }
    }


    @Test
    @DirtiesContext
    fun submitProjectOK() {

        val assignment = assignmentRepository.findById("testKotlinProj").get()
        val submissionId = testsHelper.uploadProject(this.mvc,"projectKotlinNoPackageOK", "testKotlinProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 4 lines", 4, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary.get(0).indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary.get(0).reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("junit should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be OK (value)", "OK", summary[3].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())
        assert(buildResult.checkstyleErrors.isEmpty())
        assert(buildResult.PMDerrors().isEmpty())
        assert(buildResult.hasJUnitErrors() == false)
        assertNotNull(buildResult.elapsedTimeJUnit())
        assert(buildResult.elapsedTimeJUnit()!! > 0.toBigDecimal())

    }

    @Test
    @DirtiesContext
    fun submitProjectStyleErrors1() {

        val assignment = assignmentRepository.findById("testKotlinProj").get()
        val submissionId = testsHelper.uploadProject(this.mvc,"projectKotlinWithStyleErrors", "testKotlinProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 4 lines", 4, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be NOK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be NOK (value)", "NOK", summary[2].reportValue)
        assertEquals("junit should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be OK (value)", "OK", summary[3].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())

        assertEquals("checkstyle should have 3 errors", buildResult.checkstyleErrors.size, 3)
        assertThat(buildResult.checkstyleErrors,
                CoreMatchers.hasItems(
                        "Function parameter name should start with a lowercase letter. If the name has more than one word, subsequent words should be capitalized at Main.kt:20:14",
                        "Variable name should start with a lowercase letter. If the name has more than one word, subsequent words should be capitalized at Main.kt:34:9",
                        "Function name should start with a lowercase letter. If the name has more than one word, subsequent words should be capitalized at Main.kt:20:5",
                ))

        assert(buildResult.hasJUnitErrors() == false)
        assertNotNull(buildResult.elapsedTimeJUnit())
        assert(buildResult.elapsedTimeJUnit()!! > 0.toBigDecimal())

    }

    @Test
    @DirtiesContext
    fun submitProjectStyleErrorsAboveThreshold() {

        val assignment = assignmentRepository.findById("testKotlinProj").get()
        val submissionId = testsHelper.uploadProject(this.mvc,"projectKotlinTooManyStyleErrors", "testKotlinProj",
            STUDENT_1, submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        // exceeding the threshold must not be reported as an internal error
        assertNull("there should be no error", reportResult.modelAndView!!.modelMap["error"])

        val warning = reportResult.modelAndView!!.modelMap["warning"] as String?
        assertNotNull("the student must be told why the tests didn't run", warning)
        assertThat(warning, CoreMatchers.containsString("exceeded the maximum number of code quality issues"))
        assertThat(warning, CoreMatchers.containsString("unit tests were not executed"))

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 4 lines", 4, summary.size)
        assertEquals("compilation should be OK", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be NOK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be NOK (value)", "NOK", summary[2].reportValue)

        // the tests were never executed, so they are all failing and there is no progress to show
        assertEquals("junit should be NOK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be NOK (value)", "NOK", summary[3].reportValue)
        assertNull("there should be no progress", summary[3].reportProgress)
        assertNull("there should be no goal", summary[3].reportGoal)

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertTrue("the threshold should have been detected", buildResult.codeQualityThresholdExceeded())
        assertNotNull("the weighted issue count should be known", buildResult.codeQualityWeightedIssues())

        // the model keeps every issue - it's the template that only renders the first ones
        assertTrue("detekt should have reported more than 10 issues, got ${buildResult.checkstyleErrors.size}",
            buildResult.checkstyleErrors.size > 10)

        val html = reportResult.response.contentAsString
        assertThat(html, CoreMatchers.containsString("unit tests were not executed"))
        assertEquals("only the first 10 issues should be rendered", 10,
            buildResult.checkstyleErrors.count { html.contains(it) })
        assertThat(html, CoreMatchers.containsString("and ${buildResult.checkstyleErrors.size - 10} more"))
    }

    @Test
    @DirtiesContext
    fun submitProjectCompilationError() {

        val assignment = assignmentRepository.findById("testKotlinProj").get()
        val submissionId = testsHelper.uploadProject(this.mvc,"projectKotlinCompilationError", "testKotlinProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 4 lines", 2, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary.get(0).indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary.get(0).reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be NOK (value)", "NOK", summary[1].reportValue)
    }

    // https://github.com/drop-project-edu/drop-project/issues/97
    @Test
    @DirtiesContext
    fun submitKotlinProjectWithJavaFileInThePackage() {

        val assignment = assignmentRepository.findById("testKotlinProj2").get()
        val submissionId = testsHelper.uploadProject(this.mvc,"projectKotlinWithJavaFile", "testKotlinProj2",
            STUDENT_1, submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        // java files in a Kotlin assignment break the build in a way that the student can't understand, so they
        // must be rejected as a structure error, instead of resulting in an internal error
        assertNull("there should be no error", reportResult.modelAndView!!.modelMap["error"])

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 1 line", 1, summary.size)
        assertEquals("projectStructure should be NOK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be NOK (value)", "NOK", summary[0].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertThat(structureErrors, CoreMatchers.hasItems(
            "This is a Kotlin assignment, so it only accepts Kotlin (.kt) files, but the project contains the " +
                    "following Java (.java) files: src/org/dropproject/samples/samplekotlinassignment/Main.java. " +
                    "Please delete them and submit again."))
    }

    @Test
    @DirtiesContext
    fun submitProjectAndCheckREADME() {

        val assignment = assignmentRepository.findById("testKotlinProj2").get()
        val submissionId = testsHelper.uploadProject(this.mvc,"projectKotlinOK", "testKotlinProj2", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 4 lines", 4, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary.get(0).indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary.get(0).reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("junit should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be OK (value)", "OK", summary[3].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())
        assert(buildResult.checkstyleErrors.isEmpty())
        assert(buildResult.PMDerrors().isEmpty())
        assert(buildResult.hasJUnitErrors() == false)
        assertNotNull(buildResult.elapsedTimeJUnit())
        assert(buildResult.elapsedTimeJUnit()!! > 0.toBigDecimal())

        val readmeHTML = reportResult.modelAndView!!.modelMap["readmeHTML"] as String
        assertEquals("""
            <hr/>
            <h1>README example</h1>
            <p>Some text...</p>
            <hr/>
            
            """.trimIndent(), readmeHTML)

    }

    @Test
    @DirtiesContext
    fun getUploadPageAndCheckInstructions() {

        fun normalizeString(input: String): String {
            return input.lines().joinToString("\n") { it.trimStart() }
        }

        // the assignment testKotlinProj2 has both an instructions.html and an instructions.md. It should render the latter
        val reportResult = this.mvc.perform(get("/upload/testKotlinProj2")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-upload-form"))
            .andReturn()

        val readmeHTML = reportResult.modelAndView!!.modelMap["instructionsFragment"] as String
        assertEquals(
            normalizeString("""
                    <h1>Sample Kotlin Assignment</h1>
                    <p>This is just a very simple Kotlin assignment just to experiment with Drop Project.</p>
                    <p>The source of this assignment is available on <a href="https://github.com/drop-project-edu/sampleKotlinAssignment">https://github.com/drop-project-edu/sampleKotlinAssignment</a></p>
                    <h2>Instructions</h2>
                    <ul>
                    <li>
                    <p>Create a Kotlin project in your IDE with the structure depicted at the end of this page.
                    In particular, you must create a package <code>org.dropProject.samples.samplekotlinassignment</code> and
                    create a <code>Main.kt</code> in that package.</p>
                    </li>
                    <li>
                    <p>Within your <code>Main.kt</code> file, implement a top-level function to calculate the
                    maximum value on an array of integers. This function must have the following signature:</p>
                    <p><code>findMax(numbers: Array&lt;Int&gt;): Int</code></p>
                    </li>
                    <li>
                    <p>Create a zip file of your project and drop it on the area above these instructions.
                    In a few seconds, Drop Project will give you a report with some metrics about your project.
                    If you don't feel like coding this stuff, you can grab a pre-built submission
                    <a href="https://github.com/drop-project-edu/sampleKotlinAssignment/raw/master/sampleKotlinSubmission.zip">here</a>.</p>
                    </li>
                    </ul>
                    <h2>Additional information</h2>
                    <p>Check this <a href="testKotlinProj2/public/file.txt">file</a> for additional information</p>
                    
            """.trimIndent()), normalizeString(readmeHTML))

    }


}



