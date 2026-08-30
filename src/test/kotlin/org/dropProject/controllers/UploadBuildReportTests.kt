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

import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.dropproject.dao.*
import org.dropproject.data.BuildReport
import org.dropproject.data.TestType
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class UploadBuildReportTests : UploadTestBase() {

    @Test
    fun `upload project that doesn't compile`() {

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
    fun `upload project with checkstyle errors`() {

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
    fun `upload project ok`() {

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
    fun `upload project using java 17`() {

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
    fun `upload project with README`() {

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
    fun `upload project with junit errors`() {
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
    fun `upload project with junit5 errors`() {
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
    fun `upload project with skipped junit tests`() {

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
    fun `upload project with junit errors - hidden tests visibility`() {

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
    fun `upload project with another encoding`() {

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
    fun `upload project with BOM`() {

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
    fun `upload project with junit errors in two test files`() {

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
    fun `upload project that runs out of memory`() {

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
    fun `upload project with large output`() {  // too many println's

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
}
