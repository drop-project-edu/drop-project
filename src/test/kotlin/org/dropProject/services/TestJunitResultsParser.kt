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
package org.dropproject.services

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner


@RunWith(SpringRunner::class)
@ActiveProfiles("test")
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
class TestJunitResultsParser {

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    val junitResultsParser = org.dropproject.services.JunitResultsParser()
    val jacocoResultsParser = org.dropproject.services.JacocoResultsParser()

    val junitXmlReportsRoot = "src/test/sampleJunitXmlReports"
    val jacocoReportsRoot = "src/test/sampleJacocoReports"

    @Test
    fun testParseReportWithNoErrors() {

        val xmlFile = resourceLoader.getResource("file:${junitXmlReportsRoot}/testNoErrors.xml").file.readText()

        val junitResult = junitResultsParser.parseXml(xmlFile)

        assertEquals("TestProject", junitResult.testClassName)
        assertEquals("org.testProj.TestProject", junitResult.fullTestClassName)
        assertEquals(2, junitResult.numTests)
        assertEquals(0, junitResult.numErrors)
        assertEquals(0, junitResult.numFailures)

    }

    @Test
    fun testParseReportWithErrors1() {

        val xmlFile = resourceLoader.getResource("file:${junitXmlReportsRoot}/testErrors1.xml").file.readText()

        val junitResult = junitResultsParser.parseXml(xmlFile)

        assertEquals("TestTeacherSimulador", junitResult.testClassName)
        assertEquals("pt.ulusofona.lp2.crazyChess.TestTeacherSimulador", junitResult.fullTestClassName)
        assertEquals(9, junitResult.numTests)
        assertEquals(3, junitResult.numErrors)
        assertEquals(0, junitResult.numFailures)

    }

    @Test
    fun testParseReportWithErrors2() {

        val xmlFile = resourceLoader.getResource("file:${junitXmlReportsRoot}/testErrors2.xml").file.readText()

        val junitResult = junitResultsParser.parseXml(xmlFile)

        assertEquals("TestTeacherPart1WithLargeFiles", junitResult.testClassName)
        assertEquals(1, junitResult.junitMethodResults.size)
        val jUnitMethodResult = junitResult.junitMethodResults[0]
        jUnitMethodResult.filterStacktrace("pt.ulusofona.deisi.aed.deisiflix")
        assertEquals("""
ERROR: pt.ulusofona.deisi.aed.deisiflix.TestTeacherPart1WithLargeFiles.test01ParseFiles
java.lang.NullPointerException: Cannot invoke "java.util.ArrayList.add(Object)" because "filmeNaLista.atoresFem" is null
            at pt.ulusofona.deisi.aed.deisiflix.Main.lerFicheiros(Main.java:98)
            at pt.ulusofona.deisi.aed.deisiflix.TestTeacherPart1WithLargeFiles.test01ParseFiles(TestTeacherPart1WithLargeFiles.java:38)


        """.trimIndent(),
            jUnitMethodResult.toString())

    }

    // https://github.com/drop-project-edu/drop-project/issues/102
    @Test
    fun testParseReportWhereTheOutputsDifferInABlankLine() {

        val xmlFile = resourceLoader
            .getResource("file:${junitXmlReportsRoot}/testErrorsBlankLineInComparison.xml").file.readText()

        val junitResult = junitResultsParser.parseXml(xmlFile)

        assertEquals(1, junitResult.junitMethodResults.size)
        val jUnitMethodResult = junitResult.junitMethodResults[0]
        jUnitMethodResult.filterStacktrace("")

        // the only difference between the two outputs is the blank line after the welcome message, so it must
        // survive into the report that is shown to the student
        assertEquals("""
FAILURE: TestTeacherFunctions.TestTeacherFunctions.test_01_criaMenu
org.opentest4j.AssertionFailedError:${' '}
Menu deve ser igual ==> expected: <
Bem vindo ao Campo DEISIado

1 - Novo Jogo
2 - Ler Jogo
0 - Sair
> but was: <
Bem vindo ao Campo DEISIado
1 - Novo Jogo
2 - Ler Jogo
0 - Sair
>
${'\t'}at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
${'\t'}at TestTeacherFunctions.test_01_criaMenu(TestTeacherFunctions.kt:23)


        """.trimIndent(), jUnitMethodResult.toString())
    }

    @Test
    fun testParseReportWithSkippedErrors() {

        val xmlFile = resourceLoader.getResource("file:${junitXmlReportsRoot}/testSkipped.xml").file.readText()

        val junitResult = junitResultsParser.parseXml(xmlFile)

        assertEquals("TestTeacherProject", junitResult.testClassName)
        assertEquals("org.dropProject.sampleAssignments.testProj.TestTeacherProject", junitResult.fullTestClassName)
        assertEquals(1, junitResult.numTests)
        assertEquals(0, junitResult.numErrors)
        assertEquals(1, junitResult.numFailures)
        assertEquals(1, junitResult.numSkipped)
    }

    @Test
    fun testParseCoverageReport() {

        val csvFile = resourceLoader.getResource("file:${jacocoReportsRoot}/jacoco-testProj.csv").file.readText()

        val coverageResult = jacocoResultsParser.parseCsv(csvFile)

        assertEquals(11, coverageResult.linesMissed)
        assertEquals(207, coverageResult.linesCovered)
        assertEquals(95, coverageResult.lineCoveragePercent)

    }
}
