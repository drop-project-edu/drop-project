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

import junit.framework.TestCase.*
import org.dropProject.TestsHelper
import org.dropProject.dao.*
import org.dropProject.data.BuildReport
import org.dropProject.data.SubmissionInfo
import org.dropProject.data.TestType
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.*
import org.dropProject.services.ZipService
import org.dropProject.storage.StorageService
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.hasProperty
import org.hamcrest.collection.IsCollectionWithSize
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.verify
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ResourceLoader
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.nio.file.Files
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class UploadControllerTests {

    @Value("\${mavenizedProjects.rootLocation}")
    val mavenizedProjectsRootLocation: String = ""

    @Value("\${assignments.rootLocation}")
    val assignmentsRootLocation: String = ""

    @Value("\${storage.rootLocation}")
    val submissionsRootLocation: String = ""

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    @Autowired
    lateinit var authorRepository: AuthorRepository

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var jUnitReportRepository: JUnitReportRepository

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var assigneeRepository: AssigneeRepository

    @Autowired
    lateinit var assignmentTestMethodRepository: AssignmentTestMethodRepository

    @Autowired
    lateinit var projectGroupRestrictionsRepository: ProjectGroupRestrictionsRepository

    @Autowired
    lateinit var zipService: ZipService

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var testsHelper: TestsHelper

    val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_2 = User("student2", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_3 = User("student3", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

    @Before
    fun setup() {
        var folder = File(mavenizedProjectsRootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
        folder.mkdirs()

        // create initial assignment
        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = "testJavaProj", gitCurrentHash = "somehash")
        assignmentRepository.save(assignment01)

        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment01,
                                                testClass = "TestTeacherProject", testMethod = "testFuncaoParaTestar"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment01,
                                                testClass = "TestTeacherProject", testMethod = "testFuncaoLentaParaTestar"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment01,
                                                testClass = "TestTeacherHiddenProject", testMethod = "testFuncaoParaTestarQueNaoApareceAosAlunos"))
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

    // @Test - rever isto
    fun shouldNotAcceptNoZipFile() {
        val multipartFile = MockMultipartFile("file", "test.txt", "text/plain", "Spring Framework".toByteArray())
        this.mvc.perform(multipart("/upload").file(multipartFile).with(user(STUDENT_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/upload"))
                .andExpect(flash().attribute("error", "O ficheiro tem que ser um .zip"))

        verify(this.storageService, never()).store(multipartFile, "")
    }

    // @Test   - Infelizmente o MockMvc não consegue testar isto....
    fun shouldNotAcceptBigFile() {

        val bigFileData = ByteArray(100_000_000) { 1 }

        val multipartFile = MockMultipartFile("file", "test.txt", "text/plain", bigFileData)
        this.mvc.perform(multipart("/upload").file(multipartFile).with(user(STUDENT_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/upload"))
                .andExpect(flash().attribute("error", "Ficheiro excede o tamanho máximo permitido"))

        verify(this.storageService, never()).store(multipartFile, "")
    }

    @Test
    @DirtiesContext
    fun getUploadPage() {

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-upload-form"))
                // .andExpect(model().attribute("uploadSubmission", null))  ?????


    }

    @Test
    @DirtiesContext
    fun getUploadPageWithCooloff() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.cooloffPeriod = 10
        assignmentRepository.save(assignment)

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-upload-form"))


    }

    @Test
    @DirtiesContext
    fun uploadProjectGoesIntoRightFolder() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1)

        val submissionDB = submissionRepository.findById(submissionId.toLong()).get()
        val submissionFolder = File("$submissionsRootLocation/upload", submissionDB.submissionFolder)

        assertTrue("submission folder doesn't exist", submissionFolder.exists())

    }

    @Test
    @DirtiesContext
    fun uploadProjectInvalidStructure1() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 1 line", 1, summary.size)
        assertEquals("projectStructure should be NOK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be NOK (value)", "NOK", summary[0].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertThat(structureErrors,
                CoreMatchers.hasItems("O projecto não contém uma pasta 'src/org/dropProject/sampleAssignments/testProj'",
                        "O projecto não contém o ficheiro Main.java na pasta 'src/org/dropProject/sampleAssignments/testProj'"))
    }


    @Test
    @DirtiesContext
    fun uploadProjectInvalidStructure2() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure2", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 1 line", 1, summary.size)
        assertEquals("projectStructure should be NOK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be NOK (value)", "NOK", summary[0].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertThat(structureErrors,
                CoreMatchers.hasItems("O projecto contém uma pasta README.md mas devia ser um ficheiro"))
    }


    @Test
    @DirtiesContext
    fun uploadProjectDoesntCompile() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 2 lines", 2, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be NOK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be NOK (value)", "NOK", summary[1].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertThat(buildResult.compilationErrors,
                CoreMatchers.hasItems("org/dropProject/sampleAssignments/testProj/Main.java:[3,8] class Sample is public, should be declared in a file named Sample.java"))
    }

    @Test
    @DirtiesContext
    fun uploadProjectWithCompilationErrorsThenCooloff() { // cooloff is reduced for structure or compilation errors

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.cooloffPeriod = 10
        assignmentRepository.save(assignment)

        testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
        val now = LocalTime.now()

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-upload-form"))
                .andExpect(model().attribute("coolOffEnd",
                        now.plusMinutes(2).format(DateTimeFormatter.ofPattern("HH:mm"))))
    }

    @Test
    @DirtiesContext
    fun uploadProjectThenCooloff() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.cooloffPeriod = 10
        assignmentRepository.save(assignment)

        testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors", "testJavaProj", STUDENT_1)
        val now = LocalTime.now()

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-upload-form"))
                .andExpect(model().attribute("coolOffEnd",
                        now.plusMinutes(10).format(DateTimeFormatter.ofPattern("HH:mm"))))
    }

    @Test
    @DirtiesContext
    fun uploadProjectCheckstyleErrors() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 5 lines", 5, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be NOK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be NOK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be NOK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be NOK (value)", "NOK", summary[2].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())

        assertEquals("checkstyle should have 6 errors", buildResult.checkstyleErrors.size, 6)
        assertThat(buildResult.checkstyleErrors,
                CoreMatchers.hasItems(
                        "org/dropProject/sampleAssignments/testProj/Main.java:12:17: Nome da função 'FazCoisas' deve começar por letra minúscula. Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula). [MethodName]"
                        , "org/dropProject/sampleAssignments/testProj/Main.java:3:7: O nome da classe 'aluno' deve começar com letra maiúscula. Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula). [TypeName]"
                        , "org/dropProject/sampleAssignments/testProj/Main.java:10:22: A constante 'constante' deve estar em maiúsculas. Caso o nome tenha mais do que uma palavra, as mesmas devem ser separadas pelo caracter underscore(_) [ConstantName]"
                        , "org/dropProject/sampleAssignments/testProj/Main.java:30:9: 'if' deve usar '{}'s mesmo que seja uma única linha [NeedBraces]"
                        , "org/dropProject/sampleAssignments/testProj/Main.java:4:9: O nome da variável 'Numero' deve começar com letra minúscula. Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula). [MemberName]"
                        , "org/dropProject/sampleAssignments/testProj/Main.java:31: Não é permitida a utilização da instrução System.exit(). Deve lançar uma Exception ou tratar graciosamente o erro. [RegexpSinglelineJava]"
                ))
    }

    @Test
    @DirtiesContext
    fun uploadProjectOK() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectOK", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 5 lines", 5, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
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
        assert(buildResult.elapsedTimeJUnit()!! > 1.toBigDecimal())

        // check that the submission was associated with the right assignment git hash
        val submissionFromDB = submissionRepository.findById(submissionId.toLong()).get()
        assertEquals("somehash", submissionFromDB.assignmentGitHash)
    }

    @Test
    @DirtiesContext
    fun uploadProjectJava17() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectJava17", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 5 lines", 5, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("junit should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be OK (value)", "OK", summary[3].reportValue)
    }

    @Test
    @DirtiesContext
    fun uploadProjectWithREADME() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWithREADME", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val readmeContent = reportResult.modelAndView!!.modelMap["readmeHTML"] as String
        if (!readmeContent.contains("Test README")) {
            fail("README doesn't contain 'Test README'")
        }
    }

    @Test
    @DirtiesContext
    fun multipleSubmissionsIncrementsCounter() {

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
    @DirtiesContext
    fun cantSeeOtherSubmissions() {

        testsHelper.uploadProject(this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1)

        this.mvc.perform(get("/buildReport/1")
                .with(user(STUDENT_3)))
                .andExpect(status().isForbidden())

    }

    @Test
    @DirtiesContext
    fun uploadProjectHackingAttempt() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectHackingAttempt", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("junit should be NOK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be NOK (value)", "NOK", summary[3].reportValue)

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertEquals(2, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numErrors)
        assertTrue(buildResult.junitErrorsTeacher?.contains("SecurityException") == true)
    }


    @Test
    @DirtiesContext
    fun uploadInGroupAndThenInAnotherGroup() {

        val projectRoot = resourceLoader.getResource("file:src/test/sampleProjects/projectCompilationErrors").file

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("numSubmissions", 0L))

        val submissionId1 = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
        assertEquals("wrong submissionId", 1, submissionId1.toLong())

        val submissionId2 = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
        assertEquals("wrong submissionId", 2, submissionId2.toLong())

        // let's change the AUTHORS
        val path = File(projectRoot, "AUTHORS.txt").toPath()
        val lines = Files.readAllLines(path)
        assertEquals("student1;Student 1", lines[0])
        assertEquals("student2;Student 2", lines[1])

        try {
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write("student3;Student 3")
            writer.close()

            val submissionId3 = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
            assertEquals("wrong submissionId", 3, submissionId3.toLong())

        } finally {
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write(lines[1])
            writer.close()
        }

        val authors = authorRepository.findAll(Sort.by(Sort.Direction.ASC, "userId"))
        assertEquals(4, authors.size)
        assertEquals(authors[0].userId, "student1")
        assertEquals(authors[0].group.id, 1)
        assertEquals(authors[1].userId, "student1")
        assertEquals(authors[1].group.id, 2)
        assertEquals(authors[2].userId, "student2")
        assertEquals(authors[2].group.id, 1)
        assertEquals(authors[3].userId, "student3")
        assertEquals(authors[3].group.id, 2)

        val submissions = submissionRepository.findAll()
        assertEquals(3, submissions.size)
        for (submission in submissions) {
            assertEquals("student1", submission.submitterUserId)
        }

    }

    @Test
    @DirtiesContext
    fun uploadProjectJunitErrors() {
        val submissionId = testsHelper.uploadProject(this.mvc, "projectJUnitErrors", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 5 lines", 5, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("junit (public) should be NOK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit (public) should be NOK (value)", "NOK", summary[3].reportValue)
        assertEquals("junit (public) should pass 1 test", 1, summary[3].reportProgress)
        assertEquals("junit (public) should have total 2 tests", 2, summary[3].reportGoal)
        assertEquals("junit (hidden) should be NOK (key)", Indicator.HIDDEN_UNIT_TESTS, summary[4].indicator)
        assertEquals("junit (hidden) should be NOK (value)", "NOK", summary[4].reportValue)
        assertEquals("junit (hidden) should pass 0 tests", 0, summary[4].reportProgress)
        assertEquals("junit (hidden) should have total 1 test", 1, summary[4].reportGoal)

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == true)
        assert(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 1, Errors: 0"))
        assertNotNull(buildResult.junitErrorsTeacher)
        assert(buildResult.junitErrorsTeacher!!.contains("java.lang.AssertionError: expected:<3> but was:<0>"))
        assertEquals(2, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numTests)
        assertEquals(1, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numFailures)
        assertEquals(0, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numErrors)
        assertEquals("1/2", buildResult.junitSummaryAsObject(TestType.TEACHER)?.toStr())
        val stackTraceTeacher = buildResult.junitErrorsTeacher
        assertEquals("""
            |FAILURE: org.dropProject.sampleAssignments.testProj.TestTeacherProject.testFuncaoParaTestar
            |java.lang.AssertionError: expected:<3> but was:<0>
	        |${'\t'}at org.dropProject.sampleAssignments.testProj.TestTeacherProject.testFuncaoParaTestar(TestTeacherProject.java:10)
        """.trimMargin(), stackTraceTeacher?.trimEnd())

        assert(buildResult.hasJUnitErrors(TestType.HIDDEN) == true)
        assert(buildResult.junitSummaryHidden!!.startsWith("Tests run: 1, Failures: 0, Errors: 1"))
        assertNotNull(buildResult.junitErrorsHidden)
        assert(buildResult.junitErrorsHidden!!.contains("java.lang.ArithmeticException: / by zero"))
        assertEquals(1, buildResult.junitSummaryAsObject(TestType.HIDDEN)?.numTests)
        assertEquals(0, buildResult.junitSummaryAsObject(TestType.HIDDEN)?.numFailures)
        assertEquals(1, buildResult.junitSummaryAsObject(TestType.HIDDEN)?.numErrors)
        assertEquals("0/1", buildResult.junitSummaryAsObject(TestType.HIDDEN)?.toStr())
        val stackTraceHidden = buildResult.junitErrorsHidden
        assertEquals("""
            |ERROR: org.dropProject.sampleAssignments.testProj.TestTeacherHiddenProject.testFuncaoParaTestarQueNaoApareceAosAlunos
            |java.lang.ArithmeticException: / by zero
            |${'\t'}at org.dropProject.sampleAssignments.testProj.Main.funcaoQueRebenta(Main.java:14)
            |${'\t'}at org.dropProject.sampleAssignments.testProj.TestTeacherHiddenProject.testFuncaoParaTestarQueNaoApareceAosAlunos(TestTeacherHiddenProject.java:10)
        """.trimMargin(), stackTraceHidden?.trimEnd())

        val testResults = buildResult.testResults()
        assertNotNull(testResults)
        assertEquals(3, testResults!!.size)
        assertEquals("testFuncaoParaTestar", testResults[0].methodName)
        assertEquals("testFuncaoLentaParaTestar", testResults[1].methodName)
        assertEquals("testFuncaoParaTestarQueNaoApareceAosAlunos", testResults[2].methodName)
    }

    @Test
    @DirtiesContext
    fun uploadProjectJunit5Errors() {
        // this test is similar to uploadProjectJunitErrors but with a JUnit 5 enabled assignment

        val assignment = Assignment(id = "testJavaProjJUnit5", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testJavaProjJUnit5", gitCurrentHash = "somehash")
        assignmentRepository.save(assignment)

        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment, testClass = "TestTeacherProject", testMethod = "testFuncaoParaTestar"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment, testClass = "TestTeacherProject", testMethod = "testFuncaoLentaParaTestar"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment, testClass = "TestTeacherHiddenProject", testMethod = "testFuncaoParaTestarQueNaoApareceAosAlunos"))

        val submissionId = testsHelper.uploadProject(this.mvc, "projectJUnitErrors", "testJavaProjJUnit5", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 5 lines", 5, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("junit (public) should be NOK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit (public) should be NOK (value)", "NOK", summary[3].reportValue)
        assertEquals("junit (public) should pass 1 test", 1, summary[3].reportProgress)
        assertEquals("junit (public) should have total 2 tests", 2, summary[3].reportGoal)
        assertEquals("junit (hidden) should be NOK (key)", Indicator.HIDDEN_UNIT_TESTS, summary[4].indicator)
        assertEquals("junit (hidden) should be NOK (value)", "NOK", summary[4].reportValue)
        assertEquals("junit (hidden) should pass 0 tests", 0, summary[4].reportProgress)
        assertEquals("junit (hidden) should have total 1 test", 1, summary[4].reportGoal)

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == true)
        assert(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 1, Errors: 0"))
        assertNotNull(buildResult.junitErrorsTeacher)
        assert(buildResult.junitErrorsTeacher!!.contains("org.opentest4j.AssertionFailedError: expected: <3> but was: <0>"))
        assertEquals(2, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numTests)
        assertEquals(1, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numFailures)
        assertEquals(0, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numErrors)
        assertEquals("1/2", buildResult.junitSummaryAsObject(TestType.TEACHER)?.toStr())
        val stackTraceTeacher = buildResult.junitErrorsTeacher
        assertEquals("""
            |FAILURE: org.dropProject.sampleAssignments.testProj.TestTeacherProject.testFuncaoParaTestar
            |org.opentest4j.AssertionFailedError: expected: <3> but was: <0>
	        |${'\t'}at org.dropProject.sampleAssignments.testProj.TestTeacherProject.testFuncaoParaTestar(TestTeacherProject.java:13)
        """.trimMargin(), stackTraceTeacher?.trimEnd())

        assert(buildResult.hasJUnitErrors(TestType.HIDDEN) == true)
        assert(buildResult.junitSummaryHidden!!.startsWith("Tests run: 1, Failures: 0, Errors: 1"))
        assertNotNull(buildResult.junitErrorsHidden)
        assert(buildResult.junitErrorsHidden!!.contains("java.lang.ArithmeticException: / by zero"))
        assertEquals(1, buildResult.junitSummaryAsObject(TestType.HIDDEN)?.numTests)
        assertEquals(0, buildResult.junitSummaryAsObject(TestType.HIDDEN)?.numFailures)
        assertEquals(1, buildResult.junitSummaryAsObject(TestType.HIDDEN)?.numErrors)
        assertEquals("0/1", buildResult.junitSummaryAsObject(TestType.HIDDEN)?.toStr())
        val stackTraceHidden = buildResult.junitErrorsHidden
        assertEquals("""
            |ERROR: org.dropProject.sampleAssignments.testProj.TestTeacherHiddenProject.testFuncaoParaTestarQueNaoApareceAosAlunos
            |java.lang.ArithmeticException: / by zero
            |${'\t'}at org.dropProject.sampleAssignments.testProj.Main.funcaoQueRebenta(Main.java:14)
            |${'\t'}at org.dropProject.sampleAssignments.testProj.TestTeacherHiddenProject.testFuncaoParaTestarQueNaoApareceAosAlunos(TestTeacherHiddenProject.java:13)
        """.trimMargin(), stackTraceHidden?.trimEnd())

        val testResults = buildResult.testResults()
        assertNotNull(testResults)
        assertEquals(3, testResults!!.size)
        assertEquals("testFuncaoParaTestar", testResults[0].methodName)
        assertEquals("testFuncaoLentaParaTestar", testResults[1].methodName)
        assertEquals("testFuncaoParaTestarQueNaoApareceAosAlunos", testResults[2].methodName)
    }

    @Test
    @DirtiesContext
    fun uploadProjectJunitWithSkippedTests() {

        // this assignment has one test marked with @Ignore
        val assignment = Assignment(id = "testJavaProjWithIgnoredTests", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", submissionMethod = SubmissionMethod.UPLOAD,
                gitRepositoryUrl = "git://dummy", language = Language.JAVA, ownerUserId = "teacher1",
                gitRepositoryFolder = "testJavaProjWithIgnoredTests")
        assignment.active = true
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectJUnitErrors", "testJavaProjWithIgnoredTests", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("junit (public) should be NOK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit (public) should be NOK (value)", "NOK", summary[3].reportValue)
        assertEquals("junit (public) should pass 0 tests", 0, summary[3].reportProgress)
        assertEquals("junit (public) should have total 1 test", 1, summary[3].reportGoal)

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == true)
        assert(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 1, Failures: 1, Errors: 0"))
        assertNotNull(buildResult.junitErrorsTeacher)
        assert(buildResult.junitErrorsTeacher!!.contains("java.lang.AssertionError: expected:<3> but was:<0>"))
        assertEquals(1, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numTests)
        assertEquals(1, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numFailures)
        assertEquals(0, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numErrors)
        assertEquals(1, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numSkipped)
        val stackTraceTeacher = buildResult.junitErrorsTeacher
        assertEquals("""
            |FAILURE: org.dropProject.sampleAssignments.testProj.TestTeacherProject.testFuncaoParaTestar
            |java.lang.AssertionError: expected:<3> but was:<0>
	        |${'\t'}at org.dropProject.sampleAssignments.testProj.TestTeacherProject.testFuncaoParaTestar(TestTeacherProject.java:10)
        """.trimMargin(), stackTraceTeacher?.trimEnd())
    }

    @Test
    @DirtiesContext
    fun uploadProjectJunitErrors_HiddenTestsVisibility() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.hiddenTestsVisibility = TestVisibility.HIDE_EVERYTHING  // <<< this is very important for this test
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectJUnitErrors", "testJavaProj", STUDENT_1)

        this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Teacher Hidden Unit Tests"))))

        this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(TEACHER_1)))
