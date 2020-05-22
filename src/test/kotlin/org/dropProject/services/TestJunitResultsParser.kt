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
package org.dropProject.services

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner


@RunWith(SpringRunner::class)
@ActiveProfiles("test")
class TestJunitResultsParser {

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    val junitResultsParser = org.dropProject.services.JunitResultsParser()
    val jacocoResultsParser = org.dropProject.services.JacocoResultsParser()

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
