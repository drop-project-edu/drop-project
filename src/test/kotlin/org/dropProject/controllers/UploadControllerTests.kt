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

import org.junit.jupiter.api.Assertions.*
import org.dropproject.TestsHelper
import org.dropproject.dao.*
import org.dropproject.data.BuildReport
import org.dropproject.data.SubmissionInfo
import org.dropproject.data.TestType
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.dropproject.services.CooloffOverrideService
import org.dropproject.services.ZipService
import org.dropproject.storage.StorageService
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.hasProperty
import org.hamcrest.collection.IsCollectionWithSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.verify
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.dropproject.config.DropProjectProperties
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.nio.file.Files
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class UploadControllerTests {

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    @Autowired
    lateinit var authorRepository: AuthorRepository

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var rebuildStatusRepository: RebuildStatusRepository

    @Autowired
    lateinit var jUnitReportRepository: JUnitReportRepository

    @Autowired
    lateinit var assignmentACLRepository: AssignmentACLRepository

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

    @Autowired
    lateinit var cooloffOverrideService: CooloffOverrideService

    val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_2 = User("student2", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_3 = User("student3", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))
    val TEACHER_2 = User("teacher2", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

    @BeforeEach
    fun setup() {
        var folder = File(dropProjectProperties.mavenizedProjects.rootLocation)
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

    @AfterEach
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
    @Disabled("rever isto - storageService is not a mock, so the verify() call would fail")
    fun shouldNotAcceptNoZipFile() {
        val multipartFile = MockMultipartFile("file", "test.txt", "text/plain", "Spring Framework".toByteArray())
        this.mvc.perform(multipart("/upload").file(multipartFile).with(user(STUDENT_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/upload"))
                .andExpect(flash().attribute("error", "O ficheiro tem que ser um .zip"))

        verify(this.storageService, never()).store(multipartFile, "")
    }

    @Test
    @Disabled("Infelizmente o MockMvc não consegue testar isto")
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
        val submissionFolder = File("${dropProjectProperties.storage.rootLocation}/upload", submissionDB.submissionFolder)

        assertTrue(submissionFolder.exists(), "submission folder doesn't exist")

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
        assertEquals(1, summary.size, "Summary should be 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be NOK (key)")
        assertEquals("NOK", summary[0].reportValue, "projectStructure should be NOK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertThat(structureErrors,
                hasItems("The project does not contain a 'src/org/dropProject/sampleAssignments/testProj' folder",
                        "The project does not contain the Main.java file in the 'src/org/dropProject/sampleAssignments/testProj' folder"))
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
        assertEquals(1, summary.size, "Summary should be 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be NOK (key)")
        assertEquals("NOK", summary[0].reportValue, "projectStructure should be NOK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertThat(structureErrors, hasItems("The project contains a README.md folder but it should be a file"))
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
        assertEquals(2, summary.size, "Summary should be 2 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be NOK (key)")
        assertEquals("NOK", summary[1].reportValue, "compilation should be NOK (value)")

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
                // Accept both +2 and +1 minutes because a minute boundary
                // may be crossed between the upload and this assertion
                .andExpect(model().attribute("coolOffEnd",
                        anyOf(
                            equalTo(now.plusMinutes(2).format(DateTimeFormatter.ofPattern("HH:mm"))),
                            equalTo(now.plusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm")))
                        )))
    }

    @Test
    @DirtiesContext
    fun uploadProjectThenCooloff() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.cooloffPeriod = 10
        assignmentRepository.save(assignment)

        testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors", "testJavaProj", STUDENT_1)
        val now = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk)
                .andExpect(view().name("student-upload-form"))
                // Accept both +10 and +9 minutes because a minute boundary
                // may be crossed between the upload and this assertion
                .andExpect(model().attribute("coolOffEnd",
                        anyOf(
                            equalTo(now.plusMinutes(10).format(formatter)),
                            equalTo(now.plusMinutes(9).format(formatter))
                        )))
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
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be NOK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be NOK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be NOK (key)")
        assertEquals("NOK", summary[2].reportValue, "checkstyle should be NOK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assert(structureErrors.isEmpty())

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assert(buildResult.compilationErrors.isEmpty())

        assertEquals(buildResult.checkstyleErrors.size, 6, "checkstyle should have 6 errors")
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
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "junit should be OK (value)")

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
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "junit should be OK (value)")
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
            fail<Unit>("README doesn't contain 'Test README'")
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
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be NOK (key)")
        assertEquals("NOK", summary[3].reportValue, "junit should be NOK (value)")

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertEquals(2, buildResult.junitSummaryAsObject(TestType.TEACHER)?.numErrors)
        assertTrue(buildResult.junitErrorsTeacher?.contains("SecurityException") == true)
    }


    @Test
    @DirtiesContext
    fun uploadInGroupAndThenInAnotherGroup() {

        val projectRoot = resourceLoader.getResource("file:src/test/sampleProjects/compact/java/projectCompilationErrors").file

        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("numSubmissions", 0L))

        val submissionId1 = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
        assertEquals(1, submissionId1.toLong(), "wrong submissionId")

        val submissionId2 = testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1)
        assertEquals(2, submissionId2.toLong(), "wrong submissionId")

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
            assertEquals(3, submissionId3.toLong(), "wrong submissionId")

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
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit (public) should be NOK (key)")
        assertEquals("NOK", summary[3].reportValue, "junit (public) should be NOK (value)")
        assertEquals(1, summary[3].reportProgress, "junit (public) should pass 1 test")
        assertEquals(2, summary[3].reportGoal, "junit (public) should have total 2 tests")
        assertEquals(Indicator.HIDDEN_UNIT_TESTS, summary[4].indicator, "junit (hidden) should be NOK (key)")
        assertEquals("NOK", summary[4].reportValue, "junit (hidden) should be NOK (value)")
        assertEquals(0, summary[4].reportProgress, "junit (hidden) should pass 0 tests")
        assertEquals(1, summary[4].reportGoal, "junit (hidden) should have total 1 test")

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
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit (public) should be NOK (key)")
        assertEquals("NOK", summary[3].reportValue, "junit (public) should be NOK (value)")
        assertEquals(1, summary[3].reportProgress, "junit (public) should pass 1 test")
        assertEquals(2, summary[3].reportGoal, "junit (public) should have total 2 tests")
        assertEquals(Indicator.HIDDEN_UNIT_TESTS, summary[4].indicator, "junit (hidden) should be NOK (key)")
        assertEquals("NOK", summary[4].reportValue, "junit (hidden) should be NOK (value)")
        assertEquals(0, summary[4].reportProgress, "junit (hidden) should pass 0 tests")
        assertEquals(1, summary[4].reportGoal, "junit (hidden) should have total 1 test")

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

        // this assignment has one test marked with @Disabled
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
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit (public) should be NOK (key)")
        assertEquals("NOK", summary[3].reportValue, "junit (public) should be NOK (value)")
        assertEquals(0, summary[3].reportProgress, "junit (public) should pass 0 tests")
        assertEquals(1, summary[3].reportGoal, "junit (public) should have total 1 test")

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
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "junit should be OK (value)")


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
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "junit should be OK (value)")
    }

    @Test
    @DirtiesContext
    fun uploadGroupWithDuplicateMembers() {

        val projectRoot = resourceLoader.getResource("file:src/test/sampleProjects/compact/java/projectCompilationErrors").file

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
                    .andExpect(content().json("{ \"error\": \"The AUTHORS.txt file is not correct. It contains duplicate authors.\"}"))

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
        assertEquals(4, summary.size, "Summary should be 4 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be NOK (key)")
        assertEquals("NOK", summary[3].reportValue, "junit should be NOK (value)")
        assertEquals(2, summary[3].reportProgress, "junit should pass 2 tests")
        assertEquals(4, summary[3].reportGoal, "junit should have total 4 tests")

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

        assertEquals(2, summary.size, "Summary should be 2 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be NOK (key)")
        assertEquals("NOK", summary[1].reportValue, "compilation should be NOK (value)")

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
    @DirtiesContext
    fun cleanupDoesntRemoveFilesOfGroupsWithoutAFinalSubmission() {

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
    @DirtiesContext
    fun cleanupIsNotAvailableForGitAssignments() {

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
            assertEquals(4, summary.size, "Summary should be 4 lines")
            assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
            assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
            assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
            assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
            assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
            assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
            assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be NOK (key)")
            assertEquals("NOK", summary[3].reportValue, "junit should be NOK (value)")

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "sampleJavaAssignment").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "sampleJavaAssignment").deleteRecursively()
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
    fun `upload group project when one member is not in whitelist`() {

        // create whitelist with only student1
        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))

        // try to upload a group project with student1 and student2
        // projectOK has AUTHORS.txt with both student1 and student2
        val error = testsHelper.uploadProject(this.mvc, "projectOK", "testJavaProj", STUDENT_1,
            expectedResultMatcher = status().isInternalServerError())
        assertEquals("Student student2 is not authorized for this assignment.", error)
    }

    @Test
    @DirtiesContext
    fun uploadProjectWithErrors_then_updateAssignment_then_rebuildFull() {

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
        assertEquals(6, summary.size, "Summary should be 6 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.STUDENT_UNIT_TESTS, summary[3].indicator, "student tests should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "student tests should be OK (value)")
        assertEquals(1, summary[3].reportProgress, "student tests should pass 1 test")
        assertEquals(1, summary[3].reportGoal, "student tests should have total 1 tests")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[4].indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary[4].reportValue, "teacher tests should be OK (value)")
        assertEquals(2, summary[4].reportProgress, "teacher tests should pass 2 tests")
        assertEquals(2, summary[4].reportGoal, "teacher tests should have total 2 tests")

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
        assertEquals(6, summary.size, "Summary should be 6 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.STUDENT_UNIT_TESTS, summary[3].indicator, "student tests should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "student tests should be OK (value)")
        assertEquals(2, summary[3].reportProgress, "student tests should pass 2 tests")
        assertEquals(2, summary[3].reportGoal, "student tests should have total 2 tests")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[4].indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary[4].reportValue, "teacher tests should be OK (value)")
        assertEquals(2, summary[4].reportProgress, "teacher tests should pass 2 tests")
        assertEquals(2, summary[4].reportGoal, "teacher tests should have total 2 tests")

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
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "teacher tests should be OK (value)")
        assertEquals(2, summary[3].reportProgress, "teacher tests should pass 2 tests")
        assertEquals(2, summary[3].reportGoal, "teacher tests should have total 2 tests")

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
        assertEquals(6, summary.size, "Summary should be 6 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.STUDENT_UNIT_TESTS, summary[3].indicator, "student tests should be NOK (key)")
        assertEquals("Not Enough Tests", summary[3].reportValue, "student tests should be NOK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[4].indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary[4].reportValue, "teacher tests should be OK (value)")

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
        assertEquals(6, summary.size, "Summary should be 6 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.STUDENT_UNIT_TESTS, summary[3].indicator, "student tests should be NOK (key)")
        assertEquals("Not Enough Tests", summary[3].reportValue, "student tests should be NOK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[4].indicator, "teacher tests should be OK (key)")
        assertEquals("OK", summary[4].reportValue, "teacher tests should be OK (value)")

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
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be OK (key)")
        assertEquals("OK", summary[3].reportValue, "junit should be OK (value)")
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
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be OK (key)")
        assertEquals("OK", summary[2].reportValue, "checkstyle should be OK (value)")
        assertEquals(Indicator.TEACHER_UNIT_TESTS, summary[3].indicator, "junit should be NOK (key)")
        assertEquals("NOK", summary[3].reportValue, "junit should be NOK (value)")

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertTrue(buildResult.junitResults.first { it.testClassName == "TestTeacherProject" }
                .junitMethodResults.any { it.failureType == "java.lang.OutOfMemoryError" }, "Should exist a failure with OutOfMemoryError")

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
    @DirtiesContext
    fun abortRebuild() {
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
        assertTrue(report.isEmpty(), "report should be empty")

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
    fun `student in exceptions list but not in allowlist can access and submit individually`() {

        // allowlist only contains student2, not student1
        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student2"))

        // student1 is exempt from the group size restriction, but is not in the allowlist
        val projectGroupRestrictions = ProjectGroupRestrictions(minGroupSize = 2, maxGroupSize = 2, exceptions = "student1")
        projectGroupRestrictionsRepository.save(projectGroupRestrictions)

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.projectGroupRestrictions = projectGroupRestrictions
        assignmentRepository.save(assignment)

        // student1 should still be able to access the assignment, even though it's not in the allowlist
        this.mvc.perform(get("/upload/testJavaProj")
                .with(user(STUDENT_1)))
                .andExpect(status().isOk())

        // student1 should be able to submit individually, bypassing the group size restriction
        testsHelper.uploadProject(this.mvc, "projectOKIndividual", "testJavaProj", STUDENT_1,
            expectedResultMatcher = status().isOk())
    }

    @Test
    @DirtiesContext
    fun `upload group project as teacher`() {

        val projectGroupRestrictions = ProjectGroupRestrictions(minGroupSize = 2, maxGroupSize = 2)
        projectGroupRestrictionsRepository.save(projectGroupRestrictions)

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.projectGroupRestrictions = projectGroupRestrictions
        assignmentRepository.save(assignment)

        testsHelper.uploadProject(this.mvc, "projectOKTeacher", "testJavaProj", TEACHER_1,
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
        assertEquals(1, summary.size, "Summary should be 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be NOK (key)")
        assertEquals("NOK", summary[0].reportValue, "projectStructure should be NOK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertThat(structureErrors, hasItems("Test classes must start with the word Test (example: TestCar)"))
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

            Assertions.assertEquals("dummyAssignment4", assignment.id)
            Assertions.assertEquals("Dummy Assignment", assignment.name)
            Assertions.assertEquals(true, assignment.active)
            Assertions.assertEquals(AssignmentVisibility.PUBLIC, assignment.visibility)

        } finally {
            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").deleteRecursively()
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

            Assertions.assertEquals("dummyAssignment4", assignment.id)
            Assertions.assertEquals("Dummy Assignment", assignment.name)
            Assertions.assertEquals(true, assignment.active)
            Assertions.assertEquals(AssignmentVisibility.PUBLIC, assignment.visibility)

        } finally {
            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment4").deleteRecursively()
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

    @Test
    @DirtiesContext
    fun `upload without authentication should return 401 unauthorized`() {
        // Create a mock multipart file by manually creating the zip
        val projectFolder = resourceLoader.getResource("file:src/test/sampleProjects/compact/java/projectOK").file
        val zipFile = zipService.createZipFromFolder("test", projectFolder)
        zipFile.deleteOnExit()
        val mockFile = MockMultipartFile("file", zipFile.name, "application/zip", zipFile.readBytes())

        // Try to upload without authentication - should return 401 Unauthorized
        // This tests that our JavaScript will detect the 401/403/405 status and redirect to login
        // Note: In production, this might return 405 Method Not Allowed due to Spring Security behavior
        this.mvc.perform(multipart("/upload")
            .file(mockFile)
            .param("assignmentId", "testJavaProj"))
            .andExpect(status().isUnauthorized) // 401 Unauthorized
    }

    @Test
    @DirtiesContext
    fun `try to upload with cooloff then disable and upload again`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.cooloffPeriod = 10
        assignmentRepository.save(assignment)

        testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors", "testJavaProj", STUDENT_1)
        val now = LocalTime.now()

        this.mvc.perform(get("/upload/testJavaProj")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-upload-form"))
            // Accept both +10 and +9 minutes because a minute boundary
            // may be crossed between the upload and this assertion
            .andExpect(model().attribute("coolOffEnd",
                anyOf(
                    equalTo(now.plusMinutes(10).format(DateTimeFormatter.ofPattern("HH:mm"))),
                    equalTo(now.plusMinutes(9).format(DateTimeFormatter.ofPattern("HH:mm")))
                )))

        // Teacher disables cooloff for 30 minutes
        this.mvc.perform(post("/assignment/cooloff/${assignment.id}/disable")
            .param("duration", "30")
            .with(user(TEACHER_1)))
            .andExpect(status().isOk)

        this.mvc.perform(get("/upload/testJavaProj")
            .with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andExpect(view().name("student-upload-form"))
            .andExpect(model().attributeDoesNotExist("coolOffEnd"))
    }

    // ===================================
    // Maven Submission Tests
    // ===================================

    @Test
    @DirtiesContext
    fun `upload Maven project with correct structure and pom`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectOK-maven", "testJavaProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(5, summary.size, "Summary should be 5 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertTrue(structureErrors.isEmpty(), "Structure errors should be empty")

        // Verify main resources are copied to the mavenized folder
        val submissionDB = submissionRepository.findById(submissionId.toLong()).get()
        val mavenizedFolder = File(dropProjectProperties.mavenizedProjects.rootLocation,
            Submission.relativeUploadFolder("testJavaProj", submissionDB.submissionDate))
        val mavenizedProjectFolder = File(mavenizedFolder, "${submissionDB.submissionId}-mavenized")
        assertTrue(File(mavenizedProjectFolder, "src/main/resources/application.properties").exists(), "src/main/resources/application.properties should be copied to mavenized folder")
    }

    @Test
    @DirtiesContext
    fun `upload Maven project with invalid structure`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure1-maven", "testJavaProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(1, summary.size, "Summary should be 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be NOK (key)")
        assertEquals("NOK", summary[0].reportValue, "projectStructure should be NOK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertTrue(structureErrors.isNotEmpty(), "Should have structure errors")
        assertThat(structureErrors,
            hasItems("The project does not contain a 'src/main/java/org/dropProject/sampleAssignments/testProj' folder"))
    }

    @Test
    @DirtiesContext
    fun `upload Maven project without pom xml`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        // Upload a compact project (no pom.xml) to a Maven assignment
        val submissionId = testsHelper.uploadProject(this.mvc, "projectOK", "testJavaProj", STUDENT_1,
            submissionStructure = SubmissionStructure.COMPACT, language = Language.JAVA)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(1, summary.size, "Summary should be 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be NOK (key)")
        assertEquals("NOK", summary[0].reportValue, "projectStructure should be NOK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertTrue(structureErrors.any { it.contains("pom.xml", ignoreCase = true) }, "Should have error about missing pom.xml")
    }

    @Test
    @DirtiesContext
    fun `upload Maven project with JUnit errors`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectJUnitErrors-maven", "testJavaProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
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

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertTrue(buildResult.hasJUnitErrors(TestType.TEACHER) == true, "Should have JUnit errors")
    }

    @Test
    @DirtiesContext
    fun `upload Maven project with checkstyle errors`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors-maven", "testJavaProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertTrue(summary.size >= 4, "Summary should have at least 4 lines")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")
        assertEquals(Indicator.COMPILATION, summary[1].indicator, "compilation should be OK (key)")
        assertEquals("OK", summary[1].reportValue, "compilation should be OK (value)")
        assertEquals(Indicator.CHECKSTYLE, summary[2].indicator, "checkstyle should be NOK (key)")
        assertEquals("NOK", summary[2].reportValue, "checkstyle should be NOK (value)")

        val buildResult = reportResult.modelAndView!!.modelMap["buildReport"] as BuildReport
        assertTrue(buildResult.checkstyleErrors.isNotEmpty(), "Should have checkstyle errors")
    }

    @Test
    @DirtiesContext
    fun `upload Kotlin Maven project`() {
        val assignment = Assignment(
            id = "testKotlinProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropproject.samples.samplekotlinassignment",
            language = Language.KOTLIN,
            ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD,
            active = true,
            submissionStructure = SubmissionStructure.MAVEN,  // <<< Maven structure
            gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testKotlinProj"
        )
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectKotlinOK-maven", "testKotlinProj", STUDENT_1,
            submissionStructure = assignment.submissionStructure, language = assignment.language)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertTrue(summary.isNotEmpty(), "Summary should have at least 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be OK (key)")
        assertEquals("OK", summary[0].reportValue, "projectStructure should be OK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertTrue(structureErrors.isEmpty(), "Structure errors should be empty")
    }

    @Test
    @DirtiesContext
    fun `compact project should be rejected by Maven assignment`() {

        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.submissionStructure = SubmissionStructure.MAVEN
        assignmentRepository.save(assignment)

        // Upload a compact project to a Maven assignment
        val submissionId = testsHelper.uploadProject(this.mvc, "projectOK", "testJavaProj", STUDENT_1,
            submissionStructure = SubmissionStructure.COMPACT, language = Language.JAVA)

        val reportResult = this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val summary = reportResult.modelAndView!!.modelMap["summary"] as List<SubmissionReport>
        assertEquals(1, summary.size, "Summary should be 1 line")
        assertEquals(Indicator.PROJECT_STRUCTURE, summary[0].indicator, "projectStructure should be NOK (key)")
        assertEquals("NOK", summary[0].reportValue, "projectStructure should be NOK (value)")

        @Suppress("UNCHECKED_CAST")
        val structureErrors = reportResult.modelAndView!!.modelMap["structureErrors"] as List<String>
        assertTrue(structureErrors.isNotEmpty(), "Should have errors about missing Maven structure")
    }

    @Test
    @DirtiesContext
    fun `coverage is only visible to teacher when coverageVisibleToStudents is false`() {

        val assignment = Assignment(id = "testJavaProjWithCoverage",
            name = "Test Project (for automatic tests with coverage)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true,
            acceptsStudentTests = true,
            minStudentTests = 1,
            calculateStudentTestsCoverage = true,
            coverageVisibleToStudents = false,   // <<< key for this test
            gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testJavaProjWithCoverage")
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWith1StudentTest", assignment.id, STUDENT_1)

        // student should NOT see coverage
        this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Coverage:"))))

        // teacher should see coverage with "(Only visible to teacher)" note
        this.mvc.perform(get("/buildReport/$submissionId").with(user(TEACHER_1)))
            .andExpect(status().isOk())
            .andDo(print())
            .andExpect(content().string(containsString("Coverage: <span>25</span>&percnt;")))
            .andExpect(content().string(containsString("Only visible to teacher")))
    }

    @Test
    @DirtiesContext
    fun `coverage is visible to students when coverageVisibleToStudents is true`() {

        val assignment = Assignment(id = "testJavaProjWithCoverage",
            name = "Test Project (for automatic tests with coverage)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true,
            acceptsStudentTests = true,
            minStudentTests = 1,
            calculateStudentTestsCoverage = true,
            coverageVisibleToStudents = true,    // <<< key for this test
            gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testJavaProjWithCoverage")
        assignmentRepository.save(assignment)

        val submissionId = testsHelper.uploadProject(this.mvc, "projectWith1StudentTest", assignment.id, STUDENT_1)

        // student should see coverage
        this.mvc.perform(get("/buildReport/$submissionId").with(user(STUDENT_1)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Coverage:")))

        // teacher should see coverage without "(Only visible to teacher)" note
        this.mvc.perform(get("/buildReport/$submissionId").with(user(TEACHER_1)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Coverage:")))
            .andExpect(content().string(not(containsString("Only visible to teacher"))))
    }

    @Test
    @DirtiesContext
    fun uploadProjectInvalidStructure_IndicatorsShouldBeVisibleInReport() {

        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1)

        // status should remain VALIDATED
        val submissionFromDB = submissionRepository.findById(submissionId.toLong()).get()
        assertEquals(SubmissionStatus.VALIDATED, submissionFromDB.getStatus())

        // check that the report page shows the PROJECT_STRUCTURE indicator
        val reportResult = this.mvc.perform(get("/report/testJavaProj").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val submissions = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>
        assertEquals(1, submissions.size)

        val lastSubmission = submissions[0].lastSubmission
        assertNotNull(lastSubmission.reportElements, "reportElements should not be null")
        assertTrue(lastSubmission.reportElements!!.isNotEmpty(), "reportElements should not be empty")
        assertEquals(Indicator.PROJECT_STRUCTURE, lastSubmission.reportElements!![0].indicator)
        assertEquals("NOK", lastSubmission.reportElements!![0].reportValue)
    }

    @Test
    @DirtiesContext
    fun `teacher can submit to private assignment with whitelist`() {

        // 1. Vai buscar o assignment 'testJavaProj' (dono é teacher1)
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.visibility = AssignmentVisibility.PRIVATE   // Define como privadopo
        assignmentRepository.save(assignment)

        // 2. Adiciona o student1 à whitelist
        assigneeRepository.save(
            Assignee(
                assignmentId = "testJavaProj",
                authorUserId = "student1"
            )
        )

        // 3. Tenta submeter como teacher1 (que é o dono mas NÃO está na whitelist)
        val submissionId = testsHelper.uploadProject(
            this.mvc,
            "projectOK",
            "testJavaProj",
            TEACHER_1,
            authors = listOf("teacher1" to "Teacher 1")
        )

        // 4. Verifica se obteve um ID de submissão válido (sucesso)
        try {
            submissionId.toLong()
        } catch (e: Exception) {
            fail<Unit>("Deveria ter conseguido submeter, mas deu erro: $submissionId")
        }

    }


    @Test
    @DirtiesContext
    fun `teacher2 cannot submit to private assignment without whitelist or authorized people`() {

        // 1. Tornar o assignment privado
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.visibility = AssignmentVisibility.PRIVATE
        assignmentRepository.save(assignment)

        // 2. Adicionar apenas student1 à whitelist
        assigneeRepository.save(
            Assignee(
                assignmentId = "testJavaProj",
                authorUserId = "student1"
            )
        )
        // 3. Tentar submeter → deve falhar com 403 Forbidden
        // Como o GlobalExceptionHandler retorna uma String simples (não JSON) no 403, 
        // o uploadProject vai lançar uma exceção ao tentar fazer o parse do JSON.
        try {
            testsHelper.uploadProject(
                this.mvc,
                "projectOK",
                "testJavaProj",
                TEACHER_2,
                authors = listOf("teacher2" to "Teacher 2"),
                expectedResultMatcher = status().isForbidden
            )
            fail<Unit>("Deveria ter lançado uma exceção de parsing pois a resposta 403 não é JSON")
        } catch (e: Exception) {

        }
    }

    @Test
    @DirtiesContext
    fun `teacher2 can submit to private assignment when in authorized people but not in whitelist`() {

        // 1. Tornar o assignment privado
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.visibility = AssignmentVisibility.PRIVATE
        assignmentRepository.save(assignment)

        // 2. Adicionar apenas student1 à whitelist
        assigneeRepository.save(
            Assignee(
                assignmentId = "testJavaProj",
                authorUserId = "student1"
            )
        )

        // 3. Adicionar teacher2 à ACL (Lista de professores autorizados)
        assignmentACLRepository.save(
            AssignmentACL(
                assignmentId = "testJavaProj",
                userId = "teacher2"
            )
        )

        // 4. Tentar submeter → deve ter sucesso
        val submissionId = testsHelper.uploadProject(
            this.mvc,
            "projectOK",
            "testJavaProj",
            TEACHER_2,
            authors = listOf("teacher2" to "Teacher 2")
        )

        // 5. Verifica se obteve um ID de submissão válido
        try {
            submissionId.toLong()
        } catch (e: Exception) {
            fail<Unit>("Deveria ter conseguido submeter (está na ACL), mas deu erro: $submissionId")
        }

        val submissionDB = submissionRepository.findById(submissionId.toLong()).get()
        assertEquals("teacher2", submissionDB.submitterUserId)
    }

    fun `upload project with nested folder in zip should return error`() {

        // Create a temp folder simulating the wrong zip structure:
        // outerFolder/
        //   └── my-project/
        //       ├── src/
        //       └── AUTHORS.txt
        val tempDirectory = File(System.getProperty("java.io.tmpdir"))
        val zipCreationTime = System.currentTimeMillis()

        val projectFolder = File(tempDirectory, "my-project-$zipCreationTime")
        projectFolder.mkdir()
        File(projectFolder, "src").mkdir()
        File(projectFolder, "AUTHORS.txt").apply {
            createNewFile()
            writeText("student1;Student 1")
        }

        val outerFolder = File(tempDirectory, "outer-$zipCreationTime")
        outerFolder.mkdir()
        projectFolder.copyRecursively(File(outerFolder, projectFolder.name))

        // Zip the outer folder (wrong structure)
        val zipFile = zipService.createZipFromFolder("bad-submission-$zipCreationTime", outerFolder)
        zipFile.deleteOnExit()

        val multipartFile = MockMultipartFile("file", zipFile.name, "application/zip", zipFile.readBytes())

        this.mvc.perform(
            multipart("/upload")
                .file(multipartFile)
                .param("assignmentId", "testJavaProj")
                .param("async", "false")
                .with(user(STUDENT_1))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(content().string(
                """{"error":"Please make sure that AUTHORS.txt is placed directly in the root of the ZIP, and that your ZIP does not contain an extra top-level folder (e.g., project-name/AUTHORS.txt)."}"""
            ))

        // clean-up
        projectFolder.deleteRecursively()
        outerFolder.deleteRecursively()
        zipFile.delete()
    }

    @Test
    @DirtiesContext
    fun `show assignment info button is only visible to the assignment's teachers`() {

        val infoLink = "/assignment/info/testJavaProj"

        // make it PUBLIC, so that any teacher can reach the upload page
        val assignment = assignmentRepository.findById("testJavaProj").get()
        assignment.visibility = AssignmentVisibility.PUBLIC
        assignmentRepository.save(assignment)

        // the owner sees the link
        val ownerPage = this.mvc.perform(get("/upload/testJavaProj").with(user(TEACHER_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertTrue(ownerPage.contains(infoLink), "the owner should see the info link")

        // a teacher that is neither the owner nor in the ACL doesn't
        val otherTeacherPage = this.mvc.perform(get("/upload/testJavaProj").with(user(TEACHER_2)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertFalse(otherTeacherPage.contains(infoLink), "teacher2 should not be offered a link that gives him an access denied page")

        // students never see it
        val studentPage = this.mvc.perform(get("/upload/testJavaProj").with(user(STUDENT_1)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertFalse(studentPage.contains(infoLink), "students should not see the info link")

        // ... but once teacher2 is added to the ACL, he does
        assignmentACLRepository.save(AssignmentACL(assignmentId = "testJavaProj", userId = "teacher2"))

        val aclTeacherPage = this.mvc.perform(get("/upload/testJavaProj").with(user(TEACHER_2)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertTrue(aclTeacherPage.contains(infoLink), "a teacher in the ACL should see the info link")
    }
}