//                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teacher Hidden Unit Tests")))
                .andExpect(content().string(containsString("<span class=\"label label-danger\">0 / 1</span>")))  // progress for hidden tests

        assignment.hiddenTestsVisibility = TestVisibility.SHOW_OK_NOK  // <<< this is very important for this test
        assignmentRepository.save(assignment)

        this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teacher Hidden Unit Tests")))
                .andExpect(content().string(not(containsString("<span class=\"label label-danger\">0 / 1</span>"))))  // progress for hidden tests

        this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teacher Hidden Unit Tests")))
                .andExpect(content().string(containsString("<span class=\"label label-danger\">0 / 1</span>")))  // progress for hidden tests

        assignment.hiddenTestsVisibility = TestVisibility.SHOW_PROGRESS  // <<< this is very important for this test
        assignmentRepository.save(assignment)

        this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teacher Hidden Unit Tests")))
                .andExpect(content().string(containsString("<span class=\"label label-danger\">0 / 1</span>")))  // progress for hidden tests

        this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(TEACHER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teacher Hidden Unit Tests")))
                .andExpect(content().string(containsString("<span class=\"label label-danger\">0 / 1</span>")))  // progress for hidden tests
    }


    // tests an AUTHORS.txt that is not in UTF-8
    @Test
    @DirtiesContext
    fun uploadProjectOtherEncoding() {

        val uploader = User("a21702482", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
        val submissionId = testsHelper.uploadProject(this.mvc, "projectOtherEncoding", "testJavaProj", uploader)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(uploader)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 5 lines", 5, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("junit should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be OK (value)", "OK", summary[3].reportValue)


    }

    // tests an AUTHORS.txt that has a weird character at the beginning (\uFFEF), also called a BOM
    @Test
    @WithMockUser("a21702482", roles = ["STUDENT"])
    @DirtiesContext
    fun uploadProjectWithBOM() {

        val uploader = User("a21702482", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
        val submissionId = testsHelper.uploadProject(this.mvc, "projectWithBOM", "testJavaProj", uploader)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(uploader)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 5 lines", 5, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("junit should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be OK (value)", "OK", summary[3].reportValue)
    }

    @Test
    @DirtiesContext
    fun uploadGroupWithDuplicateMembers() {

        val projectRoot = resourceLoader.getResource("file:src/test/sampleProjects/projectCompilationErrors").file

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("numSubmissions", 0L))

        // let's change the AUTHORS to have duplicate authors
        val path = File(projectRoot, "AUTHORS.txt").toPath()
        val lines = Files.readAllLines(path)
        assertEquals("student1;Student 1", lines[0])
        assertEquals("student2;Student 2", lines[1])

        try {
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write("student1;Student 1")
            writer.close()

            val zipFile = zipService.createZipFromFolder("test", projectRoot)
            zipFile.deleteOnExit()

            val multipartFile = MockMultipartFile("file", zipFile.name, "application/zip", zipFile.readBytes())

            this.mvc.perform(multipart("/upload")
                    .file(multipartFile)
                    .param("assignmentId", "testJavaProj")
                    .param("async", "false")
                    .with(user(STUDENT_1)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().json("{ \"error\": \"O ficheiro AUTHORS.txt não está correcto. Contém autores duplicados.\"}"))

        } finally {
            val writer = Files.newBufferedWriter(path)
            writer.write(lines[0])
            writer.newLine()
            writer.write(lines[1])
            writer.close()
        }
    }

    @Test
    @DirtiesContext
    fun uploadProjectJunitErrorsWithTwoTestFiles() {

        // this assignment has two test files
        val assignment = Assignment(id = "testJavaProj2", name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", submissionMethod = SubmissionMethod.UPLOAD,
                gitRepositoryUrl = "git://dummy", language = Language.JAVA, ownerUserId = "teacher1",
                gitRepositoryFolder = "testJavaProj2")
        assignment.active = true
        assignment.acceptsStudentTests = false
        assignment.submissionMethod = SubmissionMethod.UPLOAD
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectJUnitErrors", "testJavaProj2", STUDENT_1)

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
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("junit should be NOK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be NOK (value)", "NOK", summary[3].reportValue)
        assertEquals("junit should pass 2 tests", 2, summary[3].reportProgress)
        assertEquals("junit should have total 4 tests", 4, summary[3].reportGoal)

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        println("buildResult = ${buildResult.mavenOutput()}")
        assert(buildResult.hasJUnitErrors() == true)
        assert(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 4, Failures: 2, Errors: 0, Time elapsed"))
        assertNotNull(buildResult.junitErrorsTeacher)
        assert(buildResult.junitErrorsTeacher!!.contains("java.lang.AssertionError: expected:<3> but was:<0>"))

        val junitReportsFromDB = jUnitReportRepository.findAll()
        assertEquals(2, junitReportsFromDB.size)
        val expectedFileNames = listOf(
            "TEST-org.dropProject.sampleAssignments.testProj.TestProject1.xml",
            "TEST-org.dropProject.sampleAssignments.testProj.TestProject2.xml"
        )
        assertThat(expectedFileNames,
            Matchers.containsInAnyOrder(junitReportsFromDB[0].fileName, junitReportsFromDB[1].fileName))
    }

    @Test
    @DirtiesContext
    fun uploadProjectUnexpectedCharacter() {
        val uploader = User("p4453", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))
        val submissionId = testsHelper.uploadProject(this.mvc, "projectUnexpectedCharacter", "testJavaProj", uploader)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(uploader)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>

        assertEquals("Summary should be 2 lines", 2, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be NOK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be NOK (value)", "NOK", summary[1].reportValue)

    }

    @Test
    @DirtiesContext
    fun homeRedirectsToActiveAssignmentOnlyWhenYouAreInWhiteList() {

        // remove student1 from any white lists that might exist
        assigneeRepository.deleteByAuthorUserId(authorUserId = "student1")

        this.mvc.perform(get("/")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You don't have any assignments yet.")))

        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))

        this.mvc.perform(get("/").with(user(STUDENT_1)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/upload/testJavaProj"))
    }

    @Test
    @DirtiesContext
    fun accessAssignmentWithWhiteList() {

        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_2)))
                .andExpect(status().isForbidden())
    }

    @Test
    @DirtiesContext
    fun markAsFinal() {

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
    @DirtiesContext
    fun cleanupSubmissions() {

        testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
        testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors", "testJavaProj", STUDENT_1)

        // mark second submission as final
        this.mvc.perform(post("/markAsFinal/2")
                .with(user(TEACHER_1)))
                .andExpect(redirectedUrl("/buildReport/2"))
                .andExpect(status().isFound())

        var mavenizedProjectsFolder = File(mavenizedProjectsRootLocation,
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

    // assignment's src/main should not overwrite student submission
    @Test
    @DirtiesContext
    fun assignmentFilesDontOverwriteSubmissionFiles() {

        try {
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "sampleJavaAssignment", "Sample Java Assignment",
                    "org.dropProject.samples.sampleJavaAssignment",
                    "UPLOAD", sampleJavaAssignmentRepo,
                    activateRightAfterCloning = true)

            val submissionId = testsHelper.uploadProject(this.mvc, "projectSampleJavaAssignmentNOK", "sampleJavaAssignment", STUDENT_1)

            val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                    .andExpect(status().isOk())
                    .andReturn()

            @Suppress("UNCHECKED_CAST")
            val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
            assertEquals("Summary should be 4 lines", 4, summary.size)
            assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
            assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
            assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
            assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
            assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
            assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
            assertEquals("junit should be NOK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
            assertEquals("junit should be NOK (value)", "NOK", summary[3].reportValue)

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "sampleJavaAssignment").exists()) {
                File(assignmentsRootLocation, "sampleJavaAssignment").deleteRecursively()
            }
        }

    }

    @Test
    @DirtiesContext
    fun uploadProjectToInexistentAssignment() {
        this.mvc.perform(get("/upload/inexistentAssignment")
                .with(user(STUDENT_1)))
                .andExpect(status().isNotFound())
    }

    @Test
    @DirtiesContext
    fun uploadProjectToNonAccessibleAssignmentBecauseItsNotInWhiteList() {

        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(User("someStudent", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT"))))))
                .andExpect(status().isForbidden())
    }

    @Test
    @DirtiesContext
    fun uploadProjectWithErrors_then_updateAssignment_then_rebuildFull() {

        val testFile = File("${assignmentsRootLocation}/testJavaProj/src/test/java/org/dropProject/sampleAssignments/testProj/TestTeacherProject.java")
        val backupTestFile = testFile.copyTo(
                File("${assignmentsRootLocation}/testJavaProj/src/test/java/org/dropProject/sampleAssignments/testProj/TestTeacherProject.java.backup"),
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
                assertEquals("Summary should be 5 lines", 5, summary.size)
                assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
                assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
                assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
                assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
                assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
                assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
                assertEquals("junit should be NOK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
                assertEquals("junit should be NOK (value)", "NOK", summary[3].reportValue)
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
                assertEquals("Summary should be 5 lines", 5, summary.size)
                assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
                assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
                assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
                assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
                assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
                assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
                assertEquals("junit should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
                assertEquals("junit should be OK (value)", "OK", summary[3].reportValue)

                val submission = reportResult.modelAndView!!.modelMap["submission"] as Submission
                assertEquals(SubmissionStatus.VALIDATED_REBUILT, submission.getStatus())
            }


        } finally {
            backupTestFile.copyTo(testFile, overwrite = true)
            backupTestFile.delete()
        }
    }

    @Test
    @DirtiesContext
    fun uploadProjectWithStudentTests() {

        val assignment = Assignment(id = "testJavaProjWithCoverage",    // <<< this is very important for this test
                name = "Test Project (for automatic tests with coverage)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true,
                acceptsStudentTests = true,    // <<< this is very important for this test
                minStudentTests = 1,
                calculateStudentTestsCoverage = true,  // <<< this is very important for this test
                gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = "testJavaProjWithCoverage")
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWith1StudentTest", assignment.id, STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 6 lines", 6, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("student tests should be OK (key)", Indicator.STUDENT_UNIT_TESTS, summary[3].indicator)
        assertEquals("student tests should be OK (value)", "OK", summary[3].reportValue)
        assertEquals("student tests should pass 1 test", 1, summary[3].reportProgress)
        assertEquals("student tests should have total 1 tests", 1, summary[3].reportGoal)
        assertEquals("teacher tests should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[4].indicator)
        assertEquals("teacher tests should be OK (value)", "OK", summary[4].reportValue)
        assertEquals("teacher tests should pass 2 tests", 2, summary[4].reportProgress)
        assertEquals("teacher tests should have total 2 tests", 2, summary[4].reportGoal)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())
        assert(buildResult.checkstyleErrors.isEmpty())
        assert(buildResult.PMDerrors().isEmpty())

        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == false)
        assertTrue(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))

        assert(buildResult.hasJUnitErrors(TestType.STUDENT) == false)
        assertTrue(buildResult.junitSummaryStudent!!.startsWith("Tests run: 1, Failures: 0, Errors: 0"))

        assert(buildResult.jacocoResults.isNotEmpty())
        assertEquals(25, buildResult.jacocoResults[0].lineCoveragePercent)
    }

    @Test
    @DirtiesContext
    fun uploadProjectWithStudentTestsUsingJUnit5() {

        val assignment = Assignment(id = "testJavaProjJUnit5",
            name = "Test Project",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true,
            acceptsStudentTests = true,    // <<< this is very important for this test
            minStudentTests = 1,
            gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testJavaProjJUnit5")
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWith2StudentTestsUsingBeforeClass", assignment.id, STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 6 lines", 6, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("student tests should be OK (key)", Indicator.STUDENT_UNIT_TESTS, summary[3].indicator)
        assertEquals("student tests should be OK (value)", "OK", summary[3].reportValue)
        assertEquals("student tests should pass 2 tests", 2, summary[3].reportProgress)
        assertEquals("student tests should have total 2 tests", 2, summary[3].reportGoal)
        assertEquals("teacher tests should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[4].indicator)
        assertEquals("teacher tests should be OK (value)", "OK", summary[4].reportValue)
        assertEquals("teacher tests should pass 2 tests", 2, summary[4].reportProgress)
        assertEquals("teacher tests should have total 2 tests", 2, summary[4].reportGoal)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())
        assert(buildResult.checkstyleErrors.isEmpty())
        assert(buildResult.PMDerrors().isEmpty())

        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == false)
        assertTrue(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))

        assert(buildResult.hasJUnitErrors(TestType.STUDENT) == false)
        assertTrue(buildResult.junitSummaryStudent!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))
    }

    @Test
    @DirtiesContext
    fun uploadProjectWithStudentTestsForAssignmentThatDoesntRequireStudentTests() {

        val assignment = Assignment(id = "testJavaProjWithCoverage",    // <<< this is very important for this test
                name = "Test Project (for automatic tests with coverage)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true,
                acceptsStudentTests = false,    // <<< this is very important for this test
                gitRepositoryUrl = "git://dummy",
                gitRepositoryFolder = "testJavaProjWithCoverage")
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWith1StudentTest", assignment.id, STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 5 lines", 5, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("teacher tests should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("teacher tests should be OK (value)", "OK", summary[3].reportValue)
        assertEquals("teacher tests should pass 2 tests", 2, summary[3].reportProgress)
        assertEquals("teacher tests should have total 2 tests", 2, summary[3].reportGoal)

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport

        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == false)
        assertTrue(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))
    }

    @Test
    @DirtiesContext
    fun uploadProjectWithoutStudentTestsForAssignmentThatRequiresStudentTests() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.acceptsStudentTests = true  // <<< this is very important for this test
        assignment.minStudentTests = 1
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectOK", "testJavaProj", STUDENT_1)  // <<< this project doesn't have student tests

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 6 lines", 6, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("student tests should be NOK (key)", Indicator.STUDENT_UNIT_TESTS, summary[3].indicator)
        assertEquals("student tests should be NOK (value)", "Not Enough Tests", summary[3].reportValue)
        assertEquals("teacher tests should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[4].indicator)
        assertEquals("teacher tests should be OK (value)", "OK", summary[4].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())
        assert(buildResult.checkstyleErrors.isEmpty())
        assert(buildResult.PMDerrors().isEmpty())

        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == false)
        assertTrue(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))

        assertNull(buildResult.hasJUnitErrors(TestType.STUDENT))
        assertNull(buildResult.junitSummaryStudent)
    }

    @Test
    @DirtiesContext
    fun uploadProjectWithoutEnoughStudentTests() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.acceptsStudentTests = true  // <<< this is very important for this test
        assignment.minStudentTests = 2  // <<< this project requires at least 2 student tests
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWith1StudentTest", "testJavaProj", STUDENT_1)  // <<< this project only has 1 student test

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 6 lines", 6, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("student tests should be NOK (key)", Indicator.STUDENT_UNIT_TESTS, summary[3].indicator)
        assertEquals("student tests should be NOK (value)", "Not Enough Tests", summary[3].reportValue)
        assertEquals("teacher tests should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[4].indicator)
        assertEquals("teacher tests should be OK (value)", "OK", summary[4].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())
        assert(buildResult.checkstyleErrors.isEmpty())
        assert(buildResult.PMDerrors().isEmpty())

        assert(buildResult.hasJUnitErrors(TestType.TEACHER) == false)
        assertTrue(buildResult.junitSummaryTeacher!!.startsWith("Tests run: 2, Failures: 0, Errors: 0"))

        assertTrue(buildResult.hasJUnitErrors(TestType.STUDENT) == false)
        assertNotNull(buildResult.junitSummaryStudent)
    }


    @Test
    @DirtiesContext
    fun uploadProjectWithTestInputFiles() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWithTestInputFiles", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 5 lines", 5, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("junit should be OK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be OK (value)", "OK", summary[3].reportValue)
    }

    @Test
    @DirtiesContext
    fun uploadProjectOutOfMemory() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.maxMemoryMb = 64  // <<< this is very important for this test
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectOutOfMemory", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 5 lines", 5, summary.size)
        assertEquals("projectStructure should be OK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be OK (value)", "OK", summary[0].reportValue)
        assertEquals("compilation should be OK (key)", Indicator.COMPILATION, summary[1].indicator)
        assertEquals("compilation should be OK (value)", "OK", summary[1].reportValue)
        assertEquals("checkstyle should be OK (key)", Indicator.CHECKSTYLE, summary[2].indicator)
        assertEquals("checkstyle should be OK (value)", "OK", summary[2].reportValue)
        assertEquals("junit should be NOK (key)", Indicator.TEACHER_UNIT_TESTS, summary[3].indicator)
        assertEquals("junit should be NOK (value)", "NOK", summary[3].reportValue)

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertTrue("Should exist a failure with OutOfMemoryError",
                buildResult.junitResults.first { it.testClassName == "TestTeacherProject" }
                .junitMethodResults.any { it.failureType == "java.lang.OutOfMemoryError" })

    }

    @Test
    @DirtiesContext
    fun uploadProjectWithLargeOutput() {  // too many println's

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWithLargeOutput", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val error = reportResult.modelAndView!!.modelMap["error"] as String
        assertEquals("The validation process was aborted because it was producing too much output to the console", error)
    }

    @Test
    @DirtiesContext
    fun rebuild() {
        val submissionId = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)

        this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
                .andExpect(status().isOk)

        val submission = submissionRepository.findById(submissionId.toLong()).get()
        assertEquals(SubmissionStatus.VALIDATED, submission.getStatus())

        this.mvc.perform(post("/rebuild/$submissionId")
                .with(user(TEACHER_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/buildReport/$submissionId"))

        val updatedSubmission = submissionRepository.findById(submissionId.toLong()).get()
        assertEquals(SubmissionStatus.VALIDATED, updatedSubmission.getStatus())
    }

    @Test
    @DirtiesContext
    fun uploadAndDeleteOneSubmission() {

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
        assertTrue("report should be empty", report.isEmpty())

        this.mvc.perform(get("/buildReport/$submissionId")
                .with(user(STUDENT_1)))
                .andExpect(status().isForbidden())
    }

    @Test
    @DirtiesContext
    fun uploadMultipleAndDeleteJustOneSubmission() {

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

    @Test
    @DirtiesContext
    fun `upload by one element of the group and get report by the other element`() {

        // student1 makes a submission in name of the group (student1, student2)
        val submissionId = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1,
            listOf(Pair("student1", "Student 1"), Pair("student2", "Student 2")))

        // student1 gets the upload form
        val reportResult = this.mvc.perform(get("/upload/testJavaProj")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-upload-form"))
            .andReturn()

        // student1 should see a "Get Last Report" button
        @Suppress("UNCHECKED_CAST")
        val lastSubmission = reportResult.modelAndView!!.modelMap["uploadSubmission"] as Submission?
        assertNotNull(lastSubmission)
        assertEquals(submissionId.toLong(), lastSubmission!!.id)

        // student2 gets the upload form
        val reportResult2 = this.mvc.perform(get("/upload/testJavaProj")
            .with(user(STUDENT_2)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-upload-form"))
            .andReturn()

        // student1 should see a "Get Last Report" button
        @Suppress("UNCHECKED_CAST")
        val lastSubmission2 = reportResult2.modelAndView!!.modelMap["uploadSubmission"] as Submission?
        assertNotNull(lastSubmission2)
        assertEquals(submissionId.toLong(), lastSubmission2!!.id)

    }

    @Test
    @DirtiesContext
    fun `upload project that violates the group restrictions of the assignment`() {

        val projectGroupRestrictions = ProjectGroupRestrictions(minGroupSize = 2, maxGroupSize = 2, exceptions = "student3")
        projectGroupRestrictionsRepository.save(projectGroupRestrictions)

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.projectGroupRestrictions = projectGroupRestrictions
        assignmentRepository.save(assignment)

        val error = testsHelper.uploadProject(this.mvc, "projectOKIndividual", "testJavaProj", STUDENT_1,
            expectedResultMatcher = status().isInternalServerError())
        assertEquals("This assignment only accepts submissions from groups with 2..2 elements.", error)

        // add this student to exceptions
        projectGroupRestrictions.exceptions = "student1,student3"
        projectGroupRestrictionsRepository.save(projectGroupRestrictions)

        testsHelper.uploadProject(this.mvc, "projectOKIndividual", "testJavaProj", STUDENT_1,
            expectedResultMatcher = status().isOk())
    }

    @Test
    @DirtiesContext
    fun `upload a project with test classes that dont follow the TestXXX convention should show an error`() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWithStudentTestNotValid", "testJavaProj", STUDENT_1)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals("Summary should be 1 line", 1, summary.size)
        assertEquals("projectStructure should be NOK (key)", Indicator.PROJECT_STRUCTURE, summary[0].indicator)
        assertEquals("projectStructure should be NOK (value)", "NOK", summary[0].reportValue)

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertThat(structureErrors, hasItems("As classes de teste devem começar com a palavra Test (exemplo: TestCar)"))
    }

    @Test
    @DirtiesContext
    fun `student home page should show public assignments`() {

        try {// list assigments should return empty
            this.mvc.perform(get("/").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create assignment
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo, visibility = "PUBLIC",
                teacherId = "p1", activateRightAfterCloning = true
            )

            // list assignments should return one assignment
            val mvcResult = this.mvc.perform(get("/").with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", IsCollectionWithSize.hasSize<Assignment>(1)))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView!!.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            Assert.assertEquals("dummyAssignment4", assignment.id)
            Assert.assertEquals("Dummy Assignment", assignment.name)
            Assert.assertEquals(true, assignment.active)
            Assert.assertEquals(AssignmentVisibility.PUBLIC, assignment.visibility)

        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment4").exists()) {
                File(assignmentsRootLocation, "dummyAssignment4").deleteRecursively()
            }
        }
    }

    @Test
    @DirtiesContext
    fun `teacher home page should show his own assignments and public assignments`() {

        val teacher = User("p1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {// list assigments should return empty
            this.mvc.perform(get("/").with(user(teacher)))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create assignment
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo, visibility = "PUBLIC",
                teacherId = "p1", activateRightAfterCloning = true
            )

            // list assignments should return one assignment
            val mvcResult = this.mvc.perform(get("/").with(user(teacher)))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", IsCollectionWithSize.hasSize<Assignment>(1)))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView!!.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            Assert.assertEquals("dummyAssignment4", assignment.id)
            Assert.assertEquals("Dummy Assignment", assignment.name)
            Assert.assertEquals(true, assignment.active)
            Assert.assertEquals(AssignmentVisibility.PUBLIC, assignment.visibility)

        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment4").exists()) {
                File(assignmentsRootLocation, "dummyAssignment4").deleteRecursively()
            }
        }
    }

    @Test
    fun `download public asset`() {
        val result = this.mvc.perform(get("/upload/testJavaProj/public/test.txt")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result.response.contentAsByteArray
        assertArrayEquals("1".toByteArray(), downloadedFileContent)

        // inexistent file
        this.mvc.perform(get("/upload/testJavaProj/public/test2.txt")
            .with(user(STUDENT_1)))
            .andExpect(status().isNotFound)
    }

}



