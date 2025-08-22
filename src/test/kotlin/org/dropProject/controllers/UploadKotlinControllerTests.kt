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
import org.dropproject.data.BuildReport
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.AssignmentRepository
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        val submissionId = testsHelper.uploadProject(this.mvc,"projectKotlinNoPackageOK", "testKotlinProj", STUDENT_1)

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

        val submissionId = testsHelper.uploadProject(this.mvc,"projectKotlinWithStyleErrors", "testKotlinProj", STUDENT_1)

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
                        "Nome do parâmetro de função deve começar por letra minúscula. Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula) at Main.kt:20:14",
                        "Nome da variável deve começar por letra minúscula. Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula) at Main.kt:34:9",
                        "Nome da função deve começar por letra minúscula. Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula) at Main.kt:20:5",
                ))

        assert(buildResult.hasJUnitErrors() == false)
        assertNotNull(buildResult.elapsedTimeJUnit())
        assert(buildResult.elapsedTimeJUnit()!! > 0.toBigDecimal())

    }

    @Test
    @DirtiesContext
    fun submitProjectCompilationError() {

        val submissionId = testsHelper.uploadProject(this.mvc,"projectKotlinCompilationError", "testKotlinProj", STUDENT_1)

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

    @Test
    @DirtiesContext
    fun submitProjectAndCheckREADME() {

        val submissionId = testsHelper.uploadProject(this.mvc,"projectKotlinOK", "testKotlinProj2", STUDENT_1)

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



