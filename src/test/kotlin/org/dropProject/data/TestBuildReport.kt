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
package org.dropProject.data

import org.dropProject.dao.Assignment
import org.dropProject.dao.Language
import org.dropProject.forms.SubmissionMethod
import org.dropProject.services.BuildReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner


@RunWith(SpringRunner::class)
@ActiveProfiles("test")
class TestBuildReport {

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    val buildReportBuilder = BuildReportBuilder()

    private val dummyJavaAssignment = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummy",
            language = Language.JAVA, gitRepositoryFolder = "testJavaProj")
    private val dummyKotlinAssignment = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummy",
            language = Language.KOTLIN, gitRepositoryFolder = "testJavaProj")

    @Test
    fun testFatalError1() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/fatalError1.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/Users/pedroalves/projects/Lusofona/DropProject/DropProject/mavenized-projects-test/projectSampleJavaAssignmentNOK-mavenized",
                dummyJavaAssignment)

        assertTrue(buildReport.mavenExecutionFailed())
    }

    @Test
    fun testFatalError2() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/fatalError2.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/srv/drop-project/mavenized-projects/1540410598559-Archive-mavenized",
                dummyJavaAssignment)

        assertTrue(buildReport.mavenExecutionFailed())

    }

    @Test
    fun testCheckstyleErrors1() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/checkstyleErrors1.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/srv/drop-project/mavenized-projects/1540285383565-miniTest1-mavenized",
                dummyJavaAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(0, buildReport.compilationErrors.size)
        assertEquals(10, buildReport.checkstyleErrors.size)
    }

    @Test
    fun testCompilerError1() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/compilerError1.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/srv/drop-project/mavenized-projects/1540285463411-MiniTeste1-mavenized",
                dummyJavaAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(28, buildReport.compilationErrors.size)
        assertEquals(0, buildReport.checkstyleErrors.size)
    }

    @Test
    fun testCompilerError2() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/compilerError2.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/Users/pedroalves/projects/Lusofona/DropProject/DropProject/mavenized-projects-test/projectUnexpectedCharacter-mavenized",
                dummyJavaAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(2, buildReport.compilationErrors.size)
        assertEquals(0, buildReport.checkstyleErrors.size)
    }

    @Test
    fun testJUnitErrors1() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/junitErrors1.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/srv/drop-project/mavenized-projects/1540285868304-miniTeste-mavenized",
                dummyJavaAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(0, buildReport.compilationErrors.size)
        assertEquals(0, buildReport.checkstyleErrors.size)
    }

    @Test
    fun testOK1() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/ok1.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/srv/drop-project/mavenized-projects/1540282987575-miniteste1-mavenized",
                dummyJavaAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(0, buildReport.compilationErrors.size)
        assertEquals(0, buildReport.checkstyleErrors.size)
    }

    @Test
    fun testKotlinCompilerError1() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/kotlinCompilerError1.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/srv/drop-project/mavenized-projects/1540829973889-test-mavenized",
                dummyKotlinAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(2, buildReport.compilationErrors.size)
        assertEquals(0, buildReport.checkstyleErrors.size)
        assertEquals("Main.kt: (2, 30) Expecting '\"'", buildReport.compilationErrors[0])
        assertEquals("Main.kt: (2, 30) Expecting ')'", buildReport.compilationErrors[1])
    }

    @Test
    fun testKotlinCompilerError2() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/kotlinCompilerError2.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
            "/srv/drop-project/mavenized-projects/fp-2425-projeto-p1/47-24/1732286495167-projeto_parte1-mavenized",
            dummyKotlinAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(1, buildReport.compilationErrors.size)
        assertEquals(0, buildReport.checkstyleErrors.size)
        assertEquals("Main.kt: (122, 2) Expecting '}'", buildReport.compilationErrors[0])
    }

    @Test
    fun testKotlinJunitErrors1() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/kotlinJunitErrors1.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/srv/drop-project/mavenized-projects/1540768632768-fp-miniteste-exemplo-mavenized",
                dummyKotlinAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(0, buildReport.compilationErrors.size)
        assertEquals(0, buildReport.checkstyleErrors.size)
    }

    @Test
    fun testKotlinOK1() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/kotlinOK1.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/srv/drop-project/mavenized-projects/1540857289260-mini_teste_pratico-mavenized",
                dummyKotlinAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(0, buildReport.compilationErrors.size)
        assertEquals(0, buildReport.checkstyleErrors.size)
    }

    @Test
    fun testPluginError() {
        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/pluginError.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "/srv/drop-project/mavenized-projects/1592587286410-Pedra, Papel, Tesoura-mavenized-for-rebuild",
                dummyKotlinAssignment)

        assertTrue(buildReport.mavenExecutionFailed())

        // TODO: devia ter uma flag "fatalError" quando um plugin rebenta
    }

    @Test
    fun testCheckstylePluginError() {
        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/checkstylePluginFails.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
            "someMavenizedProj",
            dummyJavaAssignment)

        assertTrue(buildReport.mavenExecutionFailed())

        // TODO: devia ter uma flag "fatalError" quando um plugin rebenta
    }

    @Test
    fun testKotlinDetektErrors() {

        val detektOutputs = arrayOf("kotlinDetektErrors-1.0.0.RC9.2.txt", "kotlinDetektErrors-1.1.1.txt", "kotlinDetektErrors-1.11.0.txt")

        detektOutputs.forEach {
            val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/$it").file.readLines()

            val buildReport = buildReportBuilder.build(mavenOutputLines,
                    "someMavenizedProj",
                    dummyKotlinAssignment)

            assertTrue(!buildReport.mavenExecutionFailed())
            assertEquals(0, buildReport.compilationErrors.size)
            assertEquals("$it", 5, buildReport.checkstyleErrors.size)
        }

    }

    @Test
    fun testKotlinDetektErrors2() {
        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/kotlinDetektErrors2-1.11.0.txt").file.readLines()
        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "someMavenizedProj",
                dummyKotlinAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(0, buildReport.compilationErrors.size)
        assertEquals(8, buildReport.checkstyleErrors.size)
    }

    @Test
    fun testExit() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/exit.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
                "someMavenizedProj",
                dummyJavaAssignment)

        assertTrue(!buildReport.mavenExecutionFailed())
        assertEquals(1, buildReport.compilationErrors.size)
        assertEquals("Invalid call to System.exit(). Please remove this instruction", buildReport.compilationErrors[0])
    }

    @Test
    fun testTestsDidntRun() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/testsDidntRun.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
            "someMavenizedProj",
            dummyJavaAssignment)

        assertTrue(buildReport.mavenExecutionFailed())
    }

    @Test
    fun testOutOfMemoryError1() {

        val mavenOutputLines = resourceLoader.getResource("file:src/test/sampleMavenOutputs/outOfMemoryError.txt").file.readLines()

        val buildReport = buildReportBuilder.build(mavenOutputLines,
            "someMavenizedProj",
            dummyJavaAssignment)

        assertTrue(buildReport.mavenExecutionFailed())
    }
}
