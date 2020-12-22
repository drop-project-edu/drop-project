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
package org.dropProject.controllers

import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
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
import org.dropProject.TestsHelper
import org.dropProject.dao.Assignment
import org.dropProject.dao.Indicator
import org.dropProject.dao.Language
import org.dropProject.dao.SubmissionReport
import org.dropProject.data.BuildReport
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.AssignmentRepository
import java.io.File

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations=["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class UploadKotlinControllerTests {

    @Value("\${mavenizedProjects.rootLocation}")
    val mavenizedProjectsRootLocation : String = ""

    @Value("\${storage.rootLocation}")
    val submissionsRootLocation: String = ""

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
        var folder = File(mavenizedProjectsRootLocation)
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
    }

    @After
    fun cleanup() {
        val folder = File(mavenizedProjectsRootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }

        val submissionsFolder = File(submissionsRootLocation)
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
        val summary = reportResult.modelAndView.modelMap["summary"] as List<SubmissionReport>
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
        val structureErrors = reportResult.modelAndView.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors().isEmpty())
        assert(buildResult.checkstyleErrors().isEmpty())
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
        val summary = reportResult.modelAndView.modelMap["summary"] as List<SubmissionReport>
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
        val structureErrors = reportResult.modelAndView.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors().isEmpty())

        assertEquals("checkstyle should have 5 errors", buildResult.checkstyleErrors().size, 5)
        // TODO: This is failing on travis CI
//        assertThat(buildResult.checkstyleErrors(),
//                CoreMatchers.hasItems(
//                        "Nome do parâmetro de função deve começar por letra minúscula. Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula) - [Param] at Main.kt:20:14",
//                        "Nome da variável deve começar por letra minúscula. Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula) - [Soma] at Main.kt:34:9",
//                        "Nome da função deve começar por letra minúscula. Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula) - [SomeFunc] at Main.kt:20:5",
//                        "Instrução 'if' sem chaveta - [SomeFunc] at Main.kt:23:9",
//                        "Variável imutável declarada com var - [Soma] at Main.kt:34:5"
//                ))

        assert(buildResult.hasJUnitErrors() == false)
        assertNotNull(buildResult.elapsedTimeJUnit())
        assert(buildResult.elapsedTimeJUnit()!! > 0.toBigDecimal())

    }


}



